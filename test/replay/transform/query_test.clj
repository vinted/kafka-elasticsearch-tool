(ns replay.transform.query-test
  (:require [clojure.test :refer :all]
            [core.json :as json]
            [replay.transform.query :as transform.query]))

(deftest transforming-query-json-string
  (testing "mixing the supported languages for transforms"
    (let [query "{}"
          tfs [{:lang   :js
                :script "(q) => Object.assign(q, {'_source': true})"}
               {:lang   :sci
                :script "(fn [q] (assoc q :_explain true))"}]
          transform-fn (transform.query/transform-fn tfs)]
      (is (= {:_source true :_explain true}
             (json/decode (transform-fn query))))))

  (testing "the order of applied transformations"
    (let [query "{}"
          tfs [{:lang   :js
                :script "(q) => Object.assign(q, {'_source': 1})"}
               {:lang   :js
                :script "(q) => Object.assign(q, {'_source': 2})"}]
          transform-fn (transform.query/transform-fn tfs)]
      (is (= {:_source 2}
             (json/decode (transform-fn query))))))

  (testing "when bad language id is provided"
    (let [tfs [{:lang   :not-existing
                :script "(q) => q"}]]
      (is (thrown? Exception (transform.query/transform-fn tfs))))))
