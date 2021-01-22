(ns polyglot
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [core.json :as json]
            [polyglot.js :as js]
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

(defn apply-transformation
  "Given some JSON encoded data and a JS code snippet, GraalVM is going to interpret the JS code
  and apply it to the JSON encoded data.
  Also, script can be provided with a reference to a file.

  Input: {:lang \"js\" :data \"{\"my\": \"data\"}\" :script \"(s) => s\"}"
  [{:keys [lang file script] :as req}]
  (assoc req :result
             (if (and (nil? script) (nil? file))
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
                     :sci (apply-sci-transformation req)
                     {:error (format "Language '%s' is not supported." lang)}))))))

(def polyglot-defaults
  {:data   "{\"my\": \"data\"}"
   :lang   "js"
   :script "(s) => s"
   :file   "/path/to/file"})

(comment
  (polyglot/apply-transformation polyglot-defaults))

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
