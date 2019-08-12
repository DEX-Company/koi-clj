(ns koi.api
  (:require 
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [ring.adapter.jetty :refer [run-jetty]]
   [compojure.api.sweet :as sw :refer [api context
                                       undocumented
                                       GET PUT POST DELETE]]
   [compojure.api.coercion.spec :as spec-coercion]
   [com.stuartsierra.component :as component]
   [ring.middleware.cors :refer [wrap-cors]]
   [compojure.api.coercion.schema :as cos]
   [compojure.route :as route]
   [clojure.java.io :as io]
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [ring.util.http-response :refer [ok header created]]
   [ring.util.http-status :as status]
   [muuntaja.format.json :as json-format]
   [spec-tools.spec :as spec]
   [clojure.spec.alpha :as s]
   [spec-tools.data-spec :as ds]
   [spec-tools.json-schema :as jsc]
   [schema-tools.core :as st]
   [clojure.java.io :as io]
   [koi.middleware.basic-auth :refer [basic-auth-mw]]
   [koi.middleware.authenticated :refer [authenticated-mw]]
   [koi.middleware.token-auth :refer [token-auth-mw]]
   [koi.route-functions.auth.get-auth-credentials :refer [auth-credentials-response]]
   [koi.config :as config :refer [get-config get-remote-agent]]
   [koi.op-handler :as oph])
  (:import [java.util UUID])
  (:gen-class))

(s/def ::operation string?)
(s/def ::jobid string?)
(s/def ::params map?)
(s/def ::payload (s/keys ::req-un [::operation params]))
(s/def ::auth-header string?)
(s/def ::auth-response string?)

(defn koi-routes
  [operation-registry]
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger1.json"
     :data {:info {:title "invoke-api "
                   :description "Invoke with Ocean "}
            :tags [{:name "invoke service", :description "invoke Ocean services"}]}}}

   (context "/api/v1" []
     :tags ["Invoke ocean service"]
     :coercion :spec

     (context "/auth" []

       (POST "/token" {:as request}
         :tags ["Auth"]
         :return ::auth-response
         :header-params [authorization :- ::auth-header]
         :middleware [basic-auth-mw authenticated-mw]
         :summary "Returns auth info given a username and password in the '`Authorization`' header."
         :description "Authorization header expects '`Basic username:password`' where `username:password`
                         is base64 encoded. To adhere to basic auth standards we have to use a field called
                         `username` however we will accept a valid username or email as a value for this key."
         (auth-credentials-response request)))

     (context "/invoke/:did" []
       :path-params [did :- string?]
       :middleware [token-auth-mw authenticated-mw]
       (sw/resource
        {:post
         {:summary "Run an sync operation"
          :parameters {:body ::params}
          :responses {200 {:schema spec/any?}
                      201 {:schema spec/any?}
                      404 {:schema spec/any?}
                      500 {:schema spec/any?}
                      }
          :handler (partial oph/invoke-handler operation-registry)}}))

     (context "/invokeasync/:did" []
       :path-params [did :- string?]
       :middleware [token-auth-mw authenticated-mw]
       (sw/resource
        {
         :post
         {:summary "Run an async operation"
          :parameters {:body ::params}
          :responses {200 {:schema spec/any?}
                      201 {:schema spec/any?}
                      404 {:schema spec/any?}
                      500 {:schema spec/any?}}
          :handler (partial oph/invoke-handler operation-registry true)}}))

     (context "/jobs/:jobid" []
       :path-params [jobid :- int?]
       :middleware [token-auth-mw authenticated-mw]
       (sw/resource
        {:get
         {:summary "get the status of a job"
          :responses {200 {:schema spec/any?}
                      422 {:schema spec/any?}
                      404 {:schema spec/any?}
                      500 {:schema spec/any?}}
          :handler oph/result-handler}})))))

#_(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger1.json"
     :data {:info {:title "invoke-api "
                   :description "Invoke with Ocean "}
            :tags [{:name "invoke service", :description "invoke Ocean services"}]}}}
   routes))

(defrecord WebServer [port operation-registry]
  component/Lifecycle
  (start [this]
    (println " start jetty at " port " oper " operation-registry " - 90 " (:operation-registry operation-registry))
    (assoc this :http-server
           (run-jetty
            (koi-routes (:operation-registry operation-registry))
            {:port (Integer/valueOf (or (System/getenv "port") port))})))
  (stop [this]
    (println " no-op stopping jetty")
    this))

(defn new-webserver
  [port]
  (map->WebServer {:port port})
  )

#_(defn app-init
  ([]
   (app-init (get-config)))
  ([config]
   (info " mount starting with " config)
   #_(mount/start-with-states {#'koi.op-handler/registry
                             {:start #(oph/operation-registry config)}})))

#_(defn -main [& args]
  (do 
    (app-init)
    (run-jetty app {:port (Integer/valueOf (or (System/getenv "port") "3000"))})))

(defn default-system
  [config]
  (let [{:keys [port]} config]
    (component/system-map
     :config-options config
     :operation-registry (oph/new-operation-registry config)
     :app (component/using
           (new-webserver port)
           {:operation-registry :operation-registry}))))

(defn -main [& args]
  (component/start (default-system (aero.core/read-config (clojure.java.io/resource "config.edn")))))
