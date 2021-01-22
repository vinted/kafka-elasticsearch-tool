(ns replay.transform.uri
  (:require [clojure.string :as str]))

(defn transform-uri [uri transforms]
  (reduce (fn [uri {:keys [match replacement]}]
            (str/replace uri (re-pattern match) replacement)) uri transforms))

(comment
  (replay.transform.uri/transform-uri
    "/foo/bar/baz"
    [{:match "bar"
      :replacement "moo"}]))

(defn construct-endpoint [doc replay-conf]
  (or (:uri replay-conf)
      (let [uri (get doc (keyword (:uri_attr replay-conf)))]
        (transform-uri uri (:uri-transforms replay-conf)))))

(defn transform
  "Applies string transformations in order on the uri."
  [{:keys [uri transforms] :as request}]
  (assoc request :transformed-uri (transform-uri uri transforms)))
