(ns ops.stdin-to-es
  (:require [sink.elasticsearch.index :as indexer]
            [source :as source]
            [jsonista.core :as json]))

(def default-opts
  {:max_docs 5
   :source   {:implementation :stdin
              :decode-value?  true}
   :sink     (merge {:implementation :elasticsearch}
                    indexer/default-opts)})

(defn execute [opts]
  (let [stdin-records (source/fetch! (assoc opts :source (merge (:source default-opts)
                                                                (:source opts))))]
    (indexer/store! stdin-records
                    (merge
                      (:sink default-opts)
                      (:sink opts)))))

(comment
  (with-in-str
    (json/write-value-as-string
      {:_id "123" :_source {:foo "bar"}})
    (ops.stdin-to-es/execute
      {:max_docs 1
       :source   {:implementation :stdin
                  :decode-value?  true}
       :sink     {:connection.url "http://localhost:9200"
                  :dest.index     "dest-index-name"}})))
