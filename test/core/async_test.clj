(ns core.async-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :as a]
            [core.async :as async]))

(deftest laziness
  (let [n (rand-int 100)
        input-seq (range n)
        afn (fn [input-value c]
              (Thread/sleep (rand-int 500))
              (a/>!! c (inc input-value))
              (a/close! c))
        output-seq (async/map-pipeline-async afn 1030 input-seq)]
    (is (= n (count output-seq)))
    (is (not= input-seq output-seq))
    (is (= (set (map inc input-seq)) (set output-seq)))))
