(ns ops.krp-to-ndjson
  (:require [core.json :as json]
            [core.async :as async]
            [sink :as sink]
            [source :as source]
            [clojure.tools.logging :as log]))

(def default-krp-ndjson-config
  {:max_docs nil
   :source   {:implementation :krp
              :connection.url "http://localhost:8082"}
   :sink     {:implementation :file
              :filename       nil
              :partition-size nil}})

(defn one-consumer [opts]
  (sink/store!
    (mapcat (fn [record]
              [{:value (json/encode {:index {:_id    (:key record)
                                             :_index (:topic record)}})}
               (assoc record :value (json/encode (:value record)))])
            (source/fetch! (assoc opts :source (merge (:source default-krp-ndjson-config)
                                                      (:source opts)))))
    (assoc opts :sink
                (merge (:sink default-krp-ndjson-config)
                       (:sink opts)))))

(defn execute [opts]
  (let [concurency (-> opts :source :concurrency)
        max-docs-per-partition (int (Math/ceil (/ (-> opts :max_docs) concurency)))]
    (if concurency
      (doseq [fut (map (fn [thread-nr]
                        (future
                          (log/infof "Started KRP consumer '%s'" thread-nr)
                          (one-consumer (-> opts
                                            (assoc-in [:max_docs] max-docs-per-partition)
                                            (assoc-in [:source :partitions] [thread-nr])
                                            (update-in [:source :consumer.name] (fn [consumer-instance] (str thread-nr "-" consumer-instance)))
                                            (update-in [:sink :filename] (fn [filename] (str filename "-" thread-nr)))))))
                      (range concurency))]
       @fut)
      (one-consumer opts))))

(comment
  (execute
    {:max_docs nil
     :source   {:connection.url "http://localhost:8082"
                :topic          "keywords-test-topic"
                :offset         0}
     :sink     {:filename       "ndjson/ket-docs.ndjson"
                :partition-size 10000}})

  (execute
    {:max_docs 2000
     :source   {:connection.url           "http://localhost:9200"
                :topic                    "deep-replay"
                :group.id                 "krp-group2_instance"
                :consumer.name            "krp2_instance"
                :offset                   0
                :concurrency              20
                :delete.consumer.instance false}
     :sink     {:filename       "directory/docs"
                :partition-size 1000}}))
