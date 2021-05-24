(ns sink.elasticsearch
  (:require [clojure.tools.logging :as log]
            [sink.elasticsearch.index :as elasticsearch]))

(defn store! [records opts]
  (log/infof "Sinking in Elasticsearch")
  (elasticsearch/store! records (:sink opts)))

(comment
  (sink.elasticsearch/store!
    [{:_id "123" :_source {:foo "bar"}}]
    {:sink {:connection.url "http://localhost:9200"
            :dest.index     "foo_index"}}))
