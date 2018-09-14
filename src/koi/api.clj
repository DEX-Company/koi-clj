(ns koi.api
  (:require 
   [outpace.config :refer [defconfig]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [compojure.api.sweet :as sw :refer [api context
                                       undocumented
                                       GET PUT POST DELETE]]
   [ring.middleware.cors :refer [wrap-cors]]
   [compojure.api.coercion.schema :as cos]
   [compojure.route :as route]
   [schema.core :as s]
   [clojure.java.io :as io]
   [ring.util.http-response :refer [ok header created]]
   [koi.zeppelin :refer [invoke-job]]
   [ring.swagger.upload :as upload]
   [koi.config :as conf :refer :all]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [muuntaja.core :as muuntaja]
   [muuntaja.format.json :as json-format]
   [koi.spec :as ps])
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema Dependency
  {:assetId s/Str})

(s/defschema InvokeJobReq
  {:accountId s/Str
   :dependencies [Dependency]
   :algorithmAssetId s/Str
   :payload s/Any})

(s/defschema JobResp
  {:jobId s/Str
   (s/optional-key :errorCode) s/Num
   (s/optional-key :status) s/Str})

(defn stringify-vals
  [imap]
  (apply merge (mapv (fn[[k v]] {k (if (keyword? v) (name v) v)}) imap)))

(def app
  (-> 
   (api
    {:coercion :schema 
     :swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Notebook provider API"
                    :description "API methods for notebook management"}
             :tags [{:name "api", :description "Manage exploratory data science and invokable notebooks"}]
             :produces ["application/json"]
             }}}

    (context notebook-invoke []
             :tags ["Invoke notebook API "]

             (POST "/invokejob" []
                   :return JobResp
                   :body [ireq InvokeJobReq]
                   :summary "Invoke a job"
                   (let [{:keys [:accountId :algorithmAssetId :payload :dependencies] :as m } ireq
                         resp (invoke-job {::ps/accountId accountId
                                           ::ps/algorithmAssetId algorithmAssetId})]
                     (-> (created nil resp)
                         (dissoc :headers))))

             (GET "/status/:jobId" []
                  :return JobResp
                  :query-params [jobId :- s/Str]
                  :summary "Returns status of job"
                  (ok {:purchasedAssets  [{:id "0xfaddaadda", :description "weather data"},
                                          {:id "0xcfddaadda", :description "precipitation data"}]}))))

   (wrap-cors :access-control-allow-origin [#"http://localhost:3449"]
              :access-control-allow-methods [:get :put :post :delete])))
