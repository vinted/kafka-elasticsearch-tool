(ns cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :as clojure.cli]
            [cli.operation :as operation]
            [core.maps :as maps]))

(defn op-names [operations]
  (str "["
       (str/join ", " (map (fn [op] (str (:name op))) operations))
       "]"))

(defn cli-options [operations]
  [["-o" "--operation OPERATION" (format "A name of a supported operation. One of: %s"
                                         (op-names operations))
    :parse-fn #(keyword %)]
   [nil "--defaults" "Print to STDOUT the default configuration of the operation"
    :default nil]
   [nil "--docs" "Print to STDOUT the docstring of the operation"
    :default nil]
   ["-f" "--config-file CONFIG_FILE" "Path to the JSON file with operation config"]
   [nil "--override OVERRIDE" "JQ scripts to be applied on the config file"
    :multi true
    :update-fn conj
    :default []]
   [nil "--dry-run" "After construction of config instead of executing the operation prints config to stdout and exits."
    :default false]
   ["-h" "--help"]])

(comment
  (cli/recursive-parse ["-f" "examples/replay.json" "--override"  ".foo = 12" "--override" ".max_docs |= 13"] core/cli-operations))

(defn find-operation [operation-name operations]
  (first (filter (fn [op] (= (name operation-name) (:name op))) operations)))

(defn recursive-parse
  [args commands]
  (let [{:keys [arguments] :as o} (clojure.cli/parse-opts args (cli-options commands)
                                                          :in-order true)]
    (update (if (seq arguments)
              (let [[subcommand & args] arguments
                    command (find-operation subcommand commands)]
                (if command
                  (assoc o :operation {:name (keyword subcommand)
                                       :conf (-> (operation/parse-opts args (:defaults command) subcommand)
                                                 (update :options maps/remove-nil-vals))})
                  (assoc o :operation {:errors [(format "Subcommand '%s' does not exist" subcommand)]})))
              o)
            :options maps/remove-nil-vals)))

(comment
  (cli/recursive-parse ["server" "--port" "8080"] core/cli-operations)

  (cli/recursive-parse ["replay" "sink" "--help"] core/cli-operations)

  (cli/recursive-parse ["reindex" "source" "remote" "--host=foo"] core/cli-operations))
