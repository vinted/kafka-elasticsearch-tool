(ns ops.kafka-to-es
  (:require [sink.elasticsearch.index :as indexer]
            [source :as source]))

(def default-opts
  {:max_docs 5
   :source   {:implementation    :kafka
              :bootstrap.servers "127.0.0.1:9092"
              :decode-value?     false}
   :dest     {:remote {:connect_timeout "10s"
                       :host            "http://localhost:9200"
                       :socket_timeout  "1m"}}
   :sink     (merge {:implementation :elasticsearch}
                    indexer/default-opts)})

(defn kafka-to-es [opts]
  (let [es-host (get-in opts [:dest :remote :host])
        index-name (-> opts :dest :index)
        kafka-records (source/fetch! (assoc opts :source (merge (:source default-opts)
                                                                (:source opts))))]
    (indexer/store! (map (fn [record]
                           (indexer/->EsRecord
                             (if-let [k (:key record)]
                               k
                               (str (:topic record) "+" (:partition record) "+" (:offset record)))
                             (:value record)))
                         kafka-records)
                    (merge
                      (:sink default-opts)
                      {:connection.url  es-host
                       :dest.index      index-name
                       :already.encoded true}))))

(comment
  (kafka-to-es
    {:max_docs 1
     :source   {:topic             "source-topic"
                :bootstrap.servers "127.0.0.1:9092"}
     :dest     {:index  "dest-index-name"
                :remote {:host "http://localhost:9200"}}
     :sink     {}}))
