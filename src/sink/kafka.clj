(ns sink.kafka
  (:require [clojure.tools.logging :as log]
            [core.json :as json]
            [core.properties :as properties])
  (:import (java.time Duration)
           (java.util UUID)
           (org.apache.kafka.clients.producer KafkaProducer ProducerRecord ProducerConfig Callback)
           (org.apache.kafka.common.serialization StringSerializer)
           (org.apache.kafka.common.header.internals RecordHeader)))

(def not-producer-keys [:topic :encode-value? :implementation])

(defn kafka-producer
  "Supported options are http://kafka.apache.org/documentation.html#producerconfigs
  Keys in the opts can be either keywords or strings."
  [opts]
  (let [opts (apply dissoc opts not-producer-keys)]
    (KafkaProducer.
      (doto (properties/opts->properties opts)
        ; Set the required defaults properties for the kafka producer
        (.put ProducerConfig/CLIENT_ID_CONFIG
              (or (get opts :client.id) (get opts "client.id")
                  (str "ESToolsKafkaProducer-" (UUID/randomUUID))))
        (.put ProducerConfig/KEY_SERIALIZER_CLASS_CONFIG (.getName StringSerializer))
        (.put ProducerConfig/VALUE_SERIALIZER_CLASS_CONFIG (.getName StringSerializer))))))

(defn map->headers [r]
  (map (fn [[k v]] (RecordHeader. (name k) (.getBytes (str v))))
       (remove (fn [[_ v]] (empty? v)) r)))

(defn store!
  "Records is a list of maps {:keys String :record Map :headers Map}.
  It should be possible to JSON encode the :record
  opts must contain :sink which is a map with the Kafka Consumer opts either
  with string of keyword keys. Also, some additional keys:
    :topic - name of the topic to which to store the records
    :encode-value? - whether to JSON encode value, default true"
  [records opts]
  (properties/opts-valid? :topic (:sink opts))
  (let [sink-opts (:sink opts)
        ^KafkaProducer producer (kafka-producer sink-opts)
        topic (:topic sink-opts)]
    (doseq [r records]
      (try
        (.send producer
               (ProducerRecord. topic nil nil
                                (:key r)
                                (if (false? (:encode-value? sink-opts))
                                  (:value r)
                                  (json/encode (:value r)))
                                (map->headers (:headers r)))
               (reify Callback
                 (onCompletion [this metadata exception]
                   (when exception
                     (println metadata exception)))))
        (catch Exception e
          (log/errorf "Failed to store record '%s' in kafka because '%s'" r e))))
    (log/infof "Flushing records")
    (.flush producer)
    (log/infof "Flushed")
    (.close producer (Duration/ofSeconds 2))))

(comment
  ; without the key
  (store!
    [{:value {:test "test-1"}}]
    {:sink {:topic             "sink-test"
            :bootstrap.servers "127.0.0.1:9092"
            :linger.ms         0}})
  ; with the key
  (store!
    [{:key "test" :value {:test "test"}}]
    {:sink {:topic             "sink-test"
            :bootstrap.servers "127.0.0.1:9092"}})
  ; with headers
  (store!
    [{:key     "test"
      :value   {:test "test"}
      :headers {:test-header "test-header"}}]
    {:sink {:topic             "sink-test"
            :bootstrap.servers "127.0.0.1:9092"}}))
