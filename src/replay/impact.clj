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

(defn prepare-endpoint
  "Prepares the endpoint for PIT queries: remove preference, routing, index name."
  [^String endpoint]
  (transform.uri/transform-uri
    endpoint
    [{:match "preference=[^&]*&?"                           ;; Remove preference string
      :replacement ""}
     {:match "routing=[^&]*&?"                              ;; Remove routing parameter
      :replacement ""}
     {:match "^(/.*)(/.*)"                                  ;; Remove original index because PIT doesn't allow it
      :replacement "$2"}
     {:match "\\?$"                                         ;; Remove trailing question mark
      :replacement ""}]))

(defn generate-queries [opts query-body]
  (impact-transform/generate-queries query-body (get-in opts [:replay :query-transforms])))

(defn get-baseline-resp [^String url query-body pit k]
  (r/execute-request
    {:url     url
     :body    (assoc query-body :pit pit :size k)
     :opts    (assoc r/default-exponential-backoff-params :keywordize? true)
     :method  :get
     :headers r/default-headers}))

(defn get-baseline-ratings [url query-body pit k ignore-timeouts]
  (let [baseline-resp (get-baseline-resp url query-body pit k)]
    (when (and (:timed_out baseline-resp) (not ignore-timeouts))
      (throw (Exception. (format "Request to get baseline ratings timed-out. %s" baseline-resp))))
    (map (fn [hit]
           (assoc (select-keys hit [:_index :_id]) :rating 1))
         (get-in baseline-resp [:hits :hits]))))

(defn get-grouped-query-variations [query-body opts k]
  (->> (generate-queries opts query-body)
       (map (fn [qv] (update qv :request assoc :size k)))
       (group-by (fn [query-variation] (json/encode (:variation query-variation))))))

(defn prepare-rank-eval-request [ratings grouped-variations metric pit]
  {:requests (map (fn [[id [{request :request}]]]
                    {:id      id
                     :request (assoc request :pit pit)
                     :ratings ratings})
                  grouped-variations)
   :metric   metric})

(defn query-rank-eval-api [target-es-host target-index ratings grouped-variations metric pit]
  (let [target-url (format "%s/%s/_rank_eval" target-es-host target-index)]
    (r/execute-request
      {:url     target-url
       :body    (prepare-rank-eval-request ratings grouped-variations metric pit)
       :opts    (assoc r/default-exponential-backoff-params :keywordize? true)
       :method  :get
       :headers r/default-headers})))

(defn construct-rfi-records [rank-eval-resp query-log-entry grouped-variations baseline-ratings k]
  (let [{:keys [details failures metric_score]} rank-eval-resp
        variation-ids (keys details)]
    (map (fn [variation-id]
           (let [query-log-entry-id (get query-log-entry :_id)
                 variation (first (get grouped-variations (name variation-id)))
                 impact {:top-k              k
                         :variation-id       (name variation-id)
                         :variation          (map (fn [variation-map]
                                                    (update variation-map :value str))
                                                  (:variation variation))
                         :query              (json/encode (:request variation))
                         :failures           (json/encode failures)
                         :impact             (float (- 1 (:metric_score (variation-id details))))
                         :average-impact     (float (- 1 metric_score))
                         :hit-count          (count (get-in details [variation-id :hits]))
                         :unrelated-count    (count (get-in details [variation-id :unrated_docs]))
                         :metric-score       (get-in details [variation-id :metric_score])
                         :original-hit-count (count baseline-ratings)
                         :details            (json/encode (get details variation-id))}]
             (-> query-log-entry
                 (update :_id (fn [replay-log-entry-id] (str replay-log-entry-id "-" (hash variation-id))))
                 (assoc-in [:_source :query_log_entry_id] query-log-entry-id)
                 (assoc-in [:_source :impact] impact))))
         variation-ids)))

