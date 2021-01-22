(ns replay.deep
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [core.async :as async]
            [core.json :as json]
            [source.elasticsearch :as es]
            [sink :as sink])
  (:import (java.time Instant)))

(def DEFAULT_DEPTH 1)
(def DEFAULT_PAGE_SIZE 5000)

(defn prepare-query [query replay-conf]
  (-> query
      (assoc :size (min (or (:depth replay-conf) DEFAULT_DEPTH) DEFAULT_PAGE_SIZE))
      (assoc :_source true)
      (assoc :explain true)
      (assoc :sort ["_score" {:created_at "desc"}])))

(defn query-es-afn [conf]
  (let [replay-conf (:replay conf)
        depth (or (:depth replay-conf) DEFAULT_DEPTH)
        query-log-host (-> conf :source :remote :host)
        dest-es-host (:connection.url replay-conf)
        doc-fetch-strategy (or (:doc-fetch-strategy replay-conf) :search-after-with-pit)]
    (fn [query-log-entry channel]
      (let [index-name (or (:target-index replay-conf)
                           (-> query-log-entry :fields :uri.index first))
            query (-> query-log-entry :_source :request json/decode (prepare-query replay-conf))
            hits (es/fetch {:max_docs depth
                            :source   {:remote   {:host dest-es-host}
                                       :index    index-name
                                       :query    query
                                       :strategy doc-fetch-strategy}})]
        (sink/store! (map (fn [resp rank]
                            {:key     (format "%s:%s:%s" (:id replay-conf) (:_id query-log-entry) rank)
                             :value   {:replay_id        (:id replay-conf)
                                       :query_log_host   query-log-host
                                       :query_log_id     (:_id query-log-entry)
                                       :x_user_id        (-> query-log-entry
                                                             :_source
                                                             :request_headers
                                                             :x-user-id
                                                             first)
                                       :query_body       (-> query-log-entry :_source :request)
                                       :query-timestamp  (str (Instant/ofEpochMilli
                                                                (-> query-log-entry
                                                                    :_source
                                                                    :header.timestamp)))
                                       :replay-timestamp (str (Instant/now))
                                       :replay_host      dest-es-host
                                       :rank             rank
                                       :hit              resp}
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
