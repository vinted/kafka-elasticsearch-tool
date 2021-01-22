(ns core.deep-merge)

(defn deep-merge
  "Recursively merges maps.
  Taken from: https://clojure.atlassian.net/browse/CLJ-1468"
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(comment
  (core.deep-merge/deep-merge
    {:a {:b {:x "x"}}}
    {:a {:b {:y "y"}}})
  ;=> {:a {:b {:x "x", :y "y"}}}
  )
