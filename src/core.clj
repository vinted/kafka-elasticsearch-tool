(ns core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [core.json :as json]
            [cli :as cli]
            [ops :as ops]
            [server :as server]
            [core.deep-merge :as dm])
  (:gen-class)
  (:import (org.slf4j LoggerFactory)
           (ch.qos.logback.classic Logger Level)))

(defn find-operation [operation-name cli-operations]
  (first (filter (fn [op] (= (name operation-name) (:name op))) cli-operations)))

(defn read-config-file [config-file]
  (if (and config-file (.exists (io/file config-file)))
    (json/read-file config-file)
    (do
      (when config-file
        (log/warnf "Config file '%s' does not exists" config-file))
      {})))

(defn execute-op [operation-name options cli-operations]
  (if operation-name
    (let [operation (find-operation operation-name cli-operations)
          resp (cond
                 (true? (:docs options)) (:docs operation)
                 (true? (:defaults options)) (:defaults operation))]
      (println
        (json/encode
          (if-let [msg (if (empty? resp)
                         ((:handler-fn operation) options)
                         resp)]
            msg
            (format "Operation '%s' is finished" (name operation-name))))))
    (log/warnf "Operation name was not provided")))

(defn handle-subcommand [{:keys [options] :as cli-opts} cli-operations]
  (try
    (if-let [operation (get options :operation)]
      (let [config-file (get options :config-file)
            file-options (read-config-file config-file)]
        (execute-op operation file-options cli-operations))
      (let [{{operation-name :name
              {:keys [options arguments summary errors]} :conf
              :as my-op} :operation} cli-opts]
        (if (seq errors)
          (println errors)
          (if (or (:help options) (empty? options))
            (println (format "Help for '%s':\n" (name operation-name)) summary)
            (let [configs-from-file (read-config-file (:config-file options))
                  combined-conf (dm/deep-merge configs-from-file options)]
              (execute-op operation-name combined-conf cli-operations))))))
    (catch Exception e
      (println (format "Failed to execute with exception:\n '%s'" e))
      (.printStackTrace e))))

(def additional-operations
  [{:name       "server"
    :handler-fn server/start
    :docs       (:doc (meta #'server/start))
    :defaults   server/default-http-server-config}])

(def cli-operations
  (concat ops/operations additional-operations))

(defn handle-cli [args]
  (let [{:keys [options summary errors arguments] :as cli-opts} (cli/recursive-parse args cli-operations)]
    (if errors
      (println errors)
      (if (or (get options :help) (and (empty? options) (empty? arguments)))
        (println summary)
        (handle-subcommand cli-opts cli-operations)))))

(comment
  (core/handle-cli ["-o" "foo" "-f" "a"])
  (core/handle-cli ["replay" "sink" "--connection.url=http://localhost:9200"])
  (core/handle-cli ["replay" "sink" "-h"])
  (core/handle-cli []))

(defn -main [& args]
  (when-let [logger-level (System/getenv "ROOT_LOGGER_LEVEL")]
    (.setLevel ^Logger
               (LoggerFactory/getLogger Logger/ROOT_LOGGER_NAME)
               (Level/valueOf (str logger-level))))
  (handle-cli args)
  (shutdown-agents))