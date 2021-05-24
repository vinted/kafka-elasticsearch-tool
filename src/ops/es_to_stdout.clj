(ns ops.es-to-stdout
  (:require [sink :as sink]
            [source :as source]))

(def default-opts
  {:max_docs nil
   :source   {:implementation :elasticsearch
              :remote         {:host "http://localhost:9200"}
              :index          "*"
              :query          {:sort ["_doc"]
                               :size 2000}}
   :sink     {:implementation :stdout}})

(defn execute
  "Reads documents from Elasticsearch and writes them to stdout"
  [opts]
  (sink/store!
    (source/fetch! (assoc opts :source (merge (:source default-opts)
                                              (:source opts))))
    (assoc opts :sink
                (merge (:sink default-opts)
                       (:sink opts)))))

(comment
  (execute
    {:max_docs 1
     :source   {:implementation :elasticsearch
                :remote         {:host "http://localhost:9200"}
                :index          ".kibana"
                :query          {:sort ["_doc"]
                                 :size 2000}}}))
