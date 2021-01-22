(ns replay.transform.uri-test
  (:require [clojure.test :refer :all]
            [replay.transform.uri :as transform-uri]))

(deftest uri-transforms
  (testing "simple string replacement"
    (let [uri "/my_search_index/_search?q=elasticsearch"
          transforms [{:match "my_search_index" :replacement "test"}]]
      (is (= "/test/_search?q=elasticsearch"
             (transform-uri/transform-uri uri transforms)))))

  (testing "_count replacement with _search size=0"
    (let [uri "/my_search_index/_count?q=elasticsearch"
          transforms [{:match "_count\\?" :replacement "_search?size=0&"}]]
      (is (= "/my_search_index/_search?size=0&q=elasticsearch"
             (transform-uri/transform-uri uri transforms))))))

(deftest endpoint-construction
  (testing "uri is provided id replay conf"
    (let [doc {}
          replay-conf {:uri "foo"}]
      (is (= "foo" (transform-uri/construct-endpoint doc replay-conf)))))
  (testing "uri.attr specified"
    (let [doc {:foo "_search"}
          replay-conf {:uri_attr "foo"}]
      (is (= "_search" (transform-uri/construct-endpoint doc replay-conf)))))
  (testing "uri.attr specified with transforms"
    (let [doc {:foo "/foo/_count?q=elastic"}
          replay-conf {:uri_attr   "foo"
                       :uri-transforms [{:match "_count\\?" :replacement "_search?size=0&"}]}]
      (is (= "/foo/_search?size=0&q=elastic" (transform-uri/construct-endpoint doc replay-conf))))))
