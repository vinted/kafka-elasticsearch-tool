(ns sink
  (:require [clojure.tools.logging :as log]
            [sink.elasticsearch :as elasticsearch]
            [sink.file :as file]
            [sink.kafka :as kafka]))

(defrecord KafkaRecord [key value headers])

(defn store! [records opts]
  (let [sink-implementation-id (keyword (get-in opts [:sink :implementation]))]
    (case sink-implementation-id
      :kafka (kafka/store! records opts)
      :elasticsearch (elasticsearch/store! records opts)
      :file (file/store! records opts)
      (log/errorf "No such sink '%s' implementation!" sink-implementation-id))))
