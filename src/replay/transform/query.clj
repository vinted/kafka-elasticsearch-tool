(ns replay.transform.query
  (:require [polyglot.js :as js]
            [polyglot.jq :as jq]
            [polyglot.sci :as sci]))

(defn compile-transform [{:keys [lang script]}]
  (case (keyword lang)
    :sci (sci/script->transform-fn script)
    :js (js/script->transform-fn script)
    :jq (jq/script->transform-fn script)
    (throw (Exception. (format "No such language supported: '%s'" (name lang))))))

;; TODO: optimize consecutive JQ scripts to be executed in one pass
(defn transform-fn [transforms]
  (let [tf-fn (apply comp (map compile-transform (reverse transforms)))]
    (fn [^String query] (tf-fn query))))

(comment
  ;; Applies transforms on the input string in order
  (let [data "{}"
        tfs [{:lang   :js
              :script "(request) => { request['_source'] = false; return request; }"}
             {:lang   :sci
              :script "(fn [q] (assoc q :_explain true))"}
             {:lang   :jq
              :script ".size = 15"}]
        tf (transform-fn tfs)]
    (tf data))
  ;; => {"_explain":true,"_source":false,"size":15}

  ;; JQ scripts are the fastest option to transform
  (let [data "{\"a\": 12}"
        tfs [{:lang   :jq
              :script ".foo |= \"bar\""}]
        tf (transform-fn tfs)]
    (tf data))
  ;; => {"a":12,"foo":"bar"}
  )
