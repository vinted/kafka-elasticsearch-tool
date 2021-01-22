(ns core.ilm
  (:require [core.json :as json]
            [core.http :as http-client]
            [org.httpkit.client :as http]))

;TODO: set timeout and exponential backoff
(defn index-exists? [es-host index-name]
  @(http/request
     {:method :head
      :client @http-client/client
      :url    (format "%s/%s" es-host index-name)}
     (fn [resp] (not (= 404 (:status resp))))))

(defn set-refresh-interval! [dest-host dest-index interval-value]
  @(http/request
     {:method  :put
      :client  @http-client/client
      :url     (format "%s/%s/_settings" dest-host dest-index)
      :headers {"Content-Type" "application/json"}
      :body    (json/encode {"index.refresh_interval" interval-value})}
     (fn [resp] (json/decode (:body resp)))))

(defn create-index!
  ([dest-host dest-index] (create-index! dest-host dest-index {}))
  ([dest-host dest-index index-conf]
   @(http/request
      {:method  :put
       :client  @http-client/client
       :url     (format "%s/%s" dest-host dest-index)
       :headers {"Content-Type" "application/json"}
       :body    (json/encode index-conf)}
      (fn [resp] (json/decode (:body resp))))))

(defn refresh-index! [dest-host dest-index]
  @(http/request
     {:method  :get
      :client  @http-client/client
      :url     (format "%s/%s/_refresh" dest-host dest-index)
      :headers {"Content-Type" "application/json"}}
     (fn [resp] (json/decode (:body resp)))))

(defn delete-index! [dest-host dest-index]
  @(http/request
     {:method  :delete
      :client  @http-client/client
      :url     (format "%s/%s" dest-host dest-index)
      :headers {"Content-Type" "application/json"}}
     (fn [resp] (json/decode (:body resp)))))
