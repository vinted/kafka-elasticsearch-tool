(ns server.ops-routes
  (:require [core.json :as json]
            [ops :as my-ops]))

(defn operation->routes [{:keys [name docs defaults handler-fn]}]
  (let [path-root-handler {:summary (format "fetches a docstring of the %s operation" name)
                           :handler (fn [_]
                                      ; return available subopts
                                      {:status 200
                                       :body   (json/encode {:docs docs} {:pretty true})})}]
    [(format "/%s" name)
     ["" path-root-handler]
     ["/" path-root-handler]
     ["/execute"
      {:post {:summary   (format "execute %s operation" name)
              :responses {200 {:body {:total int?}}}
              :handler   (fn [req]
                           {:status 200
                            :body   (let [decoded-body (json/decode (:body req))]
                                      (handler-fn decoded-body))})}}]
     ["/docs"
      {:get {:summary (format "fetches a docstring of the %s operation" name)
             :handler (fn [_]
                        {:status  200
                         :headers {"Content-Type" "application/json"}
                         :body    (json/encode {:docs docs} {:pretty true})})}}]
     ["/source"
      {:get {:summary "fetches source of the operation"
             :handler (fn [_]
                        {:status  200
                         :headers {"Content-Type" "application/json"}
                         :body    (json/encode {:source nil} {:pretty true})})}}]
     ["/defaults"
      {:get {:summary (format "fetches default options of the %s operation" name)
             :handler (fn [_]
                        {:status  200
                         :headers {"Content-Type" "application/json"}
                         :body    (json/encode {:defaults defaults} {:pretty true})})}}]]))

(def ops
  (vec (concat ["/ops"]
               (map (fn [path]
                      [path {:summary "List of available operation"
                             :handler (fn [_]
                                        {:status  200
                                         :headers {"Content-Type" "application/json"}
                                         :body    (json/encode {:ops (map (fn [op] (:name op))
                                                                          my-ops/operations)})})}])
                    ["" "/"])
               (map (fn [operation] (operation->routes operation)) my-ops/operations))))
