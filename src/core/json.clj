(ns core.json
  (:require [jsonista.core :as json]))

(defn decode
  ([obj] (decode obj true))
  ([obj keywordize?] (json/read-value obj (json/object-mapper {:decode-key-fn keywordize?}))))

(defn encode
  ([obj] (encode obj {}))
  ([obj object-mapper-opts] (json/write-value-as-string obj (json/object-mapper object-mapper-opts))))

(defn encode-vanilla [obj] (json/write-value-as-string obj))

(defn read-file [^String file]
  (decode (slurp file)))
