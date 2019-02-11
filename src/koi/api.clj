(ns koi.api
  (:require 
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
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
   [clojure.spec.alpha :as sp]
   [spec-tools.data-spec :as ds]
   [spec-tools.json-schema :as jsc]
   [schema-tools.core :as st]
   [mount.core :as mount :refer [defstate]]
   [invoke-spec.protocols :as prot :refer [invoke-sync invoke-async get-result get-status get-metadata]]
   ;[scoringsvc.zepl-docker :as zd :refer [zepl-component]]
   [clojure.java.io :as io]
   [koi.middleware :as mw]
   [invoke-spec.asset :as oas])
  (:import [java.util UUID]))

(def routes
  (context "/invokesvc" []
           :tags ["invoke ocean service"]
           :coercion :spec

           (context "/metadata" []
                    (sw/resource
                     {:get
                      {:summary "returns the metadata for calling invoke"
                       :responses {200 {:schema spec/any?}}
                       :handler (fn [_]
                                  (ok mw/svc-metadata))}}))

           (context "/invoke" []
                    (sw/resource
                     {:post
                      {:summary "invoke the service  "
                       :parameters {:body spec/any?}
                       :responses {200 {:schema spec/any?}
                                   422 {:schema spec/any?}}
                       :handler mw/invoke-handler}}))))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger1.json"
     :data {:info {:title "invoke-api "
                   :description "Demonstrating Invoke with Ocean "}
            :tags [{:name "invoke service", :description "invoke multiple Ocean services"}]}}}
   routes))

;;this isn't the right place to put it, just a stopgap
;(mount/start)
;(mount/stop)
