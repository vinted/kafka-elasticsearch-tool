(ns polyglot
  (:require [clojure.string :as s]
            [clojure.data :as data]
            [clojure.java.io :as io]
            [core.json :as json]
            [polyglot.js :as js]
            [polyglot.jq :as jq]
            [polyglot.sci :as sci])
  (:import (java.io File)))

(defn apply-js-transformation
  "Given some JSON encoded data and a JS code snippet, GraalVM is going to interpret the JS code
  and apply it to the JSON encoded data.

  Input: {:data \"{\"my\": \"data\"}\" :script (s) => s\"}"
  [{:keys [data script]}]
  (js/string->string data script))

(defn apply-sci-transformation
  "Given some JSON encoded data and a SCI code snippet, GraalVM is going to interpret the SCI code
  and apply it to the JSON encoded data.

  Input: {:data \"{\"my\": \"data\"}\" :script \"(fn [m] (assoc m :foo :bar))\"}"
  [{:keys [data script]}]
  (sci/string->string data script))

(comment
  (polyglot/apply-sci-transformation
    {:data "{\"my\": \"data\"}"
     :script "(fn [m] (assoc m :foo :bar))"}))

(defn apply-jq-transformation [{:keys [data script]}]
  (let [script (jq/script->transform-fn script)]
    (script data)))

(comment
  (polyglot/apply-jq-transformation
    {:data   "{\"my\": \"data\"}"
     :script ".foo = \"bar\""}))

(defn compare-with-expected [^String expected ^String actual]
  (let [e (json/decode expected)
        a (json/decode actual)
        [only-in-expected only-in-actual in-both] (data/diff e a)]
    {:matches          (and (nil? only-in-expected) (nil? only-in-actual))
     :only_in_expected (json/encode only-in-expected)
     :only_in_result   (json/encode only-in-actual)
     :in_both          (json/encode in-both)}))

(defn apply-transformation
  "Given a JSON encoded data (1) and a code snippet (2) of one of the
  supported scripting languages, GraalVM will apply (2) on (1). The output
  of the operation will be appended under the :result key as JSON string.

  If :expected JSON string is provided then the :result will be compared with :expected
  with value semantics. The output of the comparison will be stored under :match key.
  When :expected is equal to :result then :match will have a property :matches' set to true.
  When :expected is NOT equal to :result then under :match will contain the details of what didn't match.

  Input: {:lang \"js\" :data \"{\"my\": \"data\"}\" :script \"(s) => s\"}"
  [{:keys [lang file script expected] :as req}]
  (let [result (if (and (nil? script) (nil? file))
                 {:error "Neither script nor file are provided"}
                 (if (and (nil? script) (not (.exists ^File (io/file file))))
                   {:error (format "Provided file '%s' doesn't exist" file)}
                   (let [lang-code (if (string? lang)
                                     (-> lang (s/trim) (s/lower-case) keyword)
                                     lang)
                         req (cond-> req
                                     (and (nil? script) (.exists ^File (io/file file))) (assoc :script (slurp file)))]
                     (case lang-code
                       :js (apply-js-transformation req)
                       :jq (apply-jq-transformation req)
                       :sci (apply-sci-transformation req)
                       {:error (format "Language '%s' is not supported." lang)}))))
        matches-expected? (when  expected
                            (compare-with-expected expected result))]
    (assoc req :result result
               :match matches-expected?)))

(def polyglot-defaults
  {:data     "{\"my\": \"data\"}"
   :lang     "js"
   :script   "(s) => s"
   :file     nil
   :expected nil})

(comment
  (polyglot/apply-transformation polyglot-defaults)

  (polyglot/apply-transformation
    (assoc polyglot-defaults :expected "{\"my\": \"data\"}")))

(defn map->map [m fsc]
  (json/decode (js/string->string (json/encode m) fsc)))

(defn append-timestamp-to-map
  "Map keys must be strings."
  [m]
  (js/transform-map-js m "(m) => {var target = Object.assign({}, m);target['timestamp'] = new Date().toISOString(); return target;}"))

(comment
  (append-timestamp-to-map {"graalvm and clojure" "rocks!"})
  ;=> {"graalvm and clojure" "rocks!", "timestamp" "2020-06-14T22:34:12.768Z"}
  ;;Native image compiler can compile these commands 137 OOM
  ;:polyglot (polyglot/append-timestamp-to-map options)
  )
