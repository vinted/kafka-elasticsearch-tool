(ns core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [core.json :as json]
            [cli :as cli]
            [ops :as ops]
            [ops-overrides :as ops-overrides]
            [core.deep-merge :as dm]
            [jq.core :as jq])
  (:gen-class)
  (:import (org.slf4j LoggerFactory)
           (ch.qos.logback.classic Logger Level)))

(defn find-operation [operation-name cli-operations]
  (first (filter (fn [op] (= (name operation-name) (:name op))) cli-operations)))

(defn read-config-file [config-file jq-overrides]
  (let [combined-jq-overrides (str/join " | " jq-overrides)]
    (if (and config-file (.exists (io/file config-file)))
      (if (seq jq-overrides)
        (let [^String file-contents (slurp config-file)]
          (json/decode (jq/execute file-contents combined-jq-overrides)))
        (json/read-file config-file))
      (do
        (if config-file
          (throw (Exception. (format "Config file '%s' does not exists" config-file)))
          (json/decode (if (seq jq-overrides)
                         (jq/execute "{}" combined-jq-overrides)
                         "{}")))))))

(defn execute-op [operation-name options cli-operations]
  (if operation-name
    (let [operation (find-operation operation-name cli-operations)
          resp (cond
                 (true? (:docs options)) (:docs operation)
                 (true? (:defaults options)) (:defaults operation))]
      (println
        (json/encode
          (if-let [msg (if (empty? resp)
                         (if (empty? options)
                           (throw (Exception. (format "Configuration for the operation '%s' is bad: '%s'"
                                                      (name operation-name) options)))
                           ((:handler-fn operation) options))
                         resp)]
            msg
            (format "Operation '%s' is finished" (name operation-name))))))
    (throw (Exception. "Operation name was not provided"))))

(defn handle-subcommand [{:keys [options] :as cli-opts} cli-operations]
  (try
    (if-let [operation (get options :operation)]
      (let [dry-run? (:dry-run options)
            config-file (get options :config-file)
            file-options (read-config-file config-file (:override options))]
        (if dry-run?
          (println (json/encode file-options))
          (execute-op operation file-options cli-operations)))
      (let [dry-run? (:dry-run options)
            {{operation-name :name
              {:keys [options arguments summary errors]} :conf
              :as my-op} :operation} cli-opts]
        (if (seq errors)
          (println errors)
          (if (and (or (:help options) (empty? options)) operation-name)
            (println (format "Help for '%s':\n" (name operation-name)) summary)
            (let [configs-from-file (read-config-file (:config-file options) (:override options))
                  combined-conf (dm/deep-merge configs-from-file (dissoc options :override :config-file :dry-run))]
              (if (or dry-run? (:dry-run options))
                (println (json/encode (dissoc combined-conf :defaults :docs)))
                (execute-op operation-name combined-conf cli-operations)))))))
    (catch Exception e
      (log/errorf "Failed to execute with exception: '%s'" e)
      (when (System/getenv "DEBUG_MODE")
        (.printStackTrace e))
      (System/exit 1))))

(def cli-operations
  (concat ops/operations ops-overrides/cli))

(defn handle-cli [args]
  (let [{:keys [options summary errors arguments] :as cli-opts} (cli/recursive-parse args cli-operations)]
    (if errors
      (println errors)
      (if (or (get options :help)
              (and (empty? options) (empty? arguments))
              (empty? args))
        (println summary)
        (handle-subcommand cli-opts cli-operations)))))

(comment
  (core/handle-cli ["--dry-run" "reindex" "-f" "config-file.json" "--override"  ".foo = 12" "--override" ".max_docs |= 13"])
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
