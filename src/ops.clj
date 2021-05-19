(ns ops
  (:require [ops.es-to-es :as es-to-es]
            [ops.es-to-kafka :as es-to-kafka]
            [ops.es-to-ndjson :as es-to-ndjson]
            [ops.kafka-to-es :as kafka-to-es]
            [ops.kafka-to-kafka :as kafka-to-kafka]
            [ops.kafka-to-ndjson :as kafka-to-ndjson]
            [ops.krp-to-ndjson :as krp-to-ndjson]
            [ops.kafka-to-stdout :as kafka-to-stdout]
            [replay.core :as replay]
            [replay.deep :as replay.deep]
            [replay.impact :as replay.impact]
            [polyglot :as polyglot]))

(def operations
  [{:name       "kafka-to-kafka"
    :handler-fn kafka-to-kafka/execute
    :docs       (:doc (meta #'kafka-to-kafka/execute))
    :defaults   kafka-to-kafka/default-opts}
   {:name       "kafka-to-ndjson"
    :handler-fn kafka-to-ndjson/execute
    :docs       (:doc (meta #'kafka-to-ndjson/execute))
    :defaults   kafka-to-ndjson/default-kafka-ndjson-config}
   {:name       "kafka-to-elasticsearch"
    :handler-fn kafka-to-es/kafka-to-es
    :docs       (:doc (meta #'kafka-to-es/kafka-to-es))
    :defaults   kafka-to-es/default-opts}
   {:name       "kafka-to-stdout"
    :handler-fn kafka-to-stdout/execute
    :docs       (:doc (meta #'kafka-to-stdout/execute))
    :defaults   kafka-to-stdout/default-opts}
   {:name       "elasticsearch-to-elasticsearch"
    :handler-fn es-to-es/reindex!
    :docs       (:doc (meta #'es-to-es/reindex!))
    :defaults   es-to-es/default-reindex-config}
   {:name       "reindex"
    :handler-fn es-to-es/reindex!
    :docs       (:doc (meta #'es-to-es/reindex!))
    :defaults   es-to-es/default-reindex-config}
   {:name       "elasticsearch-to-kafka"
    :handler-fn es-to-kafka/es->kafka
    :docs       (:doc (meta #'es-to-kafka/es->kafka))
    :defaults   es-to-kafka/default-es-to-kafka-config}
   {:name       "elasticsearch-to-ndjson"
    :handler-fn es-to-ndjson/es->ndjson
    :docs       (:doc (meta #'es-to-ndjson/es->ndjson))
    :defaults   es-to-ndjson/default-es-to-ndjson-config}
   {:name       "krp-to-ndjson"
    :handler-fn krp-to-ndjson/execute
    :docs       (:doc (meta #'krp-to-ndjson/execute))
    :defaults   krp-to-ndjson/default-krp-ndjson-config}
   {:name       "replay"
    :handler-fn replay/replay
    :docs       (:doc (meta #'replay/replay))
    :defaults   replay/defaults}
   {:name       "deep-replay"
    :handler-fn replay.deep/replay
    :docs       (:doc (meta #'replay.deep/replay))
    :defaults   {}}
   {:name       "polyglot"
    :handler-fn polyglot/apply-transformation
    :docs       (:doc (meta #'polyglot/apply-transformation))
    :defaults   polyglot/polyglot-defaults}
   {:name       "replay-for-impact"
    :handler-fn replay.impact/execute
    :docs       (:doc (meta #'replay.impact/execute))
    :defaults   replay.impact/defaults}])
