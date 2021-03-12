(ns replay.transform.impact
  (:require [polyglot.sci :as sci]
            [polyglot.js :as js]
            [core.json :as json]))

(defn cart [colls]
  (if (empty? colls)
    '(())
    (for [more (cart (rest colls))
          x (first colls)]
      (cons x more))))

(defn create-transformation-variations
  [compiled-transformation]
  (let [specialize (fn [compiled-transformation]
                     (reduce (fn [transformations-variation value]
                               (conj transformations-variation
                                     (-> compiled-transformation
                                         (assoc :value value)
                                         (dissoc :vals))))
                             []
                             (:vals compiled-transformation)))]
    (cart (map specialize compiled-transformation))))

(defn compile-transforms [transformations]
  (map (fn [transformation]
         (assoc transformation :fn (case (keyword (:lang transformation))
                                     :js (js/script->transform-fn-vals (:script transformation))
                                     :sci (sci/sci-compile (:script transformation))
                                     (throw (Exception. (format "Language code '%s' is not supported"
                                                                (:lang transformation)))))))
       transformations))

(defn apply-all [query-map transform-variations]
  (map (fn [variation]
         (let [fns-to-apply (map (fn [with-fn] [(:lang with-fn) (:fn with-fn) (:value with-fn)]) variation)]
           {:variation variation
            :request   (reduce (fn [acc [lang afn aval]]
                                 (case (keyword lang)
                                   :sci (afn acc aval)
                                   ; first param to js transformation should be json string
                                   ; js transformation always returns a string
                                   ; we need to decode the response string
                                   :js (json/decode (afn (json/encode acc) aval))
                                   (throw (Exception. (format "Language code '%s' is not supported" lang)))))
                               query-map
                               fns-to-apply)})) transform-variations))

(defn generate-queries
  "Creates a cartesian product of all the variations of the provided vals and scripts.
  Applies these transformations on the query hashmap.
  Each transformation is applied in order on the query.
  Returns a list of maps with :variation and :request keys:
    - :variation is for debugging purposes
    - :request is a hashmap that should be a valid ES query.
  Query is expected be a decoded hashmap.
  Transformations are hashmaps as per example."
  [query transformations]
  (map (fn [generated-query]
         (update generated-query :variation (fn [variation]
                                              (map #(select-keys % [:id :value]) variation))))
       (apply-all query (-> transformations
                            (compile-transforms)
                            (create-transformation-variations)))))

(comment
  (replay.transform.impact/generate-queries
    {:query {:match_all {}}}
    [{:lang :sci :id :a :script "(fn [query value] (assoc query :a value))" :vals [1 2 3]}
     {:lang :sci :id :b :script "(fn [query value] (assoc query :b value))" :vals [10 20 30]}
     {:lang :sci :id :c :script "(fn [query value] (assoc query :c value))" :vals [100 200 300]}
     {:lang :js :id :d :script "(query, value) => { query['d'] = value; return query; }" :vals ["a" "b" "c"]}]))
