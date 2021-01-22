(ns server
  (:require [clojure.tools.logging :as log]
            [org.httpkit.server :refer [run-server]]
            [server.core :refer [app]]))

(def default-http-server-config
  {:ip       "0.0.0.0"
   :port     8090
   :max-body Integer/MAX_VALUE})

(defn start
  "Start the HTTP server with provided settings."
  [config]
  (let [http-server-config (merge default-http-server-config config)]
    (log/infof "Starting an HTTP server with config: '%s'" http-server-config)
    (run-server #'app http-server-config)))

(defn -main []
  (log/infof "Starting the server")
  (start {}))
