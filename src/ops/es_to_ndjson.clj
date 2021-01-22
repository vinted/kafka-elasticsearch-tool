(ns ops.es-to-ndjson
  (:require [core.json :as json]
            [sink :as sink]
            [source :as source]))

(def default-es-to-ndjson-config
  {:max_docs nil
   :source   {:implementation :elasticsearch
              :remote         {:host "http://localhost:9200"}
              :index          "*"
              :query          {:sort ["_doc"]
                               :size 2000}}
   :sink     {:implementation :file}})

(defn es->ndjson [opts]
  (sink/store!
    (mapcat (fn [hit]
              [{:value (json/encode {:index (select-keys hit [:_index :_id :_type])})}
               (assoc hit :value (json/encode (:_source hit)))])
            (source/fetch! (assoc opts :source (merge (:source default-es-to-ndjson-config)
                                                      (:source opts)))))
    (assoc opts :sink
                (merge (:sink default-es-to-ndjson-config)
                       (:sink opts)))))

(comment
  (es->ndjson
    {:max_docs 1
     :source   {:implementation :elasticsearch
                :remote         {:host "http://localhost:9200"}
                :index          ".kibana"
                :query          {:sort ["_doc"]
                                 :size 2000}}
     :sink     {:implementation :file}}))