; https://www.elastic.co/guide/en/elasticsearch/reference/current/search-rank-eval.html
(def defaults-metric-configs
  {:precision                {:k                         10
                              :relevant_rating_threshold 1
                              :ignore_unlabeled          false}
   :recall                   {:k                         10
                              :relevant_rating_threshold 1}
   :mean_reciprocal_rank     {:k                         10
                              :relevant_rating_threshold 1}
   :dcg                      {:k         10
                              :normalize false}
   :expected_reciprocal_rank {:maximum_relevance 10
                              :k                 10}})

(defn get-top-k
  "First check an explicit parameter then if it is provided in the metric.
  If not found throws an exception"
  [opts]
  (or (get-in opts [:replay :top-k])
      (-> opts :replay :metric first last :k)
      10))

(defn get-metric
  "Get the provided metric and merges it onto the default metric config."
  [opts]
  (let [k (get-top-k opts)
        provided-metric (get-in opts [:replay :metric])
        metric-name (ffirst provided-metric)
        provided-metric-config (get provided-metric metric-name)
        default-metric-config (if metric-name
                                (get defaults-metric-configs metric-name)
                                (get defaults-metric-configs :precision))]
    (when (nil? default-metric-config)
      (throw (Exception. (format "Metric '%s' not supported by _rank_eval API. '%s'."
                                 (name metric-name) (get opts :replay)))))
    {(or metric-name :precision)
     (merge default-metric-config
            (select-keys (assoc provided-metric-config :k k)
                         (keys default-metric-config)))}))

(defn measure-impact [opts query-log-entry]
  (let [target-es-host (get-in opts [:replay :connection.url])
        raw-endpoint (get-in query-log-entry [:_source :uri])
        target-index (or (get-in opts [:replay :target-index]) (get-index-or-alias raw-endpoint))
        k (get-top-k opts)
        query-body (json/decode (get-in query-log-entry [:_source :request]))
        metric (get-metric opts)
        pit (assoc (pit/init target-es-host target-index opts) :keep_alive "30s")
        baseline-ratings-url (format "%s%s" target-es-host (prepare-endpoint raw-endpoint))
        baseline-ratings (get-baseline-ratings baseline-ratings-url query-body pit k (get-in opts [:replay :ignore-timeouts]))
        grouped-variations (get-grouped-query-variations query-body opts k)
        rank-eval-resp (query-rank-eval-api target-es-host target-index baseline-ratings grouped-variations metric pit)]
    (log/infof "RFI metric used: '%s'" metric)
    (construct-rfi-records rank-eval-resp query-log-entry grouped-variations baseline-ratings k)))

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
            :target-index     nil
            :concurrency      1
            :metric           nil
            :ignore-timeouts  false}
   :sink {:connection.url "http://localhost:9200"
          :dest.index     "impact_sink_index"
          :batch.size     50}})

(defn prepare-replay-conf [conf]
  (dp/deep-merge defaults conf))

(defn execute
  "Fetches baseline query and for a list of query transforms and values, generates variations of the query,
  then invokes _rank_eval API for metrics on what is the impact of the query transforms to the ranking.
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
    {:max_docs 1
     :source   {:remote {:host "http://localhost:9200"}
                :index  "query_logs"
                :query  {:query           {:bool
                                           {:filter
                                            [{:term {:query_from {:value 0}}}
                                             {:range {:header.timestamp {:gte "now-2d"}}}
                                             {:match {:request "multi_match"}}
                                             {:prefix {:uri.keyword "/index-name/_search"}}]
                                            :must_not
                                            [{:exists {:field "query_sort"}}]}}
                         :sort            [{:header.timestamp {:order :asc}}]
                         :docvalue_fields ["uri.index"]
                         :size            1}}
     :replay   {:connection.url   "http://localhost:9200"
                :concurrency      10
                :top-k            100
                :query-transforms [{:id     "jq-test"
                                    :lang   :jq
                                    :script ". as [$query, $value] | $query | .size = $value"
                                    :vals   [1 10 100]}
                                   {:id     "test2"
                                    :lang   :js
                                    :script "(query, value) => { query['from'] = value; return query; }"
                                    :vals   [123]}]}
     :sink     {:connection.url "http://localhost:9200"
                :dest.index     "impact_sink_index"
                :batch.size     50}}))
