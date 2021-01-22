(ns core.http
  (:require [org.httpkit.client :as http])
  (:import (javax.net.ssl SSLParameters SSLEngine SNIHostName)
           (java.net URI)))

(defn sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setSSLParameters ssl-engine ssl-params)))

(def client (delay (http/make-client {:ssl-configurer sni-configure})))
