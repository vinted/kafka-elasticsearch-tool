(ns cli.operation
  (:require [clojure.string :as str]
            [clojure.tools.cli :as tools-cli]
            [core.maps :as maps]
            [server :as server]))

(defn non-scalar? [value] (map? value))

(defn value->parse-fn [value]
  (cond
    (number? value) [:parse-fn #(Integer/parseInt %)]
    (boolean? value) [:parse-fn #(Boolean/parseBoolean %)]
    (list? value) [:parse-fn #(str/split % #",")]
    (vector? value) [:parse-fn #(str/split % #",")]
    (map? value) []))

(defn value->default [value]
  (cond
    (map? value) []
    (list? value) []
    (vector? value) []
    :else [:default value]))

(defn remove-non-scalars [defaults]
  (into {} (remove (fn [[_ v]] (non-scalar? v)) defaults)))

(defn defaults->cli-opts [defaults]
  (mapv (fn [[k v]]
          (concat
            [nil
             (str "--" (name k) "=" (name k))
             ""]
            (value->parse-fn v)))
        (remove-non-scalars defaults)))

(comment
  (defaults->cli-opts {:sink {:remote {:host "fooo"}}}))

(defn operation-opts [defaults]
  (concat
    (defaults->cli-opts defaults)
    [[nil "--defaults" "Print to STDOUT the default configuration of the operation"]
     [nil "--docs" "Print to STDOUT the docstring of the operation"]
     ["-f" "--config-file CONFIG_FILE" "Path to the JSON file with operation config"]
     [nil "--override OVERRIDE" "JQ scripts to be applied on the config file"
      :multi true
      :update-fn conj
      :default []]
     [nil "--dry-run" "After construction of config instead of executing the operation prints config to stdout and exits."
      :default false]
     ["-h" "--help"]]))

(defn parse-opts [args defaults operation-name]
  (let [{:keys [arguments] :as parsed} (tools-cli/parse-opts args (operation-opts defaults)
                                                             :in-order true)]
    (loop [operation-args arguments
           parsed-opts parsed]
      (let [param-name (keyword (first operation-args))
            subdefaults (get defaults param-name)]
        (if (and (seq operation-args) (some? subdefaults))
          (let [cli-opts (operation-opts subdefaults)
                {subcommand-options :options
                 subcommand-summary :summary
                 subcommand-errors  :errors
                 unprocessed-args   :arguments} (tools-cli/parse-opts (rest operation-args)
                                                                      cli-opts
                                                                      :in-order true)]
            (recur unprocessed-args
                   (-> parsed-opts
                       (update :options assoc param-name (maps/remove-nil-vals subcommand-options))
                       (update :options (fn [options] (if (:help subcommand-options)
                                                        (assoc options :help true)
                                                        options)))
                       (update :summary (fn [summary] (format "%s\n%s:\n%s" summary (name param-name)
                                                              subcommand-summary)))
                       (update :errors concat subcommand-errors))))
          (if (or (nil? param-name) (some? subdefaults))
            parsed-opts
            (update parsed-opts :errors conj
                    (format "Configuration param '%s' for operation '%s' is not known"
                            param-name operation-name))))))))

(comment
  (cli.operation/parse-opts ["--port" "8080" "-h"]
                            server/default-http-server-config
                            :server))
