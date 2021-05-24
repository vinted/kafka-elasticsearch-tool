(ns ops.stdin-to-kafka
  (:require [sink :as sink]
            [source :as source]
            [jsonista.core :as json])
  (:import (sink KafkaRecord)))

(def default-es-to-kafka-config
  {:max_docs nil
   :source   {:implementation :stdin
              :decode-value? true}
   :sink     {:implementation    :kafka
              :topic             "sink-topic"
              :bootstrap.servers "127.0.0.1:9092"}})

(defn es-record? [record]
  (and (contains? record :_id)
       (contains? record :_source)))

(defn execute [opts]
  (sink/store!
    (map (fn [record]
           (if (string? record)
             (KafkaRecord. nil record nil)
             (if (es-record? record)
              (KafkaRecord.
                (get record :_id (get record :_id))
                (get record :_source (get record :_source))
                (dissoc record :_id :_source))
              record)))
         (source/fetch! (assoc opts :source (merge (:source default-es-to-kafka-config)
                                                   (:source opts)))))
    (assoc opts :sink
                (merge (:sink default-es-to-kafka-config)
                       (:sink opts)))))

(comment
  (with-in-str
    (json/write-value-as-string
      {:_id "123" :_source {:foo "bar"}})
    (ops.stdin-to-kafka/execute
      {:max_docs 1
       :source   {:implementation :stdin
                  :decode-value?  true}
       :sink     {:topic "stdin-to-kafka-test"
                  :bootstrap.servers "127.0.0.1:9092"}}))

  (with-in-str
    (json/write-value-as-string
      {:_id "123" :_source {:foo "bar"}})
    (ops.stdin-to-kafka/execute
      {:max_docs 1
       :source   {:implementation :stdin
                  :decode-value?  false}
       :sink     {:topic "stdin-to-kafka-test"
                  :bootstrap.servers "127.0.0.1:9092"}}))

  (with-in-str
    (slurp "kafka-file.json")
    (ops.stdin-to-kafka/execute
      {:max_docs 1
       :source   {:implementation :stdin
                  :decode-value?  true}
       :sink     {:topic "stdin-to-kafka-test"
                  :bootstrap.servers "127.0.0.1:9092"}}))

  (with-in-str
    (slurp "kafka-file.json")
    (ops.stdin-to-kafka/execute
      {:max_docs 1
       :source   {:implementation :stdin
                  :decode-value?  false}
       :sink     {:topic "stdin-to-kafka-test"
                  :bootstrap.servers "127.0.0.1:9092"}})))
