(ns ops.kafka-to-kafka-test
  (:require [clojure.test :refer [deftest is]]
            [ops.kafka-to-kafka :as kafka-to-kafka]
            [source.kafka :as source-kafka]
            [sink.kafka :as kafka]
            [test-helpers :as th]))

(def source-opts
  {:impatient? true
   :retry-count 1})

(deftest ^:integration ^:kafka kafka-to-kafka-op
  (let [source-topic "source-topic"
        sink-topic "sink-topic"
        boostrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS")
                             "127.0.0.1:9092")
        records [{:key     "key"
                  :value   {:test "test"}
                  :headers {:meta "meta"}}]]
    ; SCENARIO:
    ; delete source topic
    ; delete sink topic
    ; check that sink topic doesnt't have docs
    ; write some data to source topic
    ; run op
    ; check sink topic for the document
    (th/recreate-topics! [source-topic sink-topic] {"bootstrap.servers" boostrap-servers})
    (is (empty? (source-kafka/fetch {:max_docs 1
                                     :source   (merge {:topic source-topic
                                                       :bootstrap.servers boostrap-servers}
                                                      source-opts)})))
    (is (empty? (source-kafka/fetch {:max_docs 1
                                     :source   (merge {:topic sink-topic
                                                       :bootstrap.servers boostrap-servers}
                                                      source-opts)})))
    (is (nil? (kafka/store! records {:sink {:topic             source-topic
                                            :bootstrap.servers boostrap-servers}})))
    (is (seq (source-kafka/fetch {:max_docs 1
                                  :source   (merge {:topic             source-topic
                                                    :bootstrap.servers boostrap-servers}
                                                   source-opts)})))
    (is (empty? (source-kafka/fetch {:max_docs 1
                                     :source   (merge {:topic sink-topic
                                                       :bootstrap.servers boostrap-servers}
                                                      source-opts)})))
    (is (nil? (kafka-to-kafka/execute {:max_docs 1
                                       :source   (merge {:topic             source-topic
                                                         :bootstrap.servers boostrap-servers}
                                                        source-opts)
                                       :sink     {:topic             sink-topic
                                                  :bootstrap.servers boostrap-servers}})))
    (let [[first-record :as actual-records]
          (source-kafka/fetch {:max_docs 1
                               :source   (merge {:topic             sink-topic
                                                 :bootstrap.servers boostrap-servers}
                                                source-opts)})]
      (is (seq actual-records))
      (is (= 1 (count actual-records)))
      (is (= (:value (first records))
             (:value first-record)))
      (is (= (:key (first records))
             (:key first-record)))
      (is (= (:headers (first records))
             (:headers first-record))))))
