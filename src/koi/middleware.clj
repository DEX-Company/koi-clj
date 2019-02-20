(ns koi.middleware
  (:require 
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [clojure.spec.alpha :as sp]
   [invoke-spec.protocols :as prot :refer [invoke-sync invoke-async get-result get-status get-metadata]]
   [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity]]
   [ring.util.http-status :as status]
   [clj-openrefine.core :as ore]
   [spec-tools.json-schema :as jsc]
   [clojure.java.io :as io]
   [koi.hashing :as h]
   [scjsv.core :as jsv]
   [invoke-spec.asset :as oas]))

(def service-registry
  {;:run-notebook (scoringspec/new-scoring {})
                                        ;:addition (additionspec/new-addition {})
   :hashing (h/new-hashing {})
   :openrefine (ore/new-openrefine {})})

(def svc-metadata
  (let [svcmd #(let [gm (get-metadata (service-registry %))]
                 {:operation (name %) 
                  :params (jsc/transform (:input gm))
                  :returns (jsc/transform (:output gm))})]
    (mapv svcmd [;:scoring
                 ;:addition
                 :openrefine])))

(def operation-schema-validator
  {:hashing (jsv/validator (jsc/transform ::h/params))})

(def ast-metadata {})
(defn get-asset-metadata
  [asset-id]
  (get ast-metadata (keyword asset-id)
       {}))

(def ast-url-map
  {"1234567890123456789012345678901234567890123456789012345678901234"
   "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv"})

(defn get-asset-url
  [asset-id]
  (let [res (get ast-url-map asset-id)]
    (println " returning asset url " res " for " asset-id)
    res))

(defn wrap-asset-metadata
  [input]
  (let [ocn (get-in input [:input :ocean-inputs] )
        mdata (if ocn
                  (assoc input :asset-metadata
                         (apply merge
                                (mapv (fn[[k v]]
                                        {k (get-asset-metadata (keyword v))}) ocn)))
                  input)]
    mdata))

(defn wrap-asset-url
  "for keys in :params whose values are ocean assets,
  get the asset-url corresponding to the asset did and assoc it in."
  [input]
  (let [input-list (get-in input [:params])
        kfn (fn[[k v]]
              (if (sp/valid? ::oas/ocean-asset v)
                (let [{:keys [asset-did] :as inp} v]
                  {(keyword k)
                   (assoc v :asset-url (get-asset-url asset-did))})))
        dec-arg (if input-list
                  (assoc input :params
                         (apply merge (mapv kfn input-list)))
                  input)]
    dec-arg))

(defn invoke-handler
  [inp]
  (let [{:keys [operation params] :as args} (:body-params inp)]
    (println " operation " operation " - params " params )
    (if-let [ep (service-registry (keyword operation))]
      (let [validator (operation-schema-validator (keyword operation))]
        (if (and validator params (nil? (validator params)))
          (ok (invoke-sync ep
                           (->> args
                                wrap-asset-url)))
          (http-response/bad-request (str " invalid request: " (if params (clojure.string/join (validator params)) " params is not present") " - " ))))
      (do (println " invalid operation " operation)
          (unprocessable-entity (str "operation " operation " is not supported")))))) 
