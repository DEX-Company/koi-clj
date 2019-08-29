(ns koi.examples.prov-tree-traversal
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
            valid-args?]]
   [koi.invokespec :as ispec]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.java.io :as io]
   [koi.utils :as utils :refer [put-asset get-asset-content get-asset keccak512
                                async-handler
                                process]]
   [aero.core :refer (read-config)]
   [clojure.data.json :as json]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::asset ::ispec/asset)
(sp/def ::params (sp/keys :req-un [::asset]))


(defn get-prov-section
  [metadata]
  (if-let [p (:provenance metadata)]
    (->  p
         (.toString)
         json/read-str
         keywordize-keys)
    {}))

(defn derived-by
  [asset]
  (let [provsec (get-prov-section (s/metadata asset))]
    (if provsec
      (->> provsec 
           :wasDerivedFrom vals
           (mapv :prov:usedEntity))
      [])))



(defn walk-fn
  [remote-agent asset-id]
  (let [ast (s/get-asset remote-agent asset-id)
        mdata (s/metadata ast)
        imap {:asset-id asset-id
              :metadata (dissoc mdata :provenance)}]
    (if-let [prov (:provenance mdata)]
      (merge imap {:provenance (get-prov-section mdata)
                   :derived-from (derived-by ast)}
             (get-prov-section mdata))
      imap)))

(defn prov-data
  [remote-agent asset-id]
  (let [k1 (walk-fn remote-agent asset-id)]
    (assoc k1 :derived-from (mapv (partial walk-fn remote-agent) (:derived-from k1)))))

(defn prov-tree-fn
  [remote-agent {:keys [asset]}]
  (fn []
    (let [data (json/write-str (prov-data remote-agent (:did asset)))
          res {:dependencies [asset] 
               :results [{:param-name :prov-tree
                          :type :json
                          :content data}]}]
      res)))

(deftype ProvTraversalClass [agent jobs jobids]
  :load-ns true
  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process agent args prov-tree-fn))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process agent args prov-tree-fn)))

  prot/PValidParams
  (valid-args? [_ args]
    {:valid? (sp/valid? ::params args)}))
