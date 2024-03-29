(ns replay.transform.uri
  (:require [clojure.string :as str]
            [replay.transform.selector :as selector]))

(defn transform-uri [^String uri transforms]
  (reduce (fn [uri {:keys [match replacement]}]
            (str/replace uri (re-pattern match) replacement)) uri transforms))

(comment
  (replay.transform.uri/transform-uri
    "/foo/bar/baz"
    [{:match "bar"
      :replacement "moo"}]))

(defn extract-uri [doc replay-conf]
  (let [uri-attr-path (:uri_attr replay-conf)
        ; uri-attr-path should can be either string of a list of keys to get-in
        uri-selector (selector/path->selector uri-attr-path)]
    (get-in doc uri-selector)))

(defn construct-endpoint
  "Either a hardcoded uri or transformed uri from the original query."
  [doc replay-conf]
  (or (:uri replay-conf)
      (let [uri (extract-uri doc replay-conf)]
        (transform-uri uri (:uri-transforms replay-conf)))))

(comment
  (replay.transform.uri/construct-endpoint
    {:uri "/foo/bar/baz"}
    {:uri_attr       "uri"
     :uri-transforms [{:match       "bar"
                       :replacement "XXXX"}]})

  (replay.transform.uri/construct-endpoint
    {:elasticsearch {:request {:uri "/foo/bar/baz"}}}
    {:uri_attr       ["elasticsearch" "request" "uri"]
     :uri-transforms [{:match       "bar"
                       :replacement "XXXX"}]}))

(defn transform
  "Applies string transformations in order on the uri."
  [{:keys [^String uri transforms] :as request}]
  (assoc request :transformed-uri (transform-uri uri transforms)))

(comment
  (replay.transform.uri/transform
    {:uri "/foo/bar/baz"
     :transforms [{:match "bar"
                   :replacement "XXXX"}]}))

(defn get-index-or-alias
  "Given Elasticsearch uri, extracts index or alias name"
  [endpoint]
  (or (last (re-find #"^/(.*)/_search" endpoint))
      (last (re-find #"^https?://.+(:.+)?/(.*)/_search" endpoint))))
