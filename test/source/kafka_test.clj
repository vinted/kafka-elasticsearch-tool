(ns source.kafka-test
  (:require [clojure.test :refer [deftest is testing]]
            [source.kafka :as source-kafka]
            [sink.kafka :as kafka]
            [test-helpers :as th]))

(def source-opts
  {:impatient? true
   :retry-count 1})

(deftest ^:integration ^:kafka kafka-consumer-kwyeordize-flag
  (let [test-topic "keywords-test-topic"
        boostrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS")
                             "127.0.0.1:9092")
        kafka-opts {:topic test-topic
                    :bootstrap.servers boostrap-servers}
        sink-opts {:sink kafka-opts}
        records [{:key     "key"
                  :value   {:test "test"}
                  :headers {:meta "meta"}}]]
    (th/recreate-topics! [test-topic] {"bootstrap.servers" boostrap-servers})
    (is (empty? (source-kafka/fetch {:max_docs 1
                                     :source   (merge kafka-opts source-opts)})))
    (is (nil? (kafka/store! records sink-opts)))

    (testing "by default keys should be keywords"
      (let [[first-record :as actual-records]
            (source-kafka/fetch {:max_docs 1
                                 :source   (merge kafka-opts source-opts)})]
        (is (seq actual-records))
        (is (= 1 (count actual-records)))
        (is (keyword? (first (keys (:value first-record)))))
        (is (keyword? (first (keys (:headers first-record)))))))

    (testing "keys should be strings"
      (let [[first-record :as actual-records]
            (source-kafka/fetch {:max_docs 1
                                 :source   (assoc (merge kafka-opts source-opts)
                                             :keywordize? false)})]
        (is (seq actual-records))
        (is (= 1 (count actual-records)))
        (is (string? (first (keys (:value first-record)))))
        (is (string? (first (keys (:headers first-record)))))))

    (testing "not decode value strings"
      (let [[first-record :as actual-records]
            (source-kafka/fetch {:max_docs 1
                                 :source   (assoc (merge kafka-opts source-opts)
                                             :decode-value? false)})]
        (is (seq actual-records))
        (is (= 1 (count actual-records)))
        (is (string? (:value first-record)))
        (is (keyword? (first (keys (:headers first-record)))))))))
