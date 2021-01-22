(ns ops.kafka-to-elasticsearch-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.tools.logging :as log]
            [core.ilm :as ilm]
            [ops.kafka-to-es :as kafka-to-es]
            [sink.kafka :as kafka]
            [source.elasticsearch :as es]
            [source.kafka :as source-kafka]
            [scroll.request :as r]
            [test-helpers :as th]))

(defn test-es-host []
  (or (System/getenv "ES_HOST") "http://localhost:9200"))

(def source-opts
  {:impatient? true
   :retry-count 1})

(defn wait-for-elasticsearch [f]
  (r/execute-request
    {:method :get
     :url    (format "%s/_cluster/health" (test-es-host))})
  (f))

(use-fixtures :once wait-for-elasticsearch)

(deftest ^:integration ^:kafka kafka-to-es
  (let [source-topic "source-topic"
        boostrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS") "127.0.0.1:9092")
        es-host (test-es-host)
        records [{:key     "key"
                  :value   {:test "test"}
                  :headers {:meta "meta"}}]
        dest-index-name "kafka-to-es-test-index"]
    ; SCENARIO:
    ; delete es index
    ; delete source topic
    ; create source topic
    ; put a doc in kafka topic
    ; check that the doc is in kafka topic
    ; execute op
    ; check that the elasticsearch has the doc from kafka
    (log/infof "Deleted dest index='%s' at '%s': %s"
               dest-index-name es-host (ilm/delete-index! es-host dest-index-name))
    (th/recreate-topics! [source-topic] {"bootstrap.servers" boostrap-servers})
    (is (nil? (kafka/store! records {:sink {:topic             source-topic
                                            :bootstrap.servers boostrap-servers}})))
    (is (seq (source-kafka/fetch {:max_docs 1
                                  :source   (merge {:topic             source-topic
                                                    :bootstrap.servers boostrap-servers}
                                                   source-opts)})))
    (is (nil? (kafka-to-es/kafka-to-es {:max_docs 1
                                        :source   (merge {:topic             source-topic
                                                          :bootstrap.servers boostrap-servers}
                                                         source-opts)
                                        :dest     {:index  dest-index-name
                                                   :remote {:host es-host}}
                                        :sink     {}})))
    (ilm/refresh-index! es-host dest-index-name)
    (let [docs (es/fetch {:max_docs 10
                          :source   {:remote {:host es-host}
                                     :index  dest-index-name}})]
      (is (seq docs))
      (is (= (set (map :key records))
             (set (map :_id docs))))
      (is (= (set (map :value records))
             (set (map :_source docs)))))))

(deftest ^:integration ^:kafka kafka-to-es-no-key
  (let [source-topic "source-topic"
        boostrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS") "127.0.0.1:9092")
        es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        records [{:key     nil
                  :value   {:test "test"}
                  :headers {:meta "meta"}}]
        dest-index-name "kafka-to-es-test-index"]
    ; SCENARIO:
    ; delete es index
    ; delete source topic
    ; create source topic
    ; put a doc in kafka topic
    ; check that the doc is in kafka topic
    ; execute op
    ; check that the elasticsearch has the doc from kafka
    (log/infof "Deleted dest index='%s' at '%s': %s"
               dest-index-name es-host (ilm/delete-index! es-host dest-index-name))
    (th/recreate-topics! [source-topic] {"bootstrap.servers" boostrap-servers})
    (is (nil? (kafka/store! records {:sink {:topic             source-topic
                                            :bootstrap.servers boostrap-servers}})))
    (is (seq (source-kafka/fetch {:max_docs 1
                                  :source   (merge {:topic             source-topic
                                                    :bootstrap.servers boostrap-servers}
                                                   source-opts)})))
    (is (nil? (kafka-to-es/kafka-to-es {:max_docs 1
                                        :source   (merge {:topic             source-topic
                                                          :bootstrap.servers boostrap-servers}
                                                         source-opts)
                                        :dest     {:index  dest-index-name
                                                   :remote {:host es-host}}
                                        :sink     {}})))
    (ilm/refresh-index! es-host dest-index-name)
    (let [docs (es/fetch {:max_docs 10
                          :source   {:remote {:host es-host}
                                     :index  dest-index-name}})]
      (is (seq docs))
      (is (= (set ["source-topic+0+0"]) (set (map :_id docs))))
      (is (= (set (map :value records))
             (set (map :_source docs)))))))
