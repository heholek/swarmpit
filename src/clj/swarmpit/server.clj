(ns swarmpit.server
  (:gen-class)
  (:import (clojure.lang ExceptionInfo))
  (:require [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [swarmpit.authentication :refer [wrap-authentication]]
            [swarmpit.authorization :refer [wrap-authorization]]
            ;[swarmpit.handler :as handler :refer :all]
            [swarmpit.event.handler :refer :all]
            [org.httpkit.server :refer [run-server]]
            ;[bidi.ring :refer [make-handler]]
            ;[bidi.bidi :refer [match-pair match-route*]]
            [clojure.tools.logging :as log]
            [swarmpit.routes :as routes]
            [swarmpit.setup :as setup]
            [swarmpit.database :as db]
            [swarmpit.agent :as agent]


            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]

            ))

(defn wrap-client-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (dissoc (ex-data e) :headers)))))

(defn wrap-fallback-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :body   (Throwable->map e)}))))

;(defn wrap-path-params
;  [handler]
;  (fn [{:keys [uri] :as request}]
;    (let [{:keys [route-params]} (match-route* routes/backend uri request)]
;      (handler
;        (assoc request :path-params route-params)))))

;(def app
;  (-> (make-handler routes/backend handler/dispatch)
;      (wrap-resource "public")
;      wrap-authorization
;      wrap-client-exception
;      wrap-fallback-exception
;      wrap-authentication
;      wrap-path-params
;      wrap-json-params
;      wrap-json-response
;      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
;      wrap-gzip))

(def app
  (ring/ring-handler
    (ring/router
      routes/table
      {:exception pretty/exception
       :data {:coercion reitit.coercion.spec/coercion
              :muuntaja m/instance
              :middleware [;; negotiation, request decoding and response encoding
                           muuntaja/format-middleware
                           ;; swagger feature
                           swagger/swagger-feature
                           ;; query-params & form-params
                           parameters/parameters-middleware
                           ;; exception handling
                           exception/exception-middleware
                           ;; coercing response bodys
                           coercion/coerce-response-middleware
                           ;; coercing request parameters
                           coercion/coerce-request-middleware]}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))))

(defn -main [& [port]]
  (log/info "Swarmpit is starting...")
  (db/init)
  (let [port (or port 8080)]
    (run-server app {:port port} :thread 8 :queue-size 300000)
    (log/info "Swarmpit running on port" port))
  (agent/init)
  (setup/docker))
