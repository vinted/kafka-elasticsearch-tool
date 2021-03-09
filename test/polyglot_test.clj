(ns polyglot-test
  (:require [clojure.test :refer :all]
            [core.async :as a]
            [core.json :as json]
            [polyglot.js :as js]
            [polyglot :as polyglot]))

(def empty-map {})
(def nested-map {:foo {:bar {:baz "quux"}}})

(deftest polyglot-string-transformations
  (testing "simple cases"
    (let [input-string (json/encode empty-map)]
      (is (= input-string (js/string->string input-string "(s) => s")))
      (is (= (json/encode {"foo" "bar"})
             (js/string->string input-string "(s) => {s['foo'] = 'bar'; return s}")))))

  (testing "deeply nested map"
    (let [input-string (json/encode nested-map)]
      (is (= (json/encode (assoc-in nested-map [:foo :bar :quuz]  "corge"))
             (js/string->string input-string
                                "(s) => {
                                  s['foo']['bar']['quuz'] = 'corge';
                                  return s;
                                }")))))

  (testing "invalid script"
    (let [input-string (json/encode empty-map)]
      (is (thrown? Exception (= input-string (js/string->string input-string "(s) => function")))))))

(deftest multithreaded-js
  (let [data (json/encode {"foo" "bar"})
        tf (polyglot.js/script->transform-fn
             "(s) => {
               s['foo'] = 'corge';
               return s;
             }")]
    (is (= 100 (count (a/map-pipeline (fn [d] (tf d)) 10 (repeat 100 data)))))))

(deftest polyglot-map-transformations
  (testing "simple cases"
    (let [m empty-map]
      (is (= m (polyglot/map->map m "(s) => s")))
      (is (= {:foo "bar"}
             (polyglot/map->map m "(s) => {s['foo'] = 'bar'; return s}")))))

  (testing "deeply nested map"
    (let [m nested-map]
      (is (= (assoc-in nested-map [:foo :bar :quuz] "corge")
             (polyglot/map->map m "(s) => {s['foo']['bar']['quuz'] = 'corge'; return s}")))))

  (testing "script expects two arguments"
    (let [m empty-map]
      (is (= m (polyglot/map->map m "(s) => s")))
      (is (= {:foo "bar"}
             (polyglot/map->map m "(s, s1) => {s['foo'] = 'bar'; return s}")))))

  (testing "invalid script"
    (let [m empty-map]
      (is (thrown? Exception (= m (polyglot/map->map m "(s) => function")))))))
