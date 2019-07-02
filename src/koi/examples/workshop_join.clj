(ns koi.examples.workshop-join
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
                                async-handler
                                process]]
   [aero.core :refer (read-config)]
   [clojure.data.json :as json]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::vehicle-dataset ::ispec/asset)
(sp/def ::workshop-dataset ::ispec/asset)


(sp/def ::params (sp/keys :req-un [::vehicle-dataset
                                   ::workshop-dataset]))

(def join-key "vin")
(defn join-datasets
  [veh-dset workshop-dset]
  (let [veh (json/read-str veh-dset)
        wksp (json/read-str workshop-dset)
        wksp-data 
        (->> (get wksp "maintenance")
             (filterv #(= (get veh join-key) (get % join-key)))
             (mapv #(dissoc % join-key)))]
    (json/write-str (assoc veh "maintenance" wksp-data))))

(defn join-dataset-fn 
  [agent {:keys [vehicle-dataset workshop-dataset]}]
  (fn []
    (let [assets (mapv #(get-asset agent %)
                       [vehicle-dataset workshop-dataset])
          [veh-dset wksp-dset] (mapv #(->> % s/content s/to-string)
                                     assets)
          joined-dset (join-datasets veh-dset wksp-dset)
          res {:dependencies assets 
               :results [{:param-name :joined-dataset
                          :type :asset
                          :metadata (-> assets first s/metadata)
                          :content joined-dset}]}]
      (info " result of join-dataset " res)
      res)))

(deftype JoinCarsDatasetClass [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args join-dataset-fn))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process args join-dataset-fn)))

  prot/PParams
  (get-params [_]
    ::params))

(defn new-join-cars-dataset
  [jobs jobids]
  (JoinCarsDatasetClass. jobs jobids))
