(ns ops.kafka-to-kafka
  (:require [sink :as sink]
            [source :as source]))

(def default-opts
  {:max_docs 5
   :source   {:implementation    :kafka
              :bootstrap.servers "127.0.0.1:9092"
              :topic             nil
              :decode-value?     false}
   :sink     {:implementation    :kafka
              :bootstrap.servers "127.0.0.1:9092"
              :topic             nil
              :encode-value?     false}})

(defn execute
  "Read records from a Kafka topic(s) and writes them another topic."
  [opts]
  (let [source-opts (merge (:source default-opts) (:source opts))
        sink-opts (merge (:sink default-opts) (:sink opts))
        records (source/fetch! (assoc opts :source source-opts))]
    (sink/store! records (assoc opts :sink sink-opts))))

(comment
  (execute
    {:max_docs 1
     :source   {:topic             "source-topic"
                :bootstrap.servers "127.0.0.1:9092"}
     :sink     {:topic             "sink-topic"
                :bootstrap.servers "127.0.0.1:9092"}}))
