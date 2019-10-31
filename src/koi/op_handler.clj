(ns koi.op-handler
  (:require 
   [org.httpkit.client :as http]
   [cheshire.core :as che :refer :all]
   [starfish.core :as s]
   [clojure.spec.alpha :as sp]
   [com.stuartsierra.component :as component]
                                        ;[koi.middleware :as km]
   [koi.utils :as ut :refer [async-handler]]
   [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                      not-found]]
   [ring.util.http-status :as status]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.config :as config :refer [get-config get-remote-agent]]
   [koi.interceptors :as ki]))

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
  ([agent storage register-fn conf]
   (let [operations (:operations conf)
         op-keys (keys operations)
         _ (println " operation registry storage " storage " agent " agent)
         op-impls (mapv #(do
                           (try
                             (info " loading " %)
                             (clojure.lang.Reflector/invokeConstructor
                              (resolve (symbol %))
                              (to-array [agent (:storage storage) jobs jobids]))

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
     (info " registered assets " res)
     res)))

(defrecord OperationRegistry [storage agent config]
  component/Lifecycle

  (start [component]
    (info ";; Starting operation registry ")
    (let [reg (operation-registry (:agent agent)
                                  storage
                                  (fn[cfg]
                                    (register-operations (:agent (:agent agent))
                                                         (:operations cfg)))
                                  config)]
      (assoc component :operation-registry reg)))

  (stop [component]
    (info ";; Stopping registry ")
    (assoc component :operation-registry nil)))

(defrecord Agent [config]
  component/Lifecycle

  (start [component]
    (let [ag (get-remote-agent config)
          res (assoc component :agent ag)]
      (info ";; Starting agent " res)
      res))

  (stop [component]
    (info ";; Stopping agent ")
    (assoc component :agent nil)))

(defn new-agent
  [config]
  (let [res (map->Agent {:config config})]
    (println " new agent " res)
    res))

(defn new-operation-registry
  [config]
  (map->OperationRegistry {:config config}))

(defn meta-handler
  "this method handles API calls to /meta/data:did. Returns the meta data for an operation "
  [config]
  (let [registry (:operation-registry config)
        ;;add the hash of the metadata as an additional key
        registry (reduce-kv (fn[acc k v]
                              (let [metadata (:metadata v)
                                    metadata-str (json/write-str metadata)
                                    asset-id (s/digest metadata-str)]
                                (info " registering operation " k " against hash " asset-id)
                                (assoc acc k metadata
                                       (keyword asset-id) metadata)))
                            {}
                            registry)]
    {:get-handler
     (fn [inp]
       (let [{:keys [asset-id]} (:route-params inp)]
         (if-let [metadata (registry (keyword asset-id))]
           (ok metadata)
           (do (error " invalid operation did " asset-id)
               (not-found (str "operation did " asset-id " is not a valid resource "))))))
     :list-handler
     (fn [inp2]
       (ok (->> (keys registry)
                (filterv #(= 64 (count (name %)))))))}))

#_(defn inv-sync
  [op-config params]
  (let [mid (km/test-middleware op-config)
        resp (mid {:invoke-args params})]
    resp))

(defn invoke-handler
  "this method returns a function that handles API calls to /invoke.
  The first argument is a boolean value, if true,
  responds with a job id. Else it returns synchronously."
  ([registry]
   (fn [inp]
     (let [params (or (:body-params inp) (:body inp))
           {:keys [operation-id]} (:route-params inp) ]
       (info (str "got invoke request for " operation-id " params " params
                  " \n route-params " (:route-params inp)
                  " \n registry keys " (registry (keyword operation-id))
                  " \n get registry "
                  (get registry (keyword operation-id))
                  ))
       (if-let [handler-fn (registry (keyword operation-id))]
         (try
           (let [invres (handler-fn params)]
             (info " invoke-response " invres )
             (if (:error invres)
               (do
                 (error (str " invoke handler returned an error " ))
                 (http-response/bad-request
                  (str " server error executing operation " (-> invres :error :cause))))
               (do 
                 (info " result of invoke resp: " invres)
                 (ok invres))))
           (catch Exception e
             (do
               (error (str " got error in invoke " e))
               (clojure.stacktrace/print-stack-trace e)
               (http-response/internal-server-error " server error executing operation "))))
         (do (error " invalid operation did " operation-id)
             (not-found (str "operation did " operation-id " is not a valid resource "))))))))

(defn invoke-async-handler
  "handler for async operation.
  Returns a function that is passed the body of the request.
  "
  [handler]
  (fn [inp]
     (let [params (or (:body-params inp) (:body inp))
           {:keys [operation-id]} (:route-params inp) ]
       (if-let [handler-fn (handler (keyword operation-id))]
         (try
           (let [invres (async-handler jobids jobs
                                       (fn[] (handler-fn params)))]
             (info " result of async invoke start " invres)
             (created "url" invres))
           (catch Exception e
             (do
               (error (str " got error in invoke " e))
               (clojure.stacktrace/print-stack-trace e)
               (http-response/internal-server-error " server error executing operation "))))
         (do (error " invalid operation did " operation-id)
             (not-found (str "operation did " operation-id " is not a valid resource ")))))))

(defn- get-results
  ([sel-fn inp]
   (let [{:keys [jobid]} (:route-params inp)]
     (try
       (let [parsed-jobid (Integer/parseInt jobid)
             job (get @jobs parsed-jobid)]
         (info " get jobs " job)
         (if job
           (ok (sel-fn job))
           (not-found (str " Job with id: " jobid " not found "))))
       (catch Exception e
         (do
           (error (str " got error in getting job results " e))
           (clojure.stacktrace/print-stack-trace e)))))))

(defn combined-handler
  "returns the status and the result of the job."
  ([inp]
   (let [res (get-results identity inp)]
     (info (str " combined handler returns " res))
     res)))

(defn status-handler
  "returns the status of the job."
  ([inp]
   (let [res (get-results
              #(select-keys % [:status])
              inp)]
     res)))

(defn result-handler
  "returns the result of the job."
  ([inp]
   (let [res (get-results
              #(do
                 (select-keys % [:results]))
              inp)]
     res)))
