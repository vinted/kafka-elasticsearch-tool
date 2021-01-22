(ns replay.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as cset]
            [core.json :as json]
            [replay.core :as replay]))

(deftest hits-count-extraction
  (testing "es <7 resp"
    (let [resp-body (json/read-file "test/resources/es6-resp.json")]
      (is (= {:value 0 :relation "eq"}
             (replay/hits-count resp-body)))))
  (testing "es 7 resp"
    (let [resp-body (json/read-file "test/resources/es7-resp.json")]
      (is (= {:value 0 :relation "eq"}
             (replay/hits-count resp-body))))))

(deftest resp-construction
  (let [input-doc (json/read-file "test/resources/replay-input-doc.json")
        resp-body (json/read-file "test/resources/es7-resp.json")]
    (is (cset/subset?
          (set [:timestamp :id :query-id :endpoint :service-time :es-time :config])
          (set (keys (get-in (replay/post-process
                               input-doc "endpoint" (json/encode-vanilla {:query {:match_all {}}})
                               0 resp-body {:foo "foo"})
                             [:_source :replay])))))))
