(ns replay.deep
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [core.async :as async]
            [core.json :as json]
            [source.elasticsearch :as es]
            [sink :as sink]
            [replay.transform.query :as transform-query]
            [replay.transform.selector :as selector]
            [replay.transform.uri :as transform-uri])
  (:import (java.time Instant)))

(def DEFAULT_DEPTH 1)

(defn additional-data [query-log-attrs query-log-entry-source]
  (loop [[attr & attrs] query-log-attrs
         acc {}]
    (if attr
      (recur attrs
             (assoc acc (:key attr)
                        (get-in query-log-entry-source
                                (selector/path->selector
                                  (:selector attr)))))
      acc)))

(defn query-es-afn [conf]
  (let [replay-conf (:replay conf)
        depth (or (:depth replay-conf) DEFAULT_DEPTH)
        query-log-host (-> conf :source :remote :host)
        dest-es-host (:connection.url replay-conf)
        doc-fetch-strategy (keyword (or (:doc-fetch-strategy replay-conf)
                                        :search-after-with-pit))
        query-selector (selector/path->selector (:query_attr replay-conf))
        query-log-attrs (partial additional-data (:query-log-attrs replay-conf))]
    (fn [query-log-entry channel]
      (let [query-log-entry-source (get query-log-entry :_source)
            raw-endpoint (transform-uri/construct-endpoint query-log-entry-source replay-conf)
            ^String index-name (or (:target-index replay-conf)
                                   (transform-uri/get-index-or-alias raw-endpoint))
            transform-fn (transform-query/transform-fn (:query-transforms replay-conf))
            ^String raw-query (get-in query-log-entry-source query-selector)
            ^String transformed-query (transform-fn raw-query)
            prepared-query (-> transformed-query json/decode)
            hits (es/fetch {:max_docs depth
                            :source   {:remote   {:host dest-es-host}
                                       :index    index-name
                                       :query    prepared-query
                                       :strategy doc-fetch-strategy}})]
        (sink/store! (map (fn [resp rank]
                            {:key     (format "%s:%s:%s" (:id replay-conf) (:_id query-log-entry) rank)
                             :value   (merge {:replay_id        (:id replay-conf)
                                              :query_log_host   query-log-host
                                              :query_log_id     (:_id query-log-entry)
                                              :replay-timestamp (str (Instant/now))
                                              :replay_host      dest-es-host
                                              :rank             rank
                                              :hit              resp}
                                             (query-log-attrs query-log-entry-source))
                             :headers {}})
                          hits (range))
                     conf)
        (a/>!! channel (:_id query-log-entry))
        (a/close! channel)))))

(defn replay
  "From an Elasticsearch cluster takes some queries, replays them
  to (another) Elasticsearch cluster with top-k results where k might be very big, like 1M.
  Each hit with the metadata is written to a specified Kafka topic.
  URIs can be transformed, queries can be transformed."
  [conf]
  (let [replay-conf (:replay conf)
        concurrency (or (:concurrency replay-conf) 50)
        queries (es/fetch conf)
        replays (async/map-pipeline-async (query-es-afn conf) concurrency queries)]
    (doseq [replay replays]
      (log/debugf "Replayed query: %s" replay))))

(comment
  (replay.deep/replay
    {:max_docs 10
     :source   {:remote {:host "http://localhost:9200"}
                :index  "query_logs"
                :query  {:query           {:bool
                                           {:filter
                                            [{:term {:query_from {:value 0}}}
                                             {:term {:stats {:value "keyword_value"}}}
                                             {:range {:header.timestamp {:gte "now-1m"}}}
                                             {:match {:request "multi_match"}}
                                             {:prefix {:uri.keyword "/index_name/_search"}}]
                                            :must_not
                                            [{:exists {:field "query_sort"}}]}}
                         :sort            [{:header.timestamp {:order :asc}}]
                         :docvalue_fields ["uri.index"]
                         :size            10}}
     :replay   {:id                 "my-replay-id"
                :description        "my description"
                :query_attr         "request"
                :uri_attr           "uri"
                :replay_data_attr   "replay"
                :uri-transforms     [{:match       "foo"
                                      :replacement "bar"}]
                :query-transforms   []
                :connection.url     "http://localhost:9200"
                :target-index       "target-index-name"
                :doc-fetch-strategy :search-after-with-pit
                :concurrency        10
                :repeats            1
                :depth              10000}
     :sink     {:implementation    :kafka
                :topic             "deep-replay-topic"
                :bootstrap.servers "127.0.0.1:9092"
                :linger.ms         1000}}))
