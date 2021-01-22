(ns source.krp
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [core.json :as json])
  (:import (java.util Base64)))

; TODO: support topic pattern
(def defaults
  {:connection.url              "http://localhost:8082"
   :topic                       "keywords-test-topic"
   :group.id                    "krp-group-id"
   :consumer.name               "krp-instance"
   :format                      "binary"
   :timeout                     2000
   :max.bytes                   30000
   :offset                      0
   :partitions                  nil
   :auto.offset.reset           "earliest"
   :consumer.request.timeout.ms 5000
   :auto.commit.enable          "true"
   :delete.consumer.instance    true})

(defn opts->options [opts]
  {:connection-url              (or (:connection.url opts) (:connection.url defaults))
   :group-id                    (or (:group.id opts) (:group.id defaults))
   :consumer-name               (or (:consumer.name opts) (:consumer.name defaults))
   :delete-consumer             (if (false? (:delete.consumer.instance opts))
                                  false
                                  (:delete.consumer.instance defaults))
   :format                      (or (:format opts) (:format defaults))
   :topic                       (or (:topic opts) (:topic defaults))
   :timeout                     (or (:timeout opts) (:timeout defaults))
   :max-bytes                   (or (:max.bytes opts) (:max.bytes defaults))
   :offset                      (or (:offset opts) (:offset defaults))
   :partitions                  (or (:partitions opts) (:partitions defaults))
   :auto-commit-enable          (or (:auto.commit.enable opts) (:auto.commit.enable defaults))
   :auto-offset-reset           (or (:auto.offset.reset opts) (:auto.offset.reset defaults))
   :consumer-request-timeout-ms (or (:consumer.request.timeout.ms opts)
                                    (:consumer.request.timeout.ms defaults))})

(defn create-consumer-instance
  [{:keys [connection-url group-id consumer-name format
           consumer-request-timeout-ms auto-commit-enable auto-offset-reset]}]
  @(http/request
     {:method  :post
      :headers {"Accept"       "application/vnd.kafka.v2+json"
                "Content-Type" "application/vnd.kafka.v2+json"}
      :url     (clojure.core/format "%s/consumers/%s" connection-url group-id)
      :body    (json/encode
                 {:name                        consumer-name
                  :format                      format
                  :auto.offset.reset           auto-offset-reset
                  :auto.commit.enable          auto-commit-enable
                  :consumer.request.timeout.ms consumer-request-timeout-ms})}
     (fn [resp] (-> resp))))

(defn delete-consumer-instance [{:keys [connection-url group-id consumer-name]}]
  @(http/request
     {:method  :delete
      :headers {"Accept" "application/vnd.kafka.v2+json"}
      :url     (format "%s/consumers/%s/instances/%s" connection-url group-id consumer-name)}
     (fn [resp] resp)))

(defn subscribe-to-topic [{:keys [connection-url group-id consumer-name topic]}]
  @(http/request
     {:method  :post
      :url     (format "%s/consumers/%s/instances/%s/subscription"
                       connection-url group-id consumer-name)
      :headers {"Content-Type" "application/vnd.kafka.v2+json"}
      :body    (json/encode {:topics [topic]})}
     (fn [resp] resp)))

(defn get-partition-count [{:keys [connection-url topic]}]
  @(http/request {:method  :get
                  :url     (format "%s/topics/%s" connection-url topic)
                  :headers {"Content-Type" "application/vnd.kafka.v2+json"}}
                 (fn [resp] (-> resp :body json/decode :partitions count))))

(defn set-offset [{:keys [connection-url group-id consumer-name topic offset partitions] :as opts}]
  (let [topic-partition-count (get-partition-count opts)
        partition-list (if (and partitions (< (apply max partitions) topic-partition-count))
                         partitions
                         (range topic-partition-count))]
    @(http/request {:method  :post
                    :url     (format "%s/consumers/%s/instances/%s/positions"
                                     connection-url group-id consumer-name)
                    :headers {"Content-Type" "application/vnd.kafka.v2+json"}
                    :body    (json/encode {:offsets (map (fn [partition]
                                                           {:topic topic
                                                            :partition partition
                                                            :offset offset})
                                                         partition-list)})}
                   (fn [resp] resp))))

(defn base64->string [^String value]
  (when value
    (String. (.decode (Base64/getDecoder) value))))

(defn fetch-messages
  [{:keys [connection-url group-id consumer-name format timeout max-bytes]}]
  @(http/request
     {:method  :get
      :headers {"Accept" (clojure.core/format "application/vnd.kafka.%s.v2+json" format)
                "Content-Type" "application/vnd.kafka.v2+json"}
      :url     (clojure.core/format
                 "%s/consumers/%s/instances/%s/records?timeout=%s&max_bytes=%s"
                 connection-url group-id consumer-name timeout max-bytes)}
     (fn [{:keys [body] :as resp}]
       (if body
         (let [decoded-body (json/decode body)]
           (if (map? decoded-body)
             (do (log/errorf "Error fetching records: %s" resp) [])
             (case format
               "binary" (map (fn [record]
                               (-> record
                                   (update :key base64->string)
                                   (update :value (comp json/decode base64->string))))
                             (json/decode body))
               "json" (json/decode body)
               (json/decode body))))
         (do (log/errorf "Empty body '%s'" resp) [])))))

(defn lazy-records [opts]
  (let [records (fetch-messages opts)]
    (if (empty? records)
      nil
      (lazy-cat records (lazy-records opts)))))

(defn fetch [{opts :source max-docs :max_docs}]
  (let [connection-opts (opts->options opts)
        consumer (create-consumer-instance connection-opts)
        subscription (subscribe-to-topic connection-opts)]
    (try
      (log/infof "Created consumer instance '%s' and subscribed to the topic '%s'"
                 consumer subscription)
      (when (-> opts :offset)
        (log/infof "Initialized lazy consumer for offset reset: %s"
                   (count (fetch-messages (assoc connection-opts :max-bytes 10))))
        (log/infof "Resetting the offset of the consumer group: %s"
                   (set-offset connection-opts)))
      (lazy-cat
        (let [records (lazy-records connection-opts)]
          (if max-docs
            (take max-docs records)
            records))
        (do
          (if (false? (-> opts :delete.consumer.instance))
            (log/infof "FINISHED CONSUMPTION AND NOT DELETING CONSUMER INSTANCE")
            (log/infof "Deleted the consumer instance '%s'"
                       (delete-consumer-instance connection-opts)))
          nil))
      (catch Exception e
        (log/errorf "Failed to fetch data with error: %s" e)
        (log/infof "Deleted the consumer instance '%s'"
                   (delete-consumer-instance connection-opts))))))

(comment
  (source.krp/fetch
    {:max_docs 1
     :source   {:connection.url           "http://localhost:8082"
                :topic                    "topic-name"
                :group.id                 "krp-group_instance"
                :consumer.name            "krp_instance"
                :offset                   0
                :delete.consumer.instance false}})

  (source.krp/fetch
    {:max_docs 1
     :source   {:connection.url "http://localhost:8082"
                :topic          "keywords-test-topic"
                :group.id       "krp-group-id"
                :consumer.name  "krp-instance"
                :offset         1
                :partitions     [0 1]}})

  (source.krp/fetch
    {:max_docs 3
     :source   {:connection.url              "http://localhost:8082"
                :topic                       "topic-name"
                :group.id                    "krp-group-id"
                :consumer.name               "krp-instance"
                :timeout                     100000
                :consumer.request.timeout.ms 10000}}))
