(ns koi.api
  (:require 
   [outpace.config :refer [defconfig]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [compojure.api.sweet :as sw :refer [api context
                                       undocumented
                                       GET PUT POST DELETE]]
   [ring.middleware.cors :refer [wrap-cors]]
   [org.httpkit.client :as http]
   [compojure.api.coercion.schema :as cos]
   [compojure.route :as route]
   [schema.core :as s]
   [clojure.java.io :as io]
   [ring.util.http-response :refer [ok header created bad-request]]
   [ring.swagger.upload :as upload]
   [koi.config :as conf :refer :all]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [muuntaja.core :as muuntaja]
   [koi.invokable :refer [invoke get-status get-result get-proof]]
   [muuntaja.format.json :as json-format]
   [cheshire.core :as che :refer [generate-string parse-stream]]
   [koi.spec :as ps]
   [org.httpkit.client :as ht]
   [clojure.data.json :as json])
  (:import [java.util UUID Base64]))

(defn decode [to-decode]
  (.decode (Base64/getDecoder) to-decode))

(defn encode [to-encode]
  (.encodeToString (Base64/getEncoder) to-encode))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
(s/defschema RegSvcReq
  {:iri s/Str})

(s/defschema RegSvcResp
  {:serviceid s/Str})

(defn parse-swagger
  [swag-url]
  (let [paths (-> (slurp swag-url)
                  (json/read-str )
                  (get-in ["paths" ]))]
    {:paths paths :uri (first (.split swag-url "swagger.json"))}))

;(parse-swagger "http://localhost:3000/swagger.json")

(s/defschema JobStatusResp
  {:status s/Str
   (s/optional-key :errorCode) s/Num})

(s/defschema OcnAsset
  {(s/optional-key :assetid) s/Str
   :asseturl s/Str
   :argname s/Str
   (s/optional-key :serviceagreementid) s/Str
   (s/optional-key :assetmetadataurl) s/Str})

(s/defschema JobResultResp
  {:result [OcnAsset]
   (s/optional-key :errorcode) s/Str})

(s/defschema OcnInputs
  {:inputs [OcnAsset]
   (s/optional-key :consumerid) s/Str
   (s/optional-key :invokeserviceagreementid) s/Str})

(s/defschema InvokeJobReq
  {:oceaninputs OcnInputs
   (s/optional-key :configuration) s/Any})


(s/defschema GetSvcResp
  {:services s/Any})

(s/defschema InvokeSvcReq
  {:serviceid s/Str
   :path s/Str
   :method s/Str
   ;;base64 encoded string
   :payload s/Str})


;;



(s/defschema InvokeJobReq
  {:oceaninputs OcnInputs
   (s/optional-key :configuration) s/Any})

(s/defschema InvokeSvcResp
  {:response s/Any})

(s/defschema InvokeOcnSvcReq
  {:serviceid s/Str
   :path s/Str
   :method s/Str
   ;;base64 encoded string
   :payload s/Str})

(s/defschema InvokeOcnSvcResp
  {:response s/Any})

(defn stringify-vals
  [imap]
  (apply merge (mapv (fn[[k v]] {k (if (keyword? v) (name v) v)}) imap)))

(def services (atom {}))
(def status (atom {}))
(def dest-file "/tmp/numlist")
(defn get-asset  
  "get the asset at URL"
  [ast-url]
  (let [k @(http/request {:url ast-url :method :get})
        op (-> k :body)]
    (clojure.java.io/copy op
                          (clojure.java.io/file dest-file))
    dest-file))

(defn register-asset
  [metadata]
  (let [k @(http/request
            {:url "http://13.67.33.157:8080/api/v1/meta/data"
             :method :post
             :body (generate-string metadata)
             :basic-auth ["test" "foobar"]})]
    k))

(def jobs-db (atom {}))
(defn invoke-job
  [{:keys [oceaninputs configuration] :as m}]
  (let [asturl (-> oceaninputs :inputs first :asseturl)
        dstfile (get-asset asturl)
        result 
        (apply + (mapv #(Integer/parseInt %)
                       (-> (slurp dstfile)
                           (.split "\n") seq)))
        resp (register-asset {:name "addition result "
                                      :description "description "
                                      :type "dataset"
                                      ;;add prov metadata here
                                      })]
    (println " result of invoke is " result)
    (spit "/tmp/result" result)
    (-> resp :body io/reader parse-stream)))

(defn insert-job
  [ireq]
  (let [uid (.toString (UUID/randomUUID))
        _ (swap! jobs-db #(update-in % [:status uid] (fn [_]:registered)))
        ;;move this to a separate thread
        resp (invoke-job ireq)]
    (swap! jobs-db #(-> %
                        (update-in [:result uid] (fn[_]resp))
                        (update-in [:status uid] (fn[_]:completed))))
    uid))

;;an atom to hold the service registration
(def service-id (atom nil))

(defn register-metadata
  "register the service"
  []
  (if @service-id @service-id 
      (let [k (register-asset {:name "datasetname "
                               :description "description "
                               :links [{:name ""
                                        :type "algorithm"
                                        :url "abcd"}]
                               :type "service"})
            ast-id (-> k :body io/reader parse-stream)]
        (reset! service-id ast-id)
        ast-id)))
(s/defschema InvokeJobResp
  {:jobid s/Str})

(def app
  (->
   (api
    {:coercion :schema
     :swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Invoke API"
                    :description "API methods for different types of invokable services"}
             :tags [{:name "api", :description "Manage invokable services"}]
             :produces ["application/json"]
             :consumes ["application/json"]
             }}}

    (context "/register"
             []
             :tags ["Register the service "]
             (POST "/" []
                   :return s/Str
                   :summary "Register"
                   (ok (register-metadata))))

    (context "/jobs"
             []
             :tags ["Invoke addition API "]

             (POST "/" []
                   :return InvokeJobResp
                   :body [ireq InvokeJobReq]
                   :summary "Invoke a addition job"
                   (ok {:jobid (insert-job ireq)}))

             (GET "/status/:jobId" []
                  :return JobStatusResp
                  :path-params [jobId :- s/Str]
                  :summary "Returns status of job"
                  (ok {:status
                       (let [status (get-in @jobs-db [:status jobId])]
                         (if status (name status)
                             "invalid jobid"))}))

             (GET "/result/:jobId" []
                  :return JobResultResp
                  :path-params [jobId :- s/Str]
                  :query-params [consumerId :- s/Str]
                  :summary "Returns result of job"
                  (ok (let [i (get-in @jobs-db
                                      [:result jobId])]
                        (println " result is "  i)
                        (if i {:result [{:assetid i
                                         :asseturl "url"
                                         :argname "a"}]}
                            ;;poor error handling
                            {:result [{:assetid "invalid" 
                                       :asseturl "url"
                                       :argname "a"}]
                             :errorcode "400"}))))))

   (wrap-cors :access-control-allow-origin [#"http://localhost:3449"]
              :access-control-allow-methods [:get :put :post :delete])))

