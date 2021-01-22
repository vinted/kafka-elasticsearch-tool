(ns ops.kafka-to-ndjson
  (:require [core.json :as json]
            [sink :as sink]
            [source :as source]))

(def default-kafka-ndjson-config
  {:max_docs nil
   :source   {:implementation    :kafka
              :bootstrap.servers "127.0.0.1:9092"
              :decode-value?     true}
   :sink     {:implementation :file}})

(defn execute [opts]
  (sink/store!
    (mapcat (fn [record]
              [{:value (json/encode {:index {:_id (:key record)
                                             :_index (:topic record)}})}
               (assoc record :value (json/encode (:value record)))])
            (source/fetch! (assoc opts :source (merge (:source default-kafka-ndjson-config)
                                                      (:source opts)))))
    (assoc opts :sink
                (merge (:sink default-kafka-ndjson-config)
                       (:sink opts)))))

(comment
  (execute
    {:max_docs nil
     :source   {:bootstrap.servers "127.0.0.1:9092"
                :topic             "topic-name"
                :impatient?        true
                :retry-count       2}
     :sink     {:implementation :file
                :filename "es-docs.ndjson"}}))
