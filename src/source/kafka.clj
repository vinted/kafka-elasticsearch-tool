(ns source.kafka
  (:require [clojure.tools.logging :as log]
            [core.json :as json]
            [core.properties :as properties])
  (:import (java.time Duration Instant)
           (java.util.regex Pattern)
           (org.apache.kafka.common.header Header)
           (org.apache.kafka.common.serialization StringDeserializer)
           (org.apache.kafka.clients.consumer KafkaConsumer ConsumerConfig
                                              ConsumerRecords ConsumerRecord OffsetAndTimestamp)
           (org.apache.kafka.common PartitionInfo TopicPartition)
           (org.apache.kafka.common.config ConfigDef)
           (java.util Map)))

(def definitions
  [{:name          :implementation
    :default       nil
    :documentation ""}
   {:name          :max_docs
    :default       nil
    :documentation ""}
   {:name          :topic
    :documentation ""}
   {:name          :impatient?
    :documentation ""}
   {:name          :retry-count
    :default       2
    :documentation ""}
   {:name          :keywordize?
    :default       true
    :documentation ""}
   {:name          :decode-value?
    :default       true
    :documentation ""}
   {:name          :timestamp
    :documentation ""}
   {:name          :timestamp-to
    :documentation ""}
   {:name          :offset
    :documentation ""}
   {:name          :offset-to
    :documentation ""}
   {:name          :poll.timeout.ms
    :default       2000
    :documentation ""}
   {:name          :seek.poll.timeout.ms
    :default       0
    :documentation ""}])

(def source-options-definitions
  (reduce
    (fn [acc [k v]] (assoc acc k (first v)))
    {} (group-by :name definitions)))

(defn default-for [name-key]
  (-> source.kafka/source-options-definitions name-key :default))

(defn val-for [source-opts key]
  (first
    (remove nil? (list (get source-opts key)
                       (get source-opts (name key))
                       (default-for key)))))

(def kafka-consumer-config-keys (.names ^ConfigDef (ConsumerConfig/configDef)))
(def default-kafka-consumer-opts (into {} (.defaultValues ^ConfigDef (ConsumerConfig/configDef))))
(def application-defaults
  {ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG (.getName StringDeserializer)
   ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG (.getName StringDeserializer)})

(defn kafka-consumer-config-with-defaults [opts]
  (let [str-opts (into {} (map (fn [[k v]] [(name k) v]) opts))
        config (merge default-kafka-consumer-opts
                      application-defaults
                      (select-keys str-opts kafka-consumer-config-keys))]
    (if (get config ConsumerConfig/GROUP_ID_CONFIG)
      config
      (dissoc config ConsumerConfig/ENABLE_AUTO_COMMIT_CONFIG))))

(defn kafka-consumer [opts]
  (KafkaConsumer. ^Map (kafka-consumer-config-with-defaults opts)))

(defn reset-offsets [^KafkaConsumer consumer opts]
  (let [poll-timeout (val-for opts :seek.poll.timeout.ms)
        _ (.poll consumer (Duration/ofMillis poll-timeout))
        timestamp (val-for opts :timestamp)
        offset-min (val-for opts :offset)]
    (if (and (nil? timestamp) (nil? offset-min))
      (.seekToBeginning consumer (.assignment consumer))
      (let [topic-partitions (.assignment consumer)
            offsets-for-times (if timestamp
                                (let [timestamp-long (.toEpochMilli (Instant/parse timestamp))]
                                  (->> (.assignment consumer)
                                       (reduce (fn [acc ^TopicPartition topic-partition]
                                                 (assoc acc topic-partition timestamp-long)) {})
                                       (.offsetsForTimes consumer)))
                                {})
            end-offsets (.endOffsets consumer (.assignment consumer))]
        (doseq [^TopicPartition topic-partition topic-partitions]
          (let [^OffsetAndTimestamp offset-and-timestamp (get offsets-for-times topic-partition)
                offset-by-timestamp (when offset-and-timestamp
                                      (Long/valueOf (.offset offset-and-timestamp)))
                ^Long end-offset (get end-offsets topic-partition)
                ^Long max-of-timestamp-and-offset
                (when-let [offsets (seq (remove nil? [offset-by-timestamp offset-min]))]
                  (apply max offsets))
                real-offset (if max-of-timestamp-and-offset max-of-timestamp-and-offset end-offset)]
            (log/tracef "offset='%s; timestamp='%s'; offset-by-timestamp='%s' end-offset='%s'; final-offset='%s'"
                        offset-min timestamp offset-by-timestamp end-offset real-offset)
            (.seek consumer topic-partition real-offset)))))))

; TODO: come up with a better option to close consumer.
(def state (atom {:consumer nil}))

(defn assign-consumer [^KafkaConsumer consumer ^Pattern topic-name-pattern]
  (let [topic-partitions (->> (.listTopics consumer)
                              (filter (fn [[^String k]] (re-matches topic-name-pattern k)))
                              (vals)
                              (apply concat)
                              (map (fn [^PartitionInfo partition-info]
                                     (TopicPartition. (.topic partition-info)
                                                      (.partition partition-info)))))]
    (log/infof "Assigning Kafka consumer to '%s' topic(s) partitions: %s"
               topic-name-pattern (count topic-partitions))
    (.assign consumer topic-partitions)))

(defn group-id-provided? [opts]
  (or (get opts :group.id) (get opts "group.id")))

