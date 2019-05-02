(ns koi.middleware
  (:require 
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [clojure.spec.alpha :as sp]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity]]
   [ring.util.http-status :as status]
   [spec-tools.json-schema :as jsc]
   [clojure.java.io :as io]
   [koi.hashing :as h]
   [koi.hashing-asset :as ha]
   [koi.failing-asset :as f]
   [koi.prime-num :as p]
   [scjsv.core :as jsv]
   ))

(def jobids (atom 0))
(def jobs (atom {}))
(def service-registry
  {:hashing (h/new-hashing jobs jobids)
   :assethashing (ha/new-hashing jobs jobids)
   :primes (p/new-primes jobs jobids)
   :fail (f/new-failing jobs jobids)
   })

(defn invoke-handler
  "this method handles API calls to /invoke. The first argument is a boolean value, if true,
  responds with a job id. Else it returns synchronously."
  ([inp] (invoke-handler false inp))
  ([async? inp]
   (let [params (:body-params inp)
         {:keys [did]} (:route-params inp)]
     (if-let [ep (service-registry (keyword did))]
       (let [validator (get-params ep) ]
         (if (and validator params (sp/valid? validator params))
           (do
             (println " valid request, making invoke request with " params)
             (if async?
               (try
                 (let [invres (invoke-async ep params)]
                   (println " result of invoke start " invres)
                   (created "url" invres))
                 (catch Exception e
                   (do
                     (println (str " got error in invoke " e))
                     (clojure.stacktrace/print-stack-trace e)
                     (http-response/internal-server-error " server error executing operation "))))
               (try 
                 (ok (invoke-sync ep params))
                 (catch Exception e
                   (do
                     (println (str " got error in invoke " e))
                     (clojure.stacktrace/print-stack-trace e)
                     (http-response/internal-server-error " server error executing operation ")))
                 ))
             )
           (do
             (println " invalid request, sending error in invoke request with " params)
             (http-response/bad-request (str " invalid request: " (if params (clojure.string/join (validator params)) " params is not present") " - " )))))
       (do (println " invalid operation did " did)
           (unprocessable-entity (str "operation did " did " is not supported"))))))) 

(defn result-handler
  ([inp]
   (let [{:keys [jobid]} (:route-params inp)]
     (try
       (println " result-handler " jobid " " @jobs)
       (ok (get @jobs (Integer/parseInt jobid)))
       (catch Exception e
         (do
           (println (str " got error in getting job results " e))
           (clojure.stacktrace/print-stack-trace e)))))))
