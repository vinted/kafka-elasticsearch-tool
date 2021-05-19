(ns ops.kafka-to-stdout)

(def default-opts
  {:max_docs 5
   :source   {:implementation    :kafka
              :bootstrap.servers "127.0.0.1:9092"
              :topic             nil
              :decode-value?     false
              :impatient?        true}
   :sink     {:implementation :stdout}})

(defn execute
  "Reads records from Kafka and writes them to STDOUT."
  [opts]
  (let [source-opts (merge (:source default-opts) (:source opts))
        sink-opts (merge (:sink default-opts) (:sink opts))
        records (source/fetch! (assoc opts :source source-opts))]
    (sink/store! records (assoc opts :sink sink-opts))))

(comment
  (execute
    {:max_docs 1
     :source   {:topic             "source-topic"
                :bootstrap.servers "127.0.0.1:9092"}}))
