(ns koi.op-handler
  (:require 
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [starfish.core :as s]
   [clojure.spec.alpha :as sp]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity]]
   [ring.util.http-status :as status]
   [clojure.java.io :as io]

   [mount.core :refer [defstate]]
   [koi.examples.hashing :as h]
   [koi.examples.hashing-asset :as ha]
   [koi.examples.failing-asset :as f]
   [koi.examples.prime-num :as p]
   [koi.examples.translate-german-to-en :as trans]
   [koi.examples.predict-iris :as iris]
   [koi.utils :refer [remote-agent]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.config :as config :refer [get-config]]))

(def jobids (atom 0))
(def jobs (atom {}))

(defn default-service-registry
  []
  {:hashing (h/new-hashing jobs jobids)
   :assethashing (ha/new-hashing jobs jobids)
   :primes (p/new-primes jobs jobids)
   :irisprediction (iris/new-iris-predictor jobs jobids)
   :translation (trans/new-german-en-translator jobs jobids)
   :fail (f/new-failing jobs jobids)
   })

(defstate service-registry :start (default-service-registry))

(defn register-operation
  [sfr asset-metadata-resource]
  (let [prime-metadata (->> asset-metadata-resource
                            slurp che/parse-string)
        ast (s/memory-asset prime-metadata "abc")
        remote-asset (s/register sfr ast)
        res (s/asset-id remote-asset)]
    (keyword res)))

(def example-metadata
  ["prime_asset_metadata.json" "hashing_asset_metadata.json"
   "hashing_metadata.json" "irisprediction_metadata.json"
   "translate_german_to_en_metadata.json"])

(def example-dids [:primes :assethashing :hashing :irisprediction :translation])
(defn register-operations
  [sfr]
  (let [regd-ids 
        (mapv (partial register-operation sfr)
              (mapv clojure.java.io/resource example-metadata))]
    (info "registering " (clojure.string/join "\n " (mapv #(str %1 "->" %2) example-metadata regd-ids )))
    regd-ids))

(defn valid-assetid-svc-registry
  []
  (let [svcreg (default-service-registry)
        regd-ids (register-operations (:agent remote-agent))]
    (merge svcreg (zipmap regd-ids (mapv svcreg example-dids)))))

(defn invoke-handler
  "this method handles API calls to /invoke. The first argument is a boolean value, if true,
  responds with a job id. Else it returns synchronously."
  ([inp] (invoke-handler false inp))
  ([async? inp]
   (let [params (:body-params inp)
         {:keys [did]} (:route-params inp) ]
     (if-let [ep (service-registry (keyword did))]
       (let [validator (get-params ep) ]
         (if (and validator params (sp/valid? validator params))
           (do
             (info " valid request, making invoke request with " params)
             (if async?
               (try
                 (let [invres (invoke-async ep params)]
                   (info " result of invoke start " invres)
                   (created "url" invres))
                 (catch Exception e
                   (do
                     (error (str " got error in invoke " e))
                     (clojure.stacktrace/print-stack-trace e)
                     (http-response/internal-server-error " server error executing operation "))))
               (try
                 (let [invres (invoke-sync ep params)]
                   (info " result of invoke resp: " invres)
                   (ok invres))
                 (catch Exception e
                   (do
                     (error (str " got error in invoke " e))
                     (clojure.stacktrace/print-stack-trace e)
                     (http-response/internal-server-error " server error executing operation "))))))
           (do
             (error " invalid request, sending error in invoke request with " params)
             (http-response/bad-request (str " invalid request: " (if params (clojure.string/join (validator params)) " params is not present") " - " )))))
       (do (error " invalid operation did " did)
           (unprocessable-entity (str "operation did " did " is not supported"))))))) 

(defn result-handler
  ([inp]
   (let [{:keys [jobid]} (:route-params inp)]
     (try
       (info " result-handler " jobid " " @jobs)
       (ok (get @jobs (Integer/parseInt jobid)))
       (catch Exception e
         (do
           (error (str " got error in getting job results " e))
           (clojure.stacktrace/print-stack-trace e)))))))
