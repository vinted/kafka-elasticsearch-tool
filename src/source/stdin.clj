(ns source.stdin
  (:require [clojure.core.async :as a]
            [core.async :as async]
            [core.json :as json])
  (:import (java.io BufferedReader)))

(def defaults
  {:max_docs 1
   :source {:implementation :stdin
            :decode-value?  true}})

(defn fetch
  "Reads lines from STDIN and returns a lazy sequence of lines."
  [opts]
  (let [decode-value? (get-in opts [:source :decode-value?] true)
        line-in-chan (a/chan 128)]
    (a/go
      (with-open [^BufferedReader rdr (BufferedReader. *in*)]
        (loop [^String line (.readLine rdr)]
          (if (= nil line)
            (a/close! line-in-chan)
            (do
              (a/>!! line-in-chan
                     (if decode-value? (json/decode line) line))
              (recur (.readLine rdr)))))))
    (if-let [max-docs (get opts :max_docs)]
      (take max-docs (async/seq-of-chan line-in-chan))
      (async/seq-of-chan line-in-chan))))

(comment
  (with-in-str
    "{\"foo\":\"bar\"}"
    (println
      (source.stdin/fetch
        {:max_docs 1
         :source   {:decode-value? true}}))))
