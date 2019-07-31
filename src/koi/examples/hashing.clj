(ns koi.examples.hashing
  (:require
   [clojure.spec.alpha :as sp]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [koi.invokespec :as ispec]
   [koi.utils :as utils :refer [get-asset-content keccak512]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf]]
   [clojure.java.io :as io]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.lang.reflect Constructor]))

(sp/def ::to-hash string?)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash "abc"})
(sp/valid? ::params {:to-hash 1})

(defn process
  [cont]
  {:results {:keccak256 (Hash/keccak256String cont)
             :keccak512 (keccak512 cont)}})

(deftype Hashing [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [to-hash (:to-hash args)]
      (info " called hashing with " to-hash)
      (process to-hash)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [to-hash (:to-hash args)
          jobid (swap! jobids inc)]
      (doto (Thread.
             (fn []
               (swap! jobs assoc jobid {:status :accepted})
               (try (let [res (process to-hash)]
                      (swap! jobs assoc jobid
                             {:status :completed
                              :results (:results res)}))
                    (catch Exception e
                      (error " Caught exception running async job " (.getMessage e))
                      (swap! jobs assoc jobid
                             {:status :error
                              :errorcode 8005
                              :description (str "Got exception " (.getMessage e))})))))
        .start)
      {:jobid jobid}))
  
  prot/PParams
  (get-params [_]
    ::params))
