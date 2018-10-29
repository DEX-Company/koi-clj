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
   [ring.util.http-response :refer [ok header created bad-request]]
   [ring.swagger.upload :as upload]
   [koi.config :as conf :refer :all]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [muuntaja.core :as muuntaja]
   [koi.invokable :refer [invoke get-status get-result get-proof]]
   [muuntaja.format.json :as json-format]
   [koi.spec :as ps]
   [org.httpkit.client :as ht]
   [clojure.data.json :as json])
  (:import [java.util UUID Base64]))

(defn decode [to-decode]
  (.decode (Base64/getDecoder) to-decode))
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

(s/defschema JobResultResp
  {:result s/Any})

(s/defschema InvokeJobResp
  {:jobid s/Str})

(s/defschema GetSvcResp
  {:services s/Any})

(s/defschema InvokeSvcReq
  {:serviceid s/Str
   :path s/Str
   :method s/Str
   ;;base64 encoded string
   :payload s/Str})

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

    (context "/"
             []
             :tags ["Invoke API "]

             (POST "/register" []
                   :return RegSvcResp
                   :body [ireq RegSvcReq]
                   :summary "register a service"
                   (let [{:keys [:iri] :as m } ireq
                         _ (println " inputs  " m)
                         uuid (.toString (UUID/randomUUID))
                         pathdoc (parse-swagger iri)
                         res (swap! services assoc uuid pathdoc)]
                     (ok {:serviceid uuid})))

             (GET "/services" []
               :return GetSvcResp 
               :summary "register a service"
               (let [res @services]
                 (ok {:services res})))

             (POST "/invoke" []
                  :return InvokeSvcResp
                  :body [ireq InvokeSvcReq]
                  :summary "Returns result of job"
                  (let [{:keys [serviceid path method payload] :as m} ireq
                        svcuri (get @services serviceid)
                        valid-paths (get-in svcuri [:paths path method])
                        payload (json/read-str (String. (decode payload)))
                        #_locpayload #_{:asseta {:purchasetoken "string"
                                             :asseturi "http://localhost:3000/index.html"}
                                    :svc {:purchasetoken "string"
                                          :publickey "string"}}
                        svcuri2 (str svcuri path)
                        _ (println " svcuri path "  svcuri " - " svcuri2 " - val " valid-paths)]
                    (if (nil? valid-paths)
                      (bad-request "invalid path " m )
                      (let [host (get svcuri :uri)
                            host2 (.substring host 0 (dec (.length host)))
                            apiurl (str host2 path)
                            _ (println " api url " apiurl)
                            resp @(ht/request {:url apiurl 
                                               :method (keyword method)
                                               :headers {"Content-Type" "application/json"}
                                               :body (json/write-str payload)})]
                        (println " invoke ---- " serviceid " - " svcuri " - " path " - " method  " - " payload
                                 " response " resp)
                        (ok {:response (:body resp)})))))

             (POST "/invokeOcn" []
                   :return InvokeSvcResp
                   :body [ireq InvokeSvcReq]
                   :summary "Returns result of job"
                   (let [{:keys [serviceid path method payload] :as m} ireq
                         svcuri (get @services serviceid)
                         _ (println serviceid " - " svcuri " - " path " - " method  " - " (json/read-str payload))
                         resp @(ht/request {:url (str svcuri path)
                                            :method (keyword method)
                                            :body payload})]
                     (ok {:response resp})))

             #_(GET "/status/:jobId" []
                  :return JobStatusResp
                  :path-params [jobId :- s/Str]
                  :summary "Returns status of job"
                  (ok {:status (name (get-status adsvc jobId))}))))
   (wrap-cors :access-control-allow-origin [#"http://localhost:3449"]
              :access-control-allow-methods [:get :put :post :delete])))

