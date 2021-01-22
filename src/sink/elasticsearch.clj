(ns sink.elasticsearch
  (:require [clojure.tools.logging :as log]
            [sink.elasticsearch.index :as elasticsearch]))

(defn store! [records opts]
  (log/infof "Sinking in Elasticsearch")
  (elasticsearch/store! records (:sink opts)))
