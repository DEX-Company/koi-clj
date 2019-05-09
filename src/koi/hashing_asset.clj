(ns koi.hashing-asset
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [koi.invokespec :as ispec]
   [clojure.java.io :as io]
   [koi.utils :as utils :refer [register-asset get-asset-content surfer keccak512]]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [sg.dex.starfish.util Hex]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::to-hash ::ispec/asset)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash {:did "1234567890123456789012345678901234567890123456789012345678901234"}})



(defn process
  [alg did]
  (let [cont (get-asset-content surfer did)
        res (cond (= alg "keccak256")
                  (Hash/keccak256String cont)
                  (= alg "keccak512")
                  (keccak512 cont)
                  :default 
                  (Hash/keccak256String cont))
        reg-asset-id (register-asset surfer res)]
    {:results {:hash_value {:did reg-asset-id}}}))

(deftype HashingAsset [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [to-hash (:to-hash args)
          alg (:algorithm args)
          did (:did to-hash)]
      (process did)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [to-hash (:to-hash args)
          alg (:algorithm args)
          did (:did to-hash)
          jobid (swap! jobids inc)]
      (doto (Thread.
             (fn []
               (swap! jobs assoc jobid {:status :accepted})
               (try (let [res (process alg did)]
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

(defn new-hashing
  [jobs jobids]
  (HashingAsset. jobs jobids))
