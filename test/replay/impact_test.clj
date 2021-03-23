(ns replay.impact-test
  (:require [clojure.test :refer :all]
            [replay.impact :as impact]))

(deftest index-name-extraction
  (let [uri "/index-name/_search?preference=7c5fe2d7-d313-4362-a62f-4c1e10e999fd"]
    (is (= "index-name" (impact/get-index-or-alias uri)))))

(deftest url-transformations
  (let [uri "/index-name/_search?preference=7c5fe2d7-d313-4362-a62f-4c1e10e999fd"]
    (is (= "/_search" (impact/prepare-endpoint uri))))
  (testing "msearch case"
    (let [uri "/_msearch?preference=7c5fe2d7-d313-4362-a62f-4c1e10e999fd"]
      (is (= "/_msearch" (impact/prepare-endpoint uri))))))

(deftest query-generation
  (let [query {:query {:match_all {}}}
        opts {:replay
              {:query-transforms [{:id     "jq-test"
                                   :lang   :jq
                                   :script ". as [$query, $value] | $query | .size = $value"
                                   :vals   [1 10]}]}}
        variations (impact/generate-queries opts query)]
    (is (= 2 (count variations)))
    (is (= '({:request   {:query {:match_all {}}
                          :size  1}
              :variation ({:id    "jq-test"
                           :value 1})}
             {:request   {:query {:match_all {}}
                          :size  10}
              :variation ({:id    "jq-test"
                           :value 10})}) variations))))

(deftest grouped-variations
  (let [k 5
        query {:query {:match_all {}}}
        opts {:replay
              {:query-transforms [{:id     "jq-test"
                                   :lang   :jq
                                   :script ". as [$query, $value] | $query | .size = $value"
                                   :vals   [1 10]}]}}
        grouped-variations (impact/get-grouped-query-variations query opts k)]
    (is (= 2 (count grouped-variations)))
    (is (= {"[{\"id\":\"jq-test\",\"value\":10}]" [{:request   {:query {:match_all {}}
                                                                :size  5}
                                                    :variation '({:id    "jq-test"
                                                                  :value 10})}]
            "[{\"id\":\"jq-test\",\"value\":1}]"  [{:request   {:query {:match_all {}}
                                                                :size  5}
                                                    :variation '({:id    "jq-test"
                                                                  :value 1})}]} grouped-variations))))

(deftest rank-eval-request-construction
  (let [k 10
        query {:query {:match_all {}}}
        opts {:replay
              {:query-transforms [{:id     "jq-test"
                                   :lang   :jq
                                   :script ". as [$query, $value] | $query | .size = $value"
                                   :vals   [1 10]}]}}
        ratings '({:_index "index-name", :_id 1, :rating 1}
                  {:_index "index-name", :_id 2, :rating 1}
                  {:_index "index-name", :_id 3, :rating 1}
                  {:_index "index-name", :_id 4, :rating 1}
                  {:_index "index-name", :_id 5, :rating 1})
        grouped-variations (impact/get-grouped-query-variations query opts k)
        metric {:precision {:k k :relevant_rating_threshold 1 :ignore_unlabeled false}}
        pit "pit"]
    (is (= {:metric   {:precision {:ignore_unlabeled          false
                                   :k                         10
                                   :relevant_rating_threshold 1}}
            :requests (list {:id      "[{\"id\":\"jq-test\",\"value\":1}]"
                             :ratings (list {:_id    1
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    2
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    3
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    4
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    5
                                             :_index "index-name"
                                             :rating 1})
                             :request {:pit   "pit"
                                       :query {:match_all {}}
                                       :size  10}}
                            {:id      "[{\"id\":\"jq-test\",\"value\":10}]"
                             :ratings (list {:_id    1
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    2
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    3
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    4
                                             :_index "index-name"
                                             :rating 1}
                                            {:_id    5
                                             :_index "index-name"
                                             :rating 1})
                             :request {:pit   "pit"
                                       :query {:match_all {}}
                                       :size  10}})}
           (impact/prepare-rank-eval-request ratings grouped-variations metric pit)))))
