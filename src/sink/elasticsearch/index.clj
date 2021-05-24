(ns sink.elasticsearch.index
  (:require [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [core.ilm :as ilm]
            [core.json :as json])
  (:import (io.confluent.connect.elasticsearch.jest JestElasticsearchClient)
           (io.confluent.connect.elasticsearch BulkIndexingClient IndexableRecord Key)
           (io.confluent.connect.elasticsearch.bulk BulkProcessor BulkProcessor$BehaviorOnMalformedDoc)
           (org.apache.kafka.connect.sink SinkRecord)
           (org.apache.kafka.common.utils SystemTime)
           (java.util Map)))

(def default-opts
  "Documentation of these params can be found at:
  https://docs.confluent.io/current/connect/kafka-connect-elasticsearch/configuration_options.html"
  {:auto.create.indices.at.start                        true
   :batch.size                                          (int 10000)
   :behavior.on.malformed.documents                     "warn"
   :behavior.on.null.values                             "delete"
   :compact.map.entries                                 true
   :connection.compression                              false
   :connection.password                                 nil
   :connection.timeout.ms                               (int 60000)
   :connection.url                                      "http://localhost:9200"
   :connection.username                                 nil
   :drop.invalid.message                                false
   :elastic.https.ssl.cipher.suites                     nil
   :elastic.https.ssl.enabled.protocols                 "TLSv1.2"
   :elastic.https.ssl.endpoint.identification.algorithm "https"
   :elastic.https.ssl.key.password                      nil
   :elastic.https.ssl.keymanager.algorithm              "SunX509"
   :elastic.https.ssl.keystore.location                 nil
   :elastic.https.ssl.keystore.password                 nil
   :elastic.https.ssl.keystore.type                     "JKS"
   :elastic.https.ssl.protocol                          "TLSv1.2"
   :elastic.https.ssl.provider                          nil
   :elastic.https.ssl.secure.random.implementation      nil
   :elastic.https.ssl.trustmanager.algorithm            "PKIX"
   :elastic.https.ssl.truststore.location               nil
   :elastic.https.ssl.truststore.password               nil
   :elastic.https.ssl.truststore.type                   "JKS"
   :elastic.security.protocol                           "PLAINTEXT"
   :flush.timeout.ms                                    (int 60000)
   :key.ignore                                          false
   :linger.ms                                           (int 1000)
   :max.buffered.records                                (int 100000)
   :max.in.flight.requests                              (int 16)
   :max.retries                                         (int 10)
   :read.timeout.ms                                     (int 60000)
   :retry.backoff.ms                                    (int 2000)
   :schema.ignore                                       "true"
   :topic.index.map                                     nil
   :topic.key.ignore                                    nil
   :topic.schema.ignore                                 nil
   :type.name                                           "_doc"
   :write.method                                        "insert"})

(defn get-conf-val [key conf defaults]
  (first
    (remove nil? (list (get conf (name key))
                       (get conf key)
                       (get defaults (name key))
                       (get defaults key)))))

(defn get-int-conf-val [key conf defaults]
  (Integer/parseInt (str (get-conf-val key conf defaults))))

(defn ^Map stringify-conf [conf]
  (reduce (fn [acc [k v]] (assoc acc (name k) (when-not (nil? v) (str v)))) {} conf))

(defn ^BulkProcessor writer [sink-opts]
  (let [client (JestElasticsearchClient. (stringify-conf (merge default-opts sink-opts)))]
    (BulkProcessor.
      (SystemTime.)
      (BulkIndexingClient. client)
      (get-int-conf-val :max.buffered.records sink-opts default-opts)
      (get-int-conf-val :max.in.flight.requests sink-opts default-opts)
      (get-int-conf-val :batch.size sink-opts default-opts)
      (get-int-conf-val :linger.ms sink-opts default-opts)
      (get-int-conf-val :max.retries sink-opts default-opts)
      (get-int-conf-val :retry.backoff.ms sink-opts default-opts)
      (BulkProcessor$BehaviorOnMalformedDoc/forValue
        (get-conf-val :behavior.on.malformed.documents sink-opts default-opts))
      nil)))

(defn record->sink-record [index-name record]
  (SinkRecord.
    ^String index-name
    0
    nil
    (get record :_id)
    nil
    (get record :_source)
    0))

(defn store!
  "Indexes records to ES index.
   For slightly faster map access construct
   Params:
    :connection.url - ES Host, default http://localhost:9200
    :dest-host - same as :connection.url, lower precedence
    :dest.index - index name, no default, required
    :dest-index - same as :dest.index lower precedence
    :already.encoded - doc is already a JSON string, default false"
  [records sink-opts]
  (let [^BulkProcessor bulk-processor (writer sink-opts)
        ^String type-name (get-conf-val :type.name sink-opts default-opts)
        ^Integer flush-timeout (get-conf-val :flush.timeout.ms sink-opts default-opts)
        dest-host (or (get-conf-val :connection.url sink-opts default-opts)
                      (get-conf-val :dest-host sink-opts {}))
        ^String index-name (or (get-conf-val :dest.index sink-opts {})
                               (get-conf-val :dest-index sink-opts {}))
        already-encoded? (get-conf-val :already.encoded sink-opts {})]
    (when-not (ilm/index-exists? dest-host index-name)
      (log/debugf "Created index: %s" (ilm/create-index! dest-host index-name)))
    (log/debugf "Disabled index refresh interval: %s" (ilm/set-refresh-interval! dest-host index-name "-1"))
    (.start bulk-processor)
    (r/fold
      (fn [& [_ record]]
        (when record
          (.add bulk-processor
                (IndexableRecord.
                  (Key. index-name type-name (str (get record :_id)))
                  (if already-encoded?
                    (get record :_source)
                    (when-let [src (get record :_source)]
                      (json/encode-vanilla src)))
                  (System/nanoTime))
                (record->sink-record index-name record)
                flush-timeout)))
      records)
    (.flush bulk-processor flush-timeout)
    (.stop bulk-processor)
    (log/debugf "Enabled index refresh interval: %s" (ilm/set-refresh-interval! dest-host index-name "1s"))))

(defrecord EsRecord [_id _source])

(comment
  (time
    ; Index 1M small maps
    (store!
      (doall (->> (range 1000000)
                  (mapv (fn [idx] (EsRecord. (str idx) {:idx idx})))))
      {:connection.url "http://localhost:9200"
       :dest.index     "index"
       :batch.size     "15000"}))
  (time
    ; already encoded json strings
    (store!
      (doall (->> (range 1000000)
                  (mapv (fn [idx] (EsRecord. (str idx) (str "{\"idx\":" idx "}"))))))
      {:connection.url  "http://localhost:9200"
       :already.encoded true
       :dest.index      "index"})))
