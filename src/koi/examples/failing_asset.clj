(ns koi.examples.failing-asset
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

(sp/def ::dummy string?)
(sp/def ::params (sp/keys :req-un [::dummy]))

(defn process
  [dummy]
  (throw (Exception. "test exception")))

(deftype FailingAsset [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [d (:dummy args)]
      (process d)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [d (:dummy args)
          jobid (swap! jobids inc)]
      (doto (Thread. (fn []
                       (swap! jobs assoc jobid {:status :accepted})
                       (try (let [res (process d)]
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

(defn new-failing
  [jobs jobids]
  (FailingAsset. jobs jobids))
