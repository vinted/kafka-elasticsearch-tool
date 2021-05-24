(ns sink.stdout
  (:require [jsonista.core :as json])
  (:import (java.io BufferedWriter PrintWriter)))

(defn store!
  "JSON encodes each record and writes it to stdout."
  [records opts]
  (let [^PrintWriter writer (PrintWriter. (BufferedWriter. *out* (* 1024 8192)))]
    (doseq [record records]
      (.println writer (json/write-value-as-string record)))
    (.flush writer)))

(comment
  (sink.stdout/store!
    [{:value "line1"}
     {:value "line2"}]
    {:sink {}}))
