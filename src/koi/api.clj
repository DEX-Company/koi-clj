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
   [clojure.spec.alpha :as s]
   [spec-tools.data-spec :as ds]
   [spec-tools.json-schema :as jsc]
   [schema-tools.core :as st]
   [mount.core :as mount :refer [defstate]]
   [invoke-spec.protocols :as prot :refer [invoke-sync invoke-async get-result get-status get-metadata]]
   [clojure.java.io :as io]
   [koi.middleware :as mw]
   [invoke-spec.asset :as oas])
  (:import [java.util UUID]))

(s/def ::operation string?)
(s/def ::params map?)
(s/def ::payload (s/keys ::req-un [::operation params]))

(def routes
  (context "/" []
           :tags ["invoke ocean service"]
           :coercion :spec

           (context "/invokesync" []
                    (sw/resource
                     {:post
                      {:summary "invoke the service  "
                       :parameters {:body ::payload}
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
