(ns core.maps)

(defn remove-nil-vals [m] (into {} (filter second m)))
