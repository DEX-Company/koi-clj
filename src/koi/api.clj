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
   [koi.middleware :as mw])
  (:import [java.util UUID])
  (:gen-class))

(s/def ::operation string?)
(s/def ::jobid string?)
(s/def ::params map?)
(s/def ::payload (s/keys ::req-un [::operation params]))

(def routes
  (context "/api/v1" []
    :tags ["Invoke ocean service"]
    :coercion :spec

    ;;create a DID for each operation
    ;;create a schema for each operation
    (context "/invoke/:did" []
             :path-params [did :- string?]

      (sw/resource
       {:post
        {:summary "Run an sync operation"
         :parameters {:body ::params}
         :responses {200 {:schema spec/any?}
                     201 {:schema spec/any?}}
         :handler mw/invoke-handler}}))

    (context "/invokeasync/:did" []
             :path-params [did :- string?]
      (sw/resource
       {
        :post
        {:summary "Run an async operation"
         :parameters {:body ::params}
         :responses {200 {:schema spec/any?}
                     201 {:schema spec/any?}}
         :handler (partial mw/invoke-handler true)}}))

    (context "/jobs/:jobid" []
      :path-params [jobid :- int?]
      (sw/resource
       {:get
        {:summary "get the status of a job"
         :responses {200 {:schema spec/any?}
                     422 {:schema spec/any?}
                     500 {:schema spec/any?}}
         :handler mw/result-handler}}))))

(def app
  (do
    (mount/start-with-states {#'koi.middleware/service-registry
                              {:start mw/valid-assetid-svc-registry}})
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
