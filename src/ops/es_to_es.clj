(ns ops.es-to-es
  (:require [clojure.tools.logging :as log]
            [core.deep-merge :as deep-merge]
            [sink.elasticsearch.index :as elasticsearch]
            [source.elasticsearch.records :as records]))

(def default-reindex-config
  {:max_docs nil
   :source   {:remote {:connect_timeout "10s"
                       :host            "http://localhost:9200"
                       :socket_timeout  "1m"}
              :index  "source"
              :query  {:sort ["_doc"]
                       :size 5000}}
   :sink     (deep-merge/deep-merge
               elasticsearch/default-opts
               {:batch.size             1000
                :dest.index             "reindex_sink_index"
                :max.in.flight.requests 200
                :read.timeout.ms        60000
                :retry.backoff.ms       1000
                :flush.timeout.ms       100000})})

(defn reindex!
  "Indexes data from one or many Elasticsearch indices
  to some specified index.
  Reindexing can be done between multiple ES clusters."
  [opts]
  (log/infof "Starting reindexing with config: '%s'" opts)
  (elasticsearch/store! (records/fetch opts default-reindex-config)
                        (merge (:sink default-reindex-config)
                               (-> opts
                                   :sink
                                   (update :connection.url (fn [connection-url]
                                                             (if (nil? connection-url)
                                                               (get-in opts [:dest :remote :host])
                                                               connection-url)))
                                   (update :dest.index (fn [dest-index]
                                                         (if (nil? dest-index)
                                                           (get-in opts [:dest :index])
                                                           dest-index))))))
  (log/infof "Finished reindexing: '%s'" opts))

(comment
  (ops.es-to-es/reindex!
    {:max_docs 1000
     :source   {:remote {:host "http://localhost:9200"}
                :index  ".kibana"}
     :dest     {:index  ".kibana-backup"
                :remote {:host "http://localhost:9200"}}}))
