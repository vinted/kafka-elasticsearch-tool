(ns source.elasticsearch
  (:require [source.elasticsearch.records :as records]
            [source.elasticsearch.search-after-with-pit :as pit]))

(defn fetch [opts]
  (let [strategy (-> opts :source :strategy)]
    (case strategy
      :search-after-with-pit (pit/fetch opts)
      (records/fetch opts))))

(comment
  (fetch {:max_docs 10
          :source   {:remote {:host "http://localhost:9200"}
                     :index  "source-index-name"}})

  (fetch {:max_docs 10
          :source   {:remote   {:host "http://localhost:9200"}
                     :index    "source-index-name"
                     :strategy :search-after-with-pit}}))
