(ns server.core
  (:require [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.dev.pretty :as pretty]
            [reitit.swagger :as swagger]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [server.ops-routes :as ops-routes]))

(def app
  (ring/ring-handler
    (ring/router
      [ops-routes/ops]
      {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
       ;;:validate spec/validate ;; enable spec validation for route data
       ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
       :exception pretty/exception
       :data      {:coercion   reitit.coercion.spec/coercion
                   :muuntaja   m/instance
                   :middleware [;; swagger feature
                                swagger/swagger-feature
                                ;; query-params & form-params
                                ;parameters/parameters-middleware
                                ;;; content-negotiation
                                ;muuntaja/format-negotiate-middleware
                                ;;; encoding response body
                                ;muuntaja/format-response-middleware
                                ;;; exception handling
                                ;exception/exception-middleware
                                ;;; decoding request body
                                ;muuntaja/format-request-middleware
                                ;;; coercing response bodys
                                ;coercion/coerce-response-middleware
                                ;; coercing request parameters
                                coercion/coerce-request-middleware]}})
    (ring/routes
      (ring/create-default-handler))))
