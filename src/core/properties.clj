(ns core.properties
  (:require [clojure.string :as str])
  (:import (java.util Properties)))

(defn ^Properties opts->properties [opts]
  (reduce (fn [^Properties props [k v]]
            (.put props ^String (name k) ^String v)
            props)
          (Properties.)
          opts))

(defn opts-valid? [required-key opts]
  (assert
    (not (and (str/blank? (get opts required-key))
              (str/blank? (get opts (keyword required-key)))))
    (format "Required kafka param='%s' option is not provided."
            required-key)))
