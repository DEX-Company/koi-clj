(ns koi.hashing-asset
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [clojure.java.io :as io]
   [invoke-spec.asset :as oas]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc]))

(sp/def ::did (sp/and string? #(= 64 (count %))))
(sp/valid? ::did "1234567890123456789012345678901234567890123456789012345678901234")
(sp/def ::purchase-token string?)
(sp/def ::asset (sp/keys :req-un [::did]
                         :opt-un [::purchase-token]) )

(sp/def ::position int?)
(sp/def ::required boolean?)

(sp/def ::to-hash ::asset)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash {:did "1234567890123456789012345678901234567890123456789012345678901234"}})

(def surfer-url (:surfer-url (read-config (clojure.java.io/resource "config.edn"))))

(def surfer (s/surfer surfer-url))

(defn register-asset
  ([content] (register-asset surfer content))
  ([surfer content]
   (let [a1 (s/asset (s/memory-asset {:abc "def"} content))
         remote-asset (s/register surfer a1)
         _ (s/upload surfer a1)]
     (s/asset-id remote-asset))))

(defn get-asset-content
  ([did] (get-asset-content surfer did))
  ([surfer did]
   (->> did
        (s/get-asset surfer)
        (s/content)
        s/to-string)))

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
                       ;;run job
                       (swap! jobs assoc jobid {:status :completed
                                                :results (:results (process did))})))
        .start)
      {:jobid jobid}))
  
  prot/PParams
  (get-params [_]
    ::params))

(defn new-hashing
  [jobs jobids]
  (HashingAsset. jobs jobids))
