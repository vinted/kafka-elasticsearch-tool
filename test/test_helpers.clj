(ns test-helpers
  (:require [clojure.tools.logging :as log])
  (:import (java.util.concurrent ExecutionException)
           (java.util Map)
           (org.apache.kafka.clients.admin AdminClient NewTopic)))

(def default-exponential-backoff-params
  {:time 100
   :rate 2
   :max  5000
   :p?   identity})

(defn exponential-backoff
  ([f] (exponential-backoff f default-exponential-backoff-params))
  ([f {:keys [time rate max p?] :as opts}]
   (if (>= time max) ;; we're over budget, just call f
     (f)
     (try
       (f)
       (catch Throwable t
         (if (p? t)
           (do
             (Thread/sleep time)
             (exponential-backoff f (assoc opts :time (* time rate))))
           (throw t)))))))

(defn create-topics! [^AdminClient kafka-admin topics]
  (try
    (.get (.all (.createTopics kafka-admin (map (fn [^String topic] (NewTopic. topic 1 (short 1))) topics))))
    (catch ExecutionException e
      (log/warnf "Creating topics '%s' got exception '%s'" topics e))))

(defn delete-topic! [^AdminClient kafka-admin topics]
  (try
    (.get (.all (.deleteTopics kafka-admin topics)))
    (catch ExecutionException e
      (log/warnf "Deleting topics '%s' got exception '%s'" topics e))))

(defn recreate-topics! [topics ^Map opts]
  (let [^AdminClient kafka-admin (AdminClient/create opts)]
    (delete-topic! kafka-admin topics)
    (Thread/sleep 500)
    (create-topics! kafka-admin topics)
    (exponential-backoff (fn []
                           (log/infof "Details of a topics: %s" topics)
                           (.get (.all (.describeTopics kafka-admin topics)))))))
