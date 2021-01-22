(ns replay.replay-with-transform-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [core.ilm :as ilm]
            [core.json :as json]
            [sink.elasticsearch.index :as index]
            [scroll.request :as r]
            [replay.core :as replay]))

(defn test-es-host []
  (or (System/getenv "ES_HOST") "http://localhost:9200"))

(defn wait-for-elasticsearch [f]
  (r/execute-request
    {:method :get
     :url    (format "%s/_cluster/health" (test-es-host))})
  (f))

(use-fixtures :once wait-for-elasticsearch)

(defn recreate-index [es-host index-name]
  (when (ilm/index-exists? es-host index-name)
    (log/infof "Deleted source index='%s' at '%s': %s"
               index-name es-host (ilm/delete-index! es-host index-name)))
  (log/infof "Created index: %s" (ilm/create-index! es-host index-name)))

(def simple-replay
  {:id             "test-replay"
   :description    "Test replay"
   :query_attr     "source"
   :uri_attr       "uri"
   :connection.url (test-es-host)
   :concurrency    1
   :repeats        1})

(deftest ^:integration es-to-es-replay
  (let [target-index "target-index"
        query {:query {:match {:title "foo"}}
               :_source false}
        queries [{:_id "query_1"
                  :_source {:source (json/encode query)
                            :uri    (str "/" target-index "/_search")}}]
        docs [{:_id "doc_1"
               :_source {:title "foo"}}]
        queries-index "replay-queries"
        sink-index "sink-index"]
    (recreate-index (test-es-host) queries-index)
    (recreate-index (test-es-host) target-index)
    (recreate-index (test-es-host) sink-index)
    (index/store! queries {:connection.url (test-es-host) :dest.index queries-index})
    (index/store! docs {:connection.url (test-es-host) :dest.index target-index})
    ; check if sink-index is empty
    (is (= 0 (count (scroll/hits {:es-host (test-es-host) :index-name sink-index}))))
    (replay/replay
      {:max_docs 1
       :source   {:remote {:host (test-es-host)}
                  :index  queries-index}
       :replay   simple-replay
       :sink     {:connection.url (test-es-host)
                  :dest.index     sink-index}})
    ; check if sink-index is not empty
    (let [sink-docs (scroll/hits {:es-host (test-es-host) :index-name sink-index})
          replay-hits (-> sink-docs
                          first
                          :_source
                          :replay
                          :response
                          json/decode
                          :hits
                          :hits)]
      (is (= 1 (count sink-docs)))
      (is (= 1 (count replay-hits)))
      (is (nil? (:_source (first replay-hits)))))))

(def replay-with-transform
  (assoc simple-replay
    :query-transforms [{:lang :js
                        :script "(q) => Object.assign(q, {'_source': true})"}]))

(deftest ^:integration es-to-es-replay-with-transform
  (let [target-index "target-index"
        query {:query {:match {:title "foo"}}
               :_source false}
        queries [{:_id "query_1"
                  :_source {:source (json/encode query)
                            :uri    (str "/" target-index "/_search")}}]
        docs [{:_id "doc_1"
               :_source {:title "foo"}}]
        queries-index "replay-queries"
        sink-index "sink-index"]
    (recreate-index (test-es-host) queries-index)
    (recreate-index (test-es-host) target-index)
    (recreate-index (test-es-host) sink-index)
    (index/store! queries {:connection.url (test-es-host) :dest.index queries-index})
    (index/store! docs {:connection.url (test-es-host) :dest.index target-index})
    ; check if sink-index is empty
    (is (= 0 (count (scroll/hits {:es-host (test-es-host) :index-name sink-index}))))
    (replay/replay
      {:max_docs 1
       :source   {:remote {:host (test-es-host)}
                  :index  queries-index}
       :replay   replay-with-transform
       :sink     {:connection.url (test-es-host)
                  :dest.index     sink-index}})
    ; check if sink-index is not empty
    (let [sink-docs (scroll/hits {:es-host (test-es-host) :index-name sink-index})
          replay-hits (-> sink-docs
                          first
                          :_source
                          :replay
                          :response
                          json/decode
                          :hits
                          :hits)]
      (is (= 1 (count sink-docs)))
      (is (= 1 (count replay-hits)))
      (is (= {:title "foo"} (:_source (first replay-hits)))))))
