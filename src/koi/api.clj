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
   [koi.addservice :as addsvc]
   [koi.invokable :refer [invoke get-status get-result get-proof]]
   [muuntaja.format.json :as json-format]
   [koi.spec :as ps])
  (:import [koi.addservice AdditionSvc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;examples
{ :name "arg1" :type :ocean-asset :asset-id "0xff"}
{ :name "arg2" :type :url-asset :url "http://abc.com"}
{ :name "arg3" :type :value :value s/Any }

;;every argument should have a name
(s/defschema ArgName
  {:name s/Str})

(s/defschema Arg
  (s/conditional #(= (:type %) :ocean-asset)
                 (merge ArgName {:type (s/eq :ocean-asset) :asset-id s/Str (s/optional-key :access-token) s/Str})
                 #(= (:type %) :url-asset) (merge ArgName {:type (s/eq :url-asset) :url s/Str})
                 #(= (:type %) "value") (merge ArgName {:type (s/eq "value") :value s/Any})))

(comment 
  (s/validate Arg {:type :ocean-asset :asset-id "0xff" :name "abc"})
  (s/validate Arg {:type :url-asset :url "http://abc.com" :name "def"})
  (s/validate Arg {:type :value :value "http://abc.com" :name "abc"})
  (s/validate Arg {:type :value :value 10 :name "abc"})
  )

(s/defschema OutputConfig
  (s/conditional #(= (:type %) :ocean-asset) {:type (s/eq :ocean-asset)
                                              ;;optionally, tell the service to register the asset giving account-id as owner
                                              ;;if not mentioned, the service owner is the owner of the resulting asset(s)
                                              (s/optional-key :account-id) s/Str
                                              (s/optional-key :log-provenance-trail) s/Bool
                                              }
                 #(= (:type %) "value") {:type (s/eq "value")}))
(comment
  (s/validate OutputConfig {:type :ocean-asset :account-id "abc"})
  (s/validate OutputConfig {:type :ocean-asset})
  (s/validate OutputConfig {:type :value})
  )


(s/defschema OutputArg
  {:args [Arg]
   (s/optional-key :payload) s/Any}  )

;;
(s/defschema InvokeJobReq
  {:args [Arg]
   :result-config [OutputConfig]})

(comment 
  (s/validate InvokeJobReq
              {:args [{:type :value :name "addsvc" :value {:a 10 :b 20}}]
               :result-config [{:type :value}]}))

(s/defschema JobStatusResp
  {:status s/Str
   (s/optional-key :errorCode) s/Num})

(s/defschema JobResultResp
  {:result s/Any})

(s/defschema InvokeJobResp
  {:jobid s/Str})

(defn stringify-vals
  [imap]
  (apply merge (mapv (fn[[k v]] {k (if (keyword? v) (name v) v)}) imap)))

(def adsvc (AdditionSvc. ))
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
             :consumes ["application/json"]
             }}}

    (context "/addition"
        []
      :tags ["Invoke addition API "]

      (POST "/" []
        :return InvokeJobResp
        :body [ireq InvokeJobReq]
        :summary "Invoke a addition job"
        (let [{:keys [:resultConfig :args] :as m } ireq
              _ (println " inputs  " m)
              resp (invoke adsvc m)]
          (-> (created nil {:jobid resp})
              (dissoc :headers))))

      (GET "/result/:jobId" []
        :return JobResultResp
        :path-params [jobId :- s/Str]
        :summary "Returns result of addition job"
        (ok {:result (get-result adsvc jobId)}))

      (GET "/status/:jobId" []
        :return JobStatusResp
        :path-params [jobId :- s/Str]
        :summary "Returns status of job"
        (ok {:status (name (get-status adsvc jobId))}))))

   (wrap-cors :access-control-allow-origin [#"http://localhost:3449"]
              :access-control-allow-methods [:get :put :post :delete])))
