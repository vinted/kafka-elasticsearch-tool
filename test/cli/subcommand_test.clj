(ns cli.subcommand-test
  (:require [clojure.test :refer :all]
            [cli :as cli]))

(def test-ops
  [{:name       "server"
    :handler-fn identity
    :docs       "Sample docs"
    :defaults   {:ip       "0.0.0.0"
                 :port     8090
                 :max-body Integer/MAX_VALUE}}])

(deftest subcommand-strategy-parsing
  (testing "if subcommand is detected"
    (let [args ["server" "--port" "8090"]
          {:keys [errors operation]} (cli/recursive-parse args test-ops)]
      (is (nil? errors))
      (is (= :server (get-in operation [:name])))
      (is (= {:port 8090}
             (get-in operation [:conf :options]))))))

(deftest pre-subcommand-parsing
  (testing "if subcommand is detected"
    (let [args ["-o" "foo" "-f" "the-file"]
          {:keys [errors operation options]} (cli/recursive-parse args test-ops)]
      (is (nil? operation))
      (is (nil? errors))
      (is (= (set [:config-file :operation])
             (set (keys options)))))))

(def subcommand-with-ops
  [{:name       "foo"
    :handler-fn identity
    :docs       "Sample docs"
    :defaults   {:max-docs 1
                 :source {:topic "my-topic"
                          :partitions [1 2 3]}}}])

(deftest subcommand-parsing-with-source
  (testing "if subcommand is detected"
    (let [args ["foo" "--max-docs=42" "source" "--topic=test-topic" "--partitions=1,2,3,4"]
          {:keys [errors operation options]}
          (cli/recursive-parse args subcommand-with-ops)]
      (is (nil? errors))
      (is (empty? (set (keys options))))
      (is (= :foo (:name operation)))
      (is (= {:max-docs 42 :source {:topic "test-topic"
                                    :partitions ["1" "2" "3" "4"]}}
             (:options (:conf operation))))
      (is (empty? (:errors (:conf operation))))))

  (testing "if subcommand is detected with undefined variables"
    (let [args ["foo" "--max-docs" "42" "source" "--foo" "bar"]
          {:keys [errors operation]} (cli/recursive-parse args subcommand-with-ops)]
      (is (nil? errors))
      (is (= :foo (:name operation)))
      (is (not (empty? (:errors (:conf operation)))))))

  (testing "if subcommand is with help flag"
    (let [args ["foo" "--max-docs" "42" "source" "--help"]
          {:keys [errors operation] :as o} (cli/recursive-parse args subcommand-with-ops)]
      (is (nil? errors))
      (is (= :foo (:name operation)))
      (is (empty? (:errors (:conf operation)))))))
