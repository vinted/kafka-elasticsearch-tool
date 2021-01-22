(ns replay.core
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [core.async :as async]
            [core.http :as http-client]
            [core.json :as json]
            [sink.elasticsearch.index :as es-sink]
            [source.elasticsearch :as es]
            [replay.transform.uri :as transform-uri]
            [replay.transform.query :as transform-query])
  (:import (java.time Instant)
           (java.util UUID)))

(defn hits-count [resp-body]
  (let [hits-total (get-in resp-body [:hits :total])]
    (if (number? hits-total)
      {:value    hits-total
       :relation "eq"}
      hits-total)))

(defn post-process [input-doc endpoint query start resp replay-conf]
  (let [replay-data-kw (keyword (or (get replay-conf :replay_data_attr) :replay))
        decoded-body (json/decode (get resp :body))]
    (-> input-doc
        (assoc-in [:_id] (str (UUID/randomUUID)))
        (assoc-in [:_source replay-data-kw :config] (json/encode replay-conf))
        (assoc-in [:_source replay-data-kw :modified-query] query)
        (assoc-in [:_source replay-data-kw :id] (:id replay-conf))
        (assoc-in [:_source replay-data-kw :query-id] (:_id input-doc))
        (assoc-in [:_source replay-data-kw :timestamp] (str (Instant/now)))
        (assoc-in [:_source replay-data-kw :endpoint] endpoint)
        (assoc-in [:_source replay-data-kw :response] (get resp :body))
        (assoc-in [:_source replay-data-kw :service-time] (- (System/currentTimeMillis) start))
        (assoc-in [:_source replay-data-kw :hits] (hits-count decoded-body))
        (assoc-in [:_source replay-data-kw :es-time] (:took decoded-body)))))

(defn query-es-afn [conf]
  (let [replay-conf (:replay conf)
        es-host (:connection.url replay-conf)
        transform-fn (transform-query/transform-fn (:query-transforms replay-conf))
        original-query-key (keyword (:query_attr replay-conf))]
    (fn [{source :_source :as input-doc} channel]
      (let [endpoint (transform-uri/construct-endpoint source replay-conf)
            original-query (get source original-query-key)
            query (transform-fn original-query)
            start (System/currentTimeMillis)]
        (http/request
          {:method  :get
           :headers {"Content-Type" "application/json"}
           :client  @http-client/client
           :url     (format "%s%s" es-host endpoint)
           :body    query}
          (fn [resp]
            (when (string? (get resp :body))
              (a/>!! channel (post-process input-doc endpoint
                                           (when-not (= original-query query) query)
                                           start resp replay-conf)))
            (a/close! channel)))))))

(def defaults
  {:max_docs 10
   :source   {:remote {:host "http://localhost:9200"}
              :index  "query_logs_index"}
   :replay   {:id               "id-of-the-replay"
              :description      "Description of the query replay."
              :query_attr       "request"
              :uri_attr         "uri"
              :replay_data_attr "replay"
              :uri-transforms   []
              :query-transforms []
              :connection.url   "http://localhost:9200"
              :concurrency      1
              :repeats          1}
   :sink     {:connection.url "http://localhost:9200"
              :dest.index     "replay_sink_index"
              :batch.size     50}})

(defn prepare-replay-conf [conf]
  (let [initial-conf (:replay conf)
        with-defaults (merge (:replay defaults) initial-conf)]
    (cond-> with-defaults
            (nil? (:id initial-conf)) (assoc :id (str (UUID/randomUUID))))))

(defn replay
  "Take some queries from an Elasticsearch cluster (transform then) replay the the queries
  to another Elasticsearch cluster, and store the responses in yet another Elasticsearch
  cluster."
  [conf]
  (log/infof "Starting a replay with conf: '%s'" conf)
  (let [replay-conf (prepare-replay-conf conf)
        concurrency (:concurrency replay-conf)
        repeats (:repeats replay-conf)
        queries (mapcat (fn [query] (repeat repeats query)) (es/fetch conf))
        responses (async/map-pipeline-async (query-es-afn (assoc conf :replay replay-conf))
                                            concurrency queries)]
    (es-sink/store! responses (:sink conf))))

(comment
  (replay {:max_docs 10
           :source   {:remote {:host "http://localhost:9200"}
                      :index  "query_logs"
                      :query  {:query {:bool
                                       {:filter
                                        [{:range {:header.timestamp {:gte "2020-07-30T00:58:12+02:00"
                                                                     :lte "2020-07-30T23:00:00+02:00"}}}
                                         {:range {:response_took {:gte 200}}}
                                         {:match {:request "function_score"}}
                                         {:prefix {:uri.keyword "/index_name/_search"}}]
                                        :must_not
                                        [{:match {:body "keyword"}}]}}
                               :sort  [{:header.timestamp {:order :asc}}]
                               :size  1000}}
           :replay   {:id               "int-ids-count"
                      :description      "replay for integer IDs"
                      :query_attr       "request"
                      :uri_attr         "uri"
                      :replay_data_attr "replay"
                      :uri-transforms   []
                      :query-transforms []
                      :connection.url   "http://localhost:9200"
                      :concurrency      10
                      :repeats          1}
           :sink     {:connection.url "http://localhost:9200"
                      :dest.index     "dest-index"
                      :batch.size     50}}))
