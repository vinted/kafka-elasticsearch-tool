(ns ops.es-to-ndjson-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [core.ilm :as ilm]
            [sink.elasticsearch.index :as index]
            [ops.es-to-ndjson :as es-to-ndjson]
            [org.httpkit.client :as http]
            [core.json :as json]
            [scroll.request :as r]))

(defn test-es-host []
  (or (System/getenv "ES_HOST") "http://localhost:9200"))

(defn wait-for-elasticsearch [f]
  (r/execute-request
    {:method :get
     :url    (format "%s/_cluster/health" (test-es-host))})
  (f))

(use-fixtures :once wait-for-elasticsearch)

(deftest ^:integration es-to-ndjson-operation
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        source-index-name "reindex-source-test-index"
        target-file "target/file.ndjson"
        number-of-docs 10
        records (map (fn [x] {:_id x
                              :_source {:value x}}) (range number-of-docs))]
    (if (.exists (io/file target-file))
      (io/delete-file target-file)
      (io/make-parents target-file))
    (log/infof "Deleted source index='%s' at '%s': %s"
               source-index-name es-host (ilm/delete-index! es-host source-index-name))
    (log/infof "Created source index='%s' at '%s': %s"
               source-index-name es-host (ilm/create-index! es-host source-index-name))
    ; Fill source index with some records
    (index/store! records {:connection.url es-host :dest.index source-index-name})
    (ilm/refresh-index! es-host source-index-name)

    (es-to-ndjson/es->ndjson
      {:max_docs nil
       :source   {:implementation :elasticsearch
                  :remote         {:host es-host}
                  :index          source-index-name}
       :sink     {:implementation :file
                  :filename       target-file}})
    (is (= 20 (count (line-seq (io/reader target-file)))))

    (is (false? (:errors
                  (json/decode
                    (:body
                      @(http/request
                         {:method  :post
                          :url     (str es-host "/_bulk")
                          :headers {"Content-Type" "application/x-ndjson"}
                          :body    (slurp target-file)}))))))))
