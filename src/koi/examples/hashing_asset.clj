(ns koi.examples.hashing-asset
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
   [koi.utils :as utils :refer [put-asset get-asset-content get-asset remote-agent keccak512
                                process]]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::to-hash ::ispec/asset)


(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash {:did "1234567890123456789012345678901234567890123456789012345678901234"}})


(defn compute-hash
  [agent {:keys [to-hash algorithm]}]
  (let [ast (get-asset agent to-hash)
        cont (-> ast s/content s/to-string)]
    (fn []
      (let [hashval (cond (= algorithm "keccak256")
                      (Hash/keccak256String cont)
                      (= algorithm "keccak512")
                      (keccak512 cont)
                      :default 
                      (Hash/keccak256String cont))
            res {:dependencies [ast]
                 :results [{:param-name :hash-value
                            :type :asset
                            :content hashval}]}]
        (info " result of execfn " res)
        res))))

(deftype HashingAsset [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args compute-hash))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [jobid (swap! jobids inc)]
      (doto (Thread.
             (fn []
               (swap! jobs assoc jobid {:status :accepted})
               (try (let [res (process args compute-hash)]
                      (swap! jobs assoc jobid
                             {:status :succeeded
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
