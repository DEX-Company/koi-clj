(ns koi.examples.prov-tree-traversal
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.invokespec :as ispec]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.java.io :as io]
   [koi.utils :as utils :refer [async-handler
                                process]]
   [clojure.data.json :as json]))

(comment
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
    [{:keys [asset]}]
    (let [data (json/write-str (prov-data remote-agent (:did asset)))]
      {:prov-tree data}))
  )
