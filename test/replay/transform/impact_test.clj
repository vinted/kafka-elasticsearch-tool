(ns replay.transform.impact-test
  (:require [clojure.test :refer [deftest is]]
            [replay.transform.impact :as txs]))

(deftest multiple-transforms
  (let [query {:query {:match_all {}}}
        transforms [{:lang :sci :id :a :script "(fn [query value] (assoc query :a value))" :vals [1]}
                    {:lang :js :id :d :script "(query, value) => { query['d'] = value; return query; }" :vals ["a"]}
                    {:lang :jq :id :d :script ". as [$query, $value] | $query | .e = $value" :vals ["X"]}]]
    (let [[{:keys [request variation]} & _ :as queries] (txs/generate-queries query transforms)]
      (is (= 1 (count queries)))
      (is (= {:a     1
              :d     "a"
              :e     "X"
              :query {:match_all {}}} request))
      (is (= '({:id    :a
               :value 1}
              {:id    :d
               :value "a"}
              {:id    :d
               :value "X"}) variation)))))

(deftest transforms-order
  (let [query {:query {:match_all {}}}
        transforms [{:lang :sci :id :a :script "(fn [query value] (assoc query :a value))" :vals [1]}
                    {:lang :js :id :d :script "(query, value) => { query['a'] = value; return query; }" :vals [2]}
                    {:lang :jq :id :d :script ". as [$query, $value] | $query | .a = $value" :vals [3]}]]
    (let [[{:keys [request variation]} & _ :as queries] (txs/generate-queries query transforms)]
      (is (= 1 (count queries)))
      (is (= {:a     3
              :query {:match_all {}}} request))
      (is (= '({:id    :a
                :value 1}
               {:id    :d
                :value 2}
               {:id    :d
                :value 3}) variation)))))

(deftest transforms-count
  (let [query {:query {:match_all {}}}
        transforms [{:lang :sci :id :a :script "(fn [query value] (assoc query :a value))" :vals [1 2]}
                    {:lang :js :id :d :script "(query, value) => { query['a'] = value; return query; }" :vals [1 2 3]}
                    {:lang :jq :id :d :script ". as [$query, $value] | $query | .a = $value" :vals [1 2 3 4]}]]
    (let [queries (txs/generate-queries query transforms)]
      (is (= (reduce * (map (fn [tf] (count (:vals tf))) transforms))
             (count queries))))))
