(ns replay.transform.query
  (:require [polyglot.js :as js]
            [polyglot.sci :as sci]))

(defn compile-transform [{:keys [lang script]}]
  (case (keyword lang)
    :sci (sci/script->transform-fn script)
    :js (js/script->transform-fn script)
    (throw (Exception. (format "No such language supported: '%s'" (name lang))))))

(defn transform-fn [transforms]
  (let [tf-fn (apply comp (map compile-transform (reverse transforms)))]
    (fn [query] (tf-fn query))))

(comment
  (time
    (let [data "{}"
          tfs [{:lang   :js
                :script "(request) => request"}
               {:lang   :sci
                :script "(fn [q] (assoc q :_explain true))"}]
          tf (transform-fn tfs)]
      (dotimes [i 1000]
        (tf data)))))
