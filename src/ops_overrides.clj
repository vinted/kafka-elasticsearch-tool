(ns ops-overrides
  (:require [clojure.string :as str]
            [core.maps :as maps]
            [polyglot :as polyglot]
            [server :as server]
            [clojure.java.io :as io]
            [ops.es-to-stdout :as es-to-stdout]
            [ops.kafka-to-stdout :as kafka-to-stdout]
            [ops.stdin-to-es :as std-to-es])
  (:import (java.io File)))


(def test-script-defaults
  (merge (dissoc polyglot/polyglot-defaults :file)
         {:data.file nil :script.file nil :expected.file nil}))

(defn from-file? [req attr-key attr-file-key]
  (and (nil? (attr-key req)) (string? (attr-file-key req))))

(defn override-with-file [req attr-key attr-file-key]
  (let [file-path (get req attr-file-key)
        file-content-or-error (if (.exists ^File (io/file file-path))
                                (slurp file-path)
                                {:error (format "File '%s' specified with a flag '%s' doesn't exist!"
                                                file-path (str "--" (name attr-file-key)))})]
    (if (string? file-content-or-error)
      (assoc req attr-key file-content-or-error)
      (assoc req :result file-content-or-error))))

(defn test-script-handler
  "When :data is nil then :file is checked.
  When :expected is nil then :expected_file is checked."
  [req]
  (maps/remove-nil-vals
    (let [updated-request (cond-> req
                                  (from-file? req :data :data.file) (override-with-file :data :data.file)
                                  (from-file? req :script :script.file) (override-with-file :script :script.file)
                                  (from-file? req :expected :expected.file) (override-with-file :expected :expected.file))]
      (if (:result updated-request)
        updated-request
        (polyglot/apply-transformation updated-request)))))

(def cli
  [{:name       "server"
    :handler-fn server/start
    :docs       (:doc (meta #'server/start))
    :defaults   server/default-http-server-config}
   {:name       "test-script"                               ;; TODO: implement ops merging
    :handler-fn test-script-handler
    :docs       (str/join "\n"
                          ["ALPHA."
                           (:doc (meta #'polyglot/apply-transformation))
                           (:doc (meta #'test-script-handler))])
    :defaults   test-script-defaults}
   {:name       "kafka-to-stdout"
    :handler-fn kafka-to-stdout/execute
    :docs       (:doc (meta #'kafka-to-stdout/execute))
    :defaults   kafka-to-stdout/default-opts}
   {:name       "elasticsearch-to-stdout"
    :handler-fn es-to-stdout/execute
    :docs       (:doc (meta #'es-to-stdout/execute))
    :defaults   es-to-stdout/default-opts}
   {:name       "stdin-to-elasticsearch"
    :handler-fn std-to-es/execute
    :docs       (:doc (meta #'std-to-es/execute))
    :defaults   std-to-es/default-opts}])
