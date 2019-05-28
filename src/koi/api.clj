(ns koi.api
  (:require 
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [ring.adapter.jetty :refer [run-jetty]]
   [compojure.api.sweet :as sw :refer [api context
                                       undocumented
                                       GET PUT POST DELETE]]
   [compojure.api.coercion.spec :as spec-coercion]
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
   [mount.core :as mount :refer [defstate]]
   [clojure.java.io :as io]
   [koi.middleware.basic-auth :refer [basic-auth-mw]]
   [koi.middleware.authenticated :refer [authenticated-mw]]
   [koi.middleware.token-auth :refer [token-auth-mw]]
   [koi.route-functions.auth.get-auth-credentials :refer [auth-credentials-response]]
   [koi.op-handler :as oph])
  (:import [java.util UUID])
  (:gen-class))

(s/def ::operation string?)
(s/def ::jobid string?)
(s/def ::params map?)
(s/def ::payload (s/keys ::req-un [::operation params]))
(s/def ::auth-header string?)
(s/def ::auth-response string?)

(def routes
  (context "/api/v1" []
           :tags ["Invoke ocean service"]
           :coercion :spec

           (context "/auth" []

                    (GET "/" {:as request}
                         :tags ["Auth"]
                         :return ::auth-response
                         :header-params [authorization :- ::auth-header]
                         :middleware [basic-auth-mw authenticated-mw]
                         :summary "Returns auth info given a username and password in the '`Authorization`' header."
                         :description "Authorization header expects '`Basic username:password`' where `username:password`
                         is base64 encoded. To adhere to basic auth standards we have to use a field called
                         `username` however we will accept a valid username or email as a value for this key."
                         (auth-credentials-response request)))


           ;;create a DID for each operation
           ;;create a schema for each operation
           (context "/invoke/:did" []
                    :path-params [did :- string?]
                    (sw/resource
                     {:post
                      {:summary "Run an sync operation"
                       :parameters {:body ::params}
                       :middleware [token-auth-mw authenticated-mw]
                       :responses {200 {:schema spec/any?}
                                   201 {:schema spec/any?}}
                       :handler oph/invoke-handler}}))

           (context "/invokeasync/:did" []
                    :path-params [did :- string?]
                    (sw/resource
                     {
                      :post
                      {:summary "Run an async operation"
                       :middleware [token-auth-mw authenticated-mw]
                       :parameters {:body ::params}
                       :responses {200 {:schema spec/any?}
                                   201 {:schema spec/any?}}
                       :handler (partial oph/invoke-handler true)}}))

           (context "/jobs/:jobid" []
                    :path-params [jobid :- int?]
                    (sw/resource
                     {:get
                      {:summary "get the status of a job"
                       :middleware [token-auth-mw authenticated-mw]
                       :responses {200 {:schema spec/any?}
                                   422 {:schema spec/any?}
                                   500 {:schema spec/any?}}
                       :handler oph/result-handler}}))))

(def app
  (do
    (mount/start-with-states {#'koi.op-handler/service-registry
                              {:start oph/valid-assetid-svc-registry}})
    (api
     {:swagger
      {:ui "/"
       :spec "/swagger1.json"
       :data {:info {:title "invoke-api "
                     :description "Invoke with Ocean "}
              :tags [{:name "invoke service", :description "invoke Ocean services"}]}}}
     routes)))

(defn -main [& args]
  (run-jetty app {:port (Integer/valueOf (or (System/getenv "port") "3000"))}))
