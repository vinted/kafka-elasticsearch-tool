(ns ops.es-to-kafka
  (:require [sink :as sink]
            [source :as source])
  (:import (sink KafkaRecord)))

(def default-es-to-kafka-config
  {:max_docs nil
   :source   {:implementation :elasticsearch
              :remote         {:connect_timeout "10s"
                               :host            "http://localhost:9200"
                               :socket_timeout  "1m"}
              :index          "*"
              :query          {:sort ["_doc"]
                               :size 2000}
              :keywordize?    false}
   :dest     {}
   :sink     {:implementation    :kafka
              :topic             "sink-topic"
              :bootstrap.servers "127.0.0.1:9092"}})

(defn es->kafka [opts]
  (sink/store!
    (map (fn [es-record]
           (KafkaRecord.
             (get es-record :_id (get es-record "_id"))
             (get es-record :_source (get es-record "_source"))
             (dissoc es-record :_id "_id" :_source "_source")))
         (source/fetch! (assoc opts :source (merge (:source default-es-to-kafka-config)
                                                   (:source opts)))))
    (assoc opts :sink
                (merge (:sink default-es-to-kafka-config)
                       (:sink opts)))))

(comment
  ; all records from Elasticsearch
  (es->kafka
    {:sink {:topic "es-tool-sink"
            :bootstrap.servers "127.0.0.1:9092"}})
  ; max 10000 and selected records from Elasticsearch
  (es->kafka
    {:max_docs 10000
     :source   {:remote      {:connect_timeout "10s"
                              :host            "http://localhost:9200"
                              :socket_timeout  "1m"}
                :index       ".kibana"
                :query       {:sort ["_doc"]
                              :size 2000}
                :keywordize? false}
     :sink     {:topic             "kibana-data"
                :bootstrap.servers "127.0.0.1:9092"}}))
