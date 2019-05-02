(ns koi.failing_asset
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [clojure.java.io :as io]
   [koi.invokespec :as ispec]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc]))

(sp/def ::to-hash ::ispec/asset)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash {:did "1234567890123456789012345678901234567890123456789012345678901234"}})

(defn process
  [did]
  (let [cont (get-asset-content did)
        _ (println " called hashing with content "  cont)
        res (str (hash cont))
        reg-asset-id (register-asset res)]
    {:results {:hash_value {:did reg-asset-id}}}))

(deftype HashingAsset [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [to-hash (:to-hash args)
          did (:did to-hash)]
      (process did)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [to-hash (:to-hash args)
          did (:did to-hash)
          jobid (swap! jobids inc)]
      (doto (Thread. (fn []
                       (swap! jobs assoc jobid {:status :accepted})
                       (try (let [res (process did)]
                              (swap! jobs assoc jobid
                                     {:status :completed
                                      :results (:results res)}))
                            (catch Exception e
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
