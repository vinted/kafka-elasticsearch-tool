(ns source
  (:require [clojure.tools.logging :as log]
            [source.elasticsearch :as elasticsearch]
            [source.kafka :as kafka]
            [source.krp :as krp]))

(defn fetch! [opts]
  (let [source-implementation-id (keyword (get-in opts [:source :implementation]))]
    (case source-implementation-id
      :elasticsearch (elasticsearch/fetch opts)
      :kafka (kafka/fetch opts)
      :krp (krp/fetch opts)
      (log/errorf "No such source implementation '%s'" source-implementation-id))))
