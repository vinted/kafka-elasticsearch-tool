(ns replay.impact
  (:require [clojure.tools.logging :as log]
            [core.async :as async]
            [core.json :as json]
            [core.deep-merge :as dp]
            [scroll.request :as r]
            [scroll.pit :as pit]
            [sink.elasticsearch.index :as es-sink]
            [source.elasticsearch :as es]
            [replay.transform.uri :as transform.uri]
            [replay.transform.impact :as impact-transform]))

(set! *warn-on-reflection* true)

; https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html

(defn get-index-or-alias [endpoint]
  (last (re-find #"^/(.*)/_search" endpoint)))

(defn prepare-endpoint [^String endpoint]
  (transform.uri/transform-uri
    endpoint
    [{:match "preference=[^&]*&?"
      :replacement ""}
     {:match "routing=[^&]*&?"
      :replacement ""}
     {:match "^(/.*)(/.*)"
      :replacement "$2"}
     {:match "\\?$"
      :replacement ""}]))

(defn generate-queries [opts query-body]
  (impact-transform/generate-queries query-body (get-in opts [:replay :query-transforms])))

(defn measure-impact [opts query-log-entry]
  (let [es-host (get-in opts [:replay :connection.url])
        raw-endpoint (get-in query-log-entry [:_source :uri])
        endpoint (prepare-endpoint raw-endpoint)
        target-index (get-index-or-alias raw-endpoint)
        pit (assoc (pit/init es-host target-index opts) :keep_alive "30s")
        query-string (get-in query-log-entry [:_source :request])
        query-body (json/decode query-string)
        url (format "%s%s" es-host endpoint)
        k (get-in opts [:replay :top-k])
        baseline-resp (r/execute-request
                        {:url     url
                         :body    (assoc query-body :pit pit :size k)
                         :opts    (assoc r/default-exponential-backoff-params :keywordize? true)
                         :method  :get
                         :headers r/default-headers})]
    (let [metric {:precision {:k k :relevant_rating_threshold 1 :ignore_unlabeled false}}
          ratings (map (fn [hit] (assoc (select-keys hit [:_index :_id]) :rating 1))
                       (get-in baseline-resp [:hits :hits]))
          target-url (format "%s/%s/_rank_eval" es-host target-index)
          query-variations (generate-queries opts query-body)
          grouped-variations (group-by (fn [qv] (json/encode (:variation qv)))
                                       (map (fn [qv] (update qv :request assoc :size k)) query-variations))
          rank-eval-resp (r/execute-request
                           {:url     target-url
                            :body    {:requests (map (fn [[id [{request :request}]]]
                                                       {:id      id
                                                        :request (assoc request :pit pit)
                                                        :ratings ratings})
                                                     grouped-variations)
                                      :metric   metric}
                            :opts    (assoc r/default-exponential-backoff-params :keywordize? true)
                            :method  :get
                            :headers r/default-headers})]
      (let [{:keys [details failures metric_score]} rank-eval-resp]
        (map (fn [variation-id]
               (let [query-log-entry-id (get query-log-entry :_id)
                     variation (first (get grouped-variations (name variation-id)))]
                 (-> query-log-entry
                     (update :_id (fn [replay-log-entry-id] (str replay-log-entry-id "-" (hash variation-id))))
                     (assoc-in [:_source :query_log_entry_id] query-log-entry-id)
                     (assoc-in [:_source :impact] {:top-k              k
                                                   :variation-id       (name variation-id)
                                                   :variation          (map (fn [variation-map]
                                                                              (update variation-map :value str))
                                                                            (:variation variation))
                                                   :query              (json/encode (:request variation))
                                                   :failures           failures
                                                   :impact             (float (- 1 (:metric_score (variation-id details))))
                                                   :average-impact     (float (- 1 metric_score))
                                                   :hit-count          (count (get-in details [variation-id :hits]))
                                                   :unrelated-count    (count (get-in details [variation-id :unrated_docs]))
                                                   :metric-score       (get-in details [variation-id :metric_score])
                                                   :original-hit-count (count ratings)
                                                   :details            (json/encode (get details variation-id))}))))
             (keys details))))))

(def defaults
  {:max_docs 1
   :source {:remote {:host "http://localhost:9200"}
            :index  "query_logs_index"}
   :replay {:id               "id-of-the-replay"
            :description      "Description of the query replay."
            :query_attr       "request"
            :uri_attr         "uri"
            :replay_data_attr "replay"
            :uri-transforms   []
            :top-k            10
            :query-transforms []
            :connection.url   "http://localhost:9200"
            :concurrency      1}
   :sink {:connection.url "http://localhost:9200"
          :dest.index     "impact_sink_index"
          :batch.size     50}})

(defn prepare-replay-conf [conf]
  (dp/deep-merge defaults conf))

(defn execute
  "Fetches baseline query and for several boost values transforms query,
  invokes _rank_eval API for metrics on what is the impact of the query transforms to the ranking.
  Impact is defined as 1 minus precision-at-K."
  [conf]
  (log/infof "Starting a replay for impact with conf: '%s'" conf)
  (let [replay-conf (prepare-replay-conf conf)
        concurrency (get-in replay-conf [:replay :concurrency])
        queries (es/fetch conf)
        responses (async/map-pipeline (fn [query-log-entry]
                                        (measure-impact replay-conf query-log-entry))
                                      concurrency queries)]
    (es-sink/store! (apply concat responses) (:sink conf))))

(comment
  (replay.impact/execute
    {:max_docs 100
     :source   {:remote {:host "http://localhost:9200"}
                :index  "query_logs"
                :query  {:query           {:bool
                                           {:filter
                                            [{:term {:query_from {:value 0}}}
                                             {:term {:stats {:value "some value"}}}
                                             {:range {:header.timestamp {:gte "now-2d"}}}
                                             {:match {:request "multi_match"}}
                                             {:prefix {:uri.keyword "/index_name/_search"}}]
                                            :must_not
                                            [{:exists {:field "query_sort"}}]}}
                         :sort            [{:header.timestamp {:order :asc}}]
                         :docvalue_fields ["uri.index"]
                         :size            1}}
     :replay   {:connection.url   "http://localhost:9200"
                :concurrency      10
                :top-k            100
                :query-transforms [{:id     "test"
                                    :lang   :sci
                                    :script "(fn [query boost]\n              (let [query-string (-> query\n                                     (get-in [:query :bool :must])\n                                     first\n                                     (get-in [:constant_score :filter :multi_match :query]))\n                    clause-to-add {:constant_score {:boost boost\n                                                    :filter {:match {:title.folded {:_name \"boost_on_exactness\"\n                                                                                    :operator \"and\"\n                                                                                    :query query-string}}}}}]\n                (update-in query [:query :bool :should] conj clause-to-add)))"
                                    :vals   [0.00001 0.0001 0.001 0.01 0.1 1 10 100 1000 10000]}
                                   {:id     "test2"
                                    :lang   :sci
                                    :script "(fn [query boost] query)"
                                    :vals   [123]}]}
     :sink     {:connection.url "http://localhost:9200"
                :dest.index     "impact_sink_index"
                :batch.size     50}}))
