(ns koi.addservice
  (:require [koi.invokable :refer [Invokable]])
  (:import [java.util UUID]))


(def result (atom {}))
(def status (atom {}))
(defrecord AdditionSvc
    []
    Invokable
  (invoke [_ {:keys [args] :as m}]
    (let [uid (.toString (UUID/randomUUID))
          _ (println " in invoke " m)
          add-args (some #(if (= "addArgs" (:name %)) %) args)
          {:keys [a b] :as ab} (get add-args :value)]
      (println " add-args " add-args  " --  " ab)
      (swap! status assoc uid :inprogress)
      (swap! result assoc uid (+ a b))
      (swap! status assoc uid :completed)
      uid))
  (get-status [_ job-id]
    (get @status job-id))
  (get-result [_ job-id]
    (get @result job-id))
  (get-proof [_ job-id]
    ""))

(def add-svc (AdditionSvc. ))

#_(let [uid (invoke add-svc {:a 10 :b 20} )]
  {:status (get-status add-svc uid)
   :result (get-result add-svc uid)
   :uid uid})
