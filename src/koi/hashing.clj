(ns koi.hashing
  (:require
   [clojure.spec.alpha :as sp]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [koi.invokespec :as ispec]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf]]
   [clojure.java.io :as io]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]))

(sp/def ::to-hash string?)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash "abc"})
(sp/valid? ::params {:to-hash 1})

(deftype Hashing [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [to-hash (:to-hash args)]
      (info " called hashing with " to-hash)
      (Hash/keccak256String to-hash)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [to-hash (:to-hash args)
          jobid (swap! jobids inc)]
      (swap! jobs assoc jobid {:status :completed
                               :results {:hash_value (str (hash to-hash))}})
      jobid))
  
  prot/PParams
  (get-params [_]
    ::params))

(defn new-hashing
  [jobs jobids]
  (Hashing. jobs jobids))
