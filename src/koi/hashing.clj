(ns koi.hashing
  (:require
   [clojure.spec.alpha :as sp]
   [invoke-spec.protocols :as prot 
    :refer [invoke-sync invoke-async get-result 
            get-status get-metadata]]
   [clojure.java.io :as io]
   [invoke-spec.asset :as oas]))

(sp/def ::input-file ::oas/ocean-asset)
(sp/def ::output-map string?)
(sp/def ::input-map (sp/keys :req-un [::input-file]))
(sp/valid? ::input-map {:input-file
                        {:asset-did "1234567890123456789012345678901234567890123456789012345678901234"
                         :service-agreement-id "1234567890123456789012345678901234567890123456789012345678901234"}})

(deftype Hashing [conf]
  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [input-file (-> args :params :input-file :asset-url)]
      (println " called hashing with file " input-file )
      (str (hash input-file)))))

(defn new-hashing
  [conf]
  (Hashing. conf))