(defn init-consumer [opts]
  (when-let [^KafkaConsumer consumer (:consumer (deref state))]
    (.close consumer))
  (let [^KafkaConsumer consumer (kafka-consumer opts)
        ^String topic (:topic opts)
        topic-name-pattern (re-pattern topic)]
    (swap! state assoc :consumer consumer)
    (if (group-id-provided? opts)
      (.subscribe consumer topic-name-pattern)
      (assign-consumer consumer topic-name-pattern))
    (reset-offsets consumer opts)
    (log/infof "Initialized consumer %s." opts)
    consumer))

(defn lazy-records [^KafkaConsumer consumer poll-timeout-ms]
  (let [^ConsumerRecords recs (.poll consumer ^Duration (Duration/ofMillis poll-timeout-ms))]
    (.count recs)
    (lazy-cat recs (lazy-records consumer poll-timeout-ms))))

(defn impatient-lazy-records [^KafkaConsumer consumer retries poll-timeout-ms]
  (let [^ConsumerRecords recs (.poll consumer ^Duration (Duration/ofMillis poll-timeout-ms))]
    (lazy-cat recs (if (and (zero? (.count recs)) (zero? retries))
                     (do
                       (log/infof "Closing consumer: '%s'" consumer)
                       (.close consumer Duration/ZERO))
                     (impatient-lazy-records consumer
                                             (if (and (zero? (.count recs)) (pos-int? retries))
                                               (dec retries)
                                               3)
                                             poll-timeout-ms)))))

(defrecord Record [topic headers key timestamp offset value partition])

(defn fetch
  "Lazily fetches records from Kafka. A record is a map (record) with keys:
  [:topic :headers :key :timestamp :value].
  Header keys are keywordized. (TODO: param whether to keywordize)
  Params is a map with two expected keys: [:max_docs :source]
  :max_docs - max amount of docs to fetch. If max_docs not provided, sequence is infinite.
  :source - Kafka Consumer opts either with string of keyword keys.
  Also, some additional keys:
    :topic - specifies which topic to consume (used as a regex pattern) (if topic does
      not exist, then IllegalStateException is thrown)
    :group.id - when provided Kafka consumer will join consumer group, otherwise partitions
      are assigned without joining the consumer group (much faster and less problems).
    :timestamp - oldest Kafka records to consume, ISO string, e.g '2007-12-03T10:15:30.00Z'
    :timestamp-to - the latest records to consume, ISO string, e.g '2007-12-03T10:15:30.00Z'
    :offset - kafka offset for all the partitions, default 0.
    :seek.poll.timeout.ms - seek poll timeout in ms, for initialization, default 0 (when :group.id
      is provided you andyou want to reset offsets most likely you want to provide this
      param with value of at least 2000).
    :poll.timeout.ms - poll timeout in ms, default 2000.
    :impatient? - specifies if the Kafka Consumer should terminate after :retry-count
    :retry-count - how many retries impatient lazy consumer should try to fetch records
    :decode-value? - whether to decode value to a map
    :keywordize? - whether keys should be keywords, default true."
  [{source-opts :source :as opts}]
  (properties/opts-valid? :topic source-opts)
  (let [max-docs (-> opts :max_docs)
        ^KafkaConsumer consumer (init-consumer source-opts)
        poll-timeout-ms (val-for source-opts :poll.timeout.ms)
        timestamp-to (when-let [t (val-for source-opts :timestamp-to)]
                       (.toEpochMilli (Instant/parse t)))
        offset-to (val-for source-opts :offset-to)
        records (if (:impatient? source-opts)
                  (impatient-lazy-records consumer (val-for source-opts :retry-count) poll-timeout-ms)
                  (do
                    (.addShutdownHook (Runtime/getRuntime)
                                      (Thread. ^Runnable (fn []
                                                           (log/infof "Starting exit.")
                                                           (.close consumer))))
                    (lazy-records consumer poll-timeout-ms)))
        keywordize? (val-for source-opts :keywordize?)
        decode-value? (val-for source-opts :decode-value?)]
    (->> (if max-docs (take max-docs records) records)
         (filter (fn [^ConsumerRecord record] (if timestamp-to
                                                (< (.timestamp record) timestamp-to)
                                                true)))
         (filter (fn [^ConsumerRecord record] (if offset-to
                                                (< (.offset record) offset-to)
                                                true)))
         (map (fn [^ConsumerRecord record]
                (->Record (.topic record)
                          (reduce (fn [acc ^Header h]
                                    (assoc acc (if keywordize?
                                                 (keyword (.key h))
                                                 (.key h)) (String. (.value h))))
                                  {} (.headers record))
                          (.key record)
                          (.timestamp record)
                          (.offset record)
                          (if decode-value?
                            (json/decode (.value record) keywordize?)
                            (.value record))
                          (.partition record)))))))

(comment
  (fetch
    {:max_docs 1
     :source   {:topic             "test-topic"
                :bootstrap.servers "127.0.0.1:9092"}})

  (fetch
    {:max_docs 2
     :source   {:topic             "stdin-to-kafka-test"
                :bootstrap.servers "127.0.0.1:9092"
                :impatient?        true
                :retry-count       3}})

  (fetch
    {:max_docs 1
     :source   {:topic                "test-topic"
                :bootstrap.servers    "127.0.0.1:9092"
                :impatient?           true
                :retry-count          2
                :offset               0
                :seek.poll.timeout.ms 2000
                :group.id             "group.id"}})

  (fetch
    {:max_docs 10
     :source   {:topic             "test-topic"
                :bootstrap.servers "127.0.0.1:9092"
                :impatient?        true
                :retry-count       3
                :decode-value?     false
                :offset            410
                :offset-to         412
                :timestamp         "2020-04-27T07:53:35.998907Z"
                :timestamp-to      "2020-04-27T07:54:35.998907Z"}}))
