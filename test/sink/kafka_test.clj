(ns sink.kafka-test
  (:require [clojure.test :refer [deftest is]]
            [core.json :as json]
            [sink.kafka :as kafka]
            [source.kafka :as source-kafka]
            [test-helpers :as th]))

(def source-opts
  {:impatient? true
   :retry-count 1})

; When working from REPL is source.kafka if modified then the test fails
; you need to run tests once again
(deftest ^:integration ^:kafka sending-data-to-kafka-with-key-and-headers
  (let [test-topic "test-topic"
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
    (let [[first-record :as actual-records]
          (source-kafka/fetch {:max_docs 1
                               :source   (merge kafka-opts source-opts)})]
      (is (seq actual-records))
      (is (= 1 (count actual-records)))
      (is (= (:value (first records))
             (:value first-record)))
      (is (= (:key (first records))
             (:key first-record)))
      (is (= (:headers (first records))
             (:headers first-record))))))

(deftest ^:integration ^:kafka sending-data-to-kafka-without-encoding
  (let [test-topic "test-topic-sink-raw"
        boostrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS")
                             "127.0.0.1:9092")
        kafka-opts {:topic test-topic
                    :bootstrap.servers boostrap-servers}
        sink-opts {:sink kafka-opts}
        value {:test "test"}
        records [{:key     "key"
                  :value   (json/encode value)
                  :headers {:meta "meta"}}]]
    (th/recreate-topics! [test-topic] {"bootstrap.servers" boostrap-servers})
    (is (empty? (source-kafka/fetch {:max_docs 1
                                     :source   (merge kafka-opts source-opts)})))
    (is (nil? (kafka/store! records (assoc-in sink-opts
                                              [:sink :encode-value?] false))))
    (let [[first-record :as actual-records]
          (source-kafka/fetch {:max_docs 1
                               :source   (merge kafka-opts source-opts)})]
      (is (seq actual-records))
      (is (= 1 (count actual-records)))
      (is (map? (:value first-record))))))
