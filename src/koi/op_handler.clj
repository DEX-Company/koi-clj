(ns koi.op-handler
  (:require 
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [starfish.core :as s]
   [clojure.spec.alpha :as sp]
   [com.stuartsierra.component :as component]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            valid-args?]]
   [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                      not-found]]
   [ring.util.http-status :as status]
   [clojure.java.io :as io]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.config :as config :refer [get-config get-remote-agent]]))

;;the jobs are stored as in-memory atoms. A production setup might
;;want to store the jobs in persisten (e.g db) storage
(def jobids (atom 0))
(def jobs (atom {}))

(defn register-operation
  "register an operation using ht eagent. Returns the asset id of the operation."
  [agent asset-metadata-resource]
  (let [ast-metadata (->> asset-metadata-resource
                            slurp che/parse-string)
        ast (s/memory-asset ast-metadata "")
        remote-asset (s/register agent ast)
        res (s/asset-id remote-asset)]
    (keyword res)))

(defn register-operations
  "registers a map of operations as defined in the config. Returns a list of
  asset ids."
  [agent operations]
  (let [operation-keys (keys operations)
        regd-ids
        (mapv (partial register-operation agent)
              (mapv clojure.java.io/resource
                    (mapv (fn[[k v]]
                            (:metadata v)) operations)))]
    (info "registering "
          (clojure.string/join "\n " (mapv #(str %1 " -> " %2)
                                           operation-keys
                                           regd-ids )))
    regd-ids))

(defn operation-registry
  "return a map that acts as an operation registry. Key is operation id/common name
  and value is the implementation class to be called.
  The register-fn takes a configuration map that registers the operations. If the agent is local,
  then use local registration mechanism, else use a remote agent.
  "
  ([] (operation-registry (get-config)))
  ([conf](operation-registry (fn[config]
                               (register-operations (:agent (get-remote-agent config))
                                                    (:operations config)))
                             conf))
  ([register-fn conf]
   (let [operations (:operations conf)
         op-keys (keys operations)
         op-impls (mapv #(do
                           (try 
                             (info " loading " %)
                             (clojure.lang.Reflector/invokeConstructor
                              (resolve (symbol %))
                              (to-array [jobs jobids]))

                             (catch Exception e
                               (do 
                                 (error " failed to load operation class " %)
                                 (clojure.stacktrace/print-stack-trace e)))))
                        (mapv (fn[[k v]] (:classname v)) operations))
         regd-ids (try
                    (info " registering operations ")
                    (register-fn conf)
                    ;(register-operations (:agent (get-remote-agent conf)) operations)
                    (catch Exception e
                      (error " failed to register operations ")
                      (clojure.stacktrace/print-stack-trace e)))
         _ (info " op-keys " op-keys  "\n op -impl " op-impls
                    "\n regd-ids " regd-ids)
         res (merge (zipmap op-keys op-impls)
                    (zipmap regd-ids op-impls))]
     ;;return a map that has the asset/operation id as key and value is the operation class
     ;;merging a map that has the common name of the operation for easier testing.
     (println " registered assets " res)
     res)))

(defrecord OperationRegistry [config]
  component/Lifecycle

  (start [component]
    (println ";; Starting registry ")
    (let [reg (operation-registry config)]
      (assoc component :operation-registry reg)))

  (stop [component]
    (println ";; Stopping registry ")
    (assoc component :operation-registry nil)))
                                        ;(defstate registry :start (operation-registry))

(defn new-operation-registry
  [config]
  (map->OperationRegistry {:config config}))

(defn invoke-handler
  "this method handles API calls to /invoke. The first argument is a boolean value, if true,
  responds with a job id. Else it returns synchronously."
  ([registry inp] (invoke-handler registry false inp))
  ([registry async? inp]
   (let [params (or (:body-params inp) (:body inp))
         {:keys [did]} (:route-params inp) ]
     (if-let [ep (registry (keyword did))]
       (let [params-check (valid-args? ep params)
             valid? (:valid? params-check) ]
         (if valid?
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
             (http-response/bad-request (str " invalid request: "
                                             #_(if params (clojure.string/join
                                                         (validator params))
                                                 " params is not present")
                                             " - " )))))
       (do (error " invalid operation did " did)
           (not-found (str "operation did " did " is a valid resource "))))))) 

(defn result-handler
  ([inp]
   (let [{:keys [jobid]} (:route-params inp)]
     (info " result-handler " jobid " " @jobs)
     (try
       (let [parsed-jobid (Integer/parseInt jobid)
             job (get @jobs parsed-jobid)]
         (if job
           (ok job)
           (not-found (str " Job with id: " jobid " not found "))))
       (catch Exception e
         (do
           (error (str " got error in getting job results " e))
           (clojure.stacktrace/print-stack-trace e)))))))
