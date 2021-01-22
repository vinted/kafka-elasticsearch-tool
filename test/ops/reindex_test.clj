(ns ops.reindex-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [scroll :as scroll]
            [sink.elasticsearch.index :as index]
            [core.ilm :as ilm]
            [ops.es-to-es :as reindex]))

(deftest ^:integration in-cluster-reindex
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        source-index-name "reindex-source-test-index"
        dest-index-name "reindex-dest-test-index"
        number-of-docs (+ 10 (rand-int 50))
        records (map (fn [x] {:_id     x
                              :_source {:value x}}) (range number-of-docs))]
    ; Sanity check that two indices are different
    (is (not= source-index-name dest-index-name))
    (is (< 0 number-of-docs))

    (log/infof "Deleted source index='%s' at '%s': %s"
               source-index-name es-host (ilm/delete-index! es-host source-index-name))
    (log/infof "Deleted dest index='%s' at '%s': %s"
               dest-index-name es-host (ilm/delete-index! es-host dest-index-name))

    (log/infof "Created source index='%s' at '%s': %s"
               source-index-name es-host (ilm/create-index! es-host source-index-name))
    (log/infof "Created dest index='%s' at '%s': %s"
               dest-index-name es-host (ilm/create-index! es-host dest-index-name))

    ; Fill source index with some records
    (index/store! records {:connection.url es-host :dest.index source-index-name})
    (ilm/refresh-index! es-host source-index-name)

    ; PRE check that souce index has all docs
    (is (= (set (map :_source records))
           (set (map :_source (scroll/hits {:es-host es-host :index-name source-index-name})))))
    
    ; Initialize reindex job
    (reindex/reindex!
      {:source   {:remote {:host es-host}
                  :index  source-index-name}
       :dest     {:index  dest-index-name
                  :remote {:host es-host}}})
    (ilm/refresh-index! es-host dest-index-name)

    ; Check if what is reindexed is the same as source data
    (is (= (set (map :_source records))
           (set (map :_source (scroll/hits {:es-host es-host :index-name dest-index-name})))))
    ; Check if source and dest indices have the same content
    (is (= (set (map :_source (scroll/hits {:es-host es-host :index-name source-index-name})))
           (set (map :_source (scroll/hits {:es-host es-host :index-name dest-index-name})))))))
