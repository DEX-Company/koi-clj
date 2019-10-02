(ns koi.examples.workshop-join
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [clojure.java.io :as io]
   [clojure.data.json :as json])
  (:import [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           ))

(def join-key "vin")
(defn- join-raw-datasets
  [veh-dset workshop-dset]
  (let [veh (json/read-str veh-dset)
        wksp (json/read-str workshop-dset)
        wksp-data 
        (->> (get wksp "maintenance")
             (filterv #(= (get veh join-key) (get % join-key)))
             (mapv #(dissoc % join-key)))]
    (json/write-str (assoc veh "maintenance" wksp-data))))

(defn join-dataset
  [{:keys [vehicle-dataset workshop-dataset] :as assets}]
  (let [[veh-dset wksp-dset] (mapv #(->> % s/content s/to-string)
                                   [vehicle-dataset workshop-dataset])
        joined-dset (join-raw-datasets veh-dset wksp-dset)
        resp-dset (s/asset (s/memory-asset joined-dset))
        res {:joined-dataset resp-dset}]
    res))
