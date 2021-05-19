(ns sink.stdout-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [sink.stdout :as stdout]))

(deftest sinking-to-stdout
  (let [records [{:value "line1"}
                 {:value "line2"}]
        opts {:sink {}}]
    (is (= "{\"value\":\"line1\"}\n{\"value\":\"line2\"}"
           (str/trim
             (with-out-str
               (stdout/store! records opts)))))))
