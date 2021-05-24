(ns source.stdin-test
  (:require [clojure.test :refer :all]
            [jsonista.core :as json]
            [source.stdin :as stdin]
            [clojure.string :as str]))

(deftest reading-from-stdin
  (with-in-str "" (is (= 0 (count (stdin/fetch {})))))
  (let [str-line (json/write-value-as-string {:foo "bar"})]
    (with-in-str
      str-line
      (let [rez (stdin/fetch {})]
        (is (= 1 (count rez)))
        (is (= [{:foo "bar"}] rez)))))
  (testing "max_docs param handling"
    (let [str-line (str/join "\n" [(json/write-value-as-string {:foo "bar1"})
                                   (json/write-value-as-string {:foo "bar2"})])]
      (with-in-str
        str-line
        (let [rez (stdin/fetch {})]
          (is (= 2 (count rez)))
          (is (= [{:foo "bar1"}
                  {:foo "bar2"}] rez))))
      (with-in-str
        str-line
        (let [rez (stdin/fetch {:max_docs 1})]
          (is (= 1 (count rez)))
          (is (= [{:foo "bar1"}] rez))))))

  (testing ":decode-value? parameter"
    (let [str-line (str/join "\n" [(json/write-value-as-string {:foo "bar"})])]
      (with-in-str
        str-line
        (let [rez (stdin/fetch {})]
          (is (= 1 (count rez)))
          (is (= [{:foo "bar"}] rez))))
      (with-in-str
        str-line
        (let [rez (stdin/fetch {:source {:decode-value? false}})]
          (is (= 1 (count rez)))
          (is (= [(json/write-value-as-string {:foo "bar"})] rez)))))))
