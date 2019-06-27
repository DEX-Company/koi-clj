(ns koi.examples.filter-empty-rows
  (:require
   [clojure.spec.alpha :as sp]
   [clojure.core.matrix :as m]
   [clojure.core.matrix.random :as crand]
   [clojure.core.matrix.stats :as ist]
   [clojure.core.matrix.dataset :as cd]
  [clojure.data.csv :as csv]
   [starfish.core :as s ]
   [koi.utils :as utils :refer [put-asset get-asset-content remote-agent process async-handler
                                get-asset]]
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
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc]))

(sp/def ::dataset ::ispec/asset)
(sp/def ::max-empty-columns number?)
(sp/def ::params (sp/keys :req-un [::dataset]
                          :opt-un [::max-empty-columns]))


;(def max-empty-cols 20)

(defn filter-rows
  [agent {:keys [dataset max-empty-columns] :or {max-empty-columns 10} :as m}]
  (let [ast (get-asset agent dataset)
        cont (-> ast s/content)
        max-ec (if (number? max-empty-columns) max-empty-columns
                   (try (Integer/parseInt max-empty-columns)
                        (catch Exception e 100)))]
    (println " max ent " max-ec)
    (fn []
      (let [
            csv-vector (csv/read-csv (io/reader (io/input-stream cont)))
            dset (cd/dataset (first csv-vector) (rest csv-vector))
            k (->> dset
                   cd/row-maps
                   (remove #(let [freq (frequencies (vals %))
                                  iv (get freq "")]
                              (and iv (< max-ec iv)))))
            filtdset (cd/dataset k)
            baos (new java.io.ByteArrayOutputStream)
            res-data (do (with-open [writer (io/writer baos)]
                  (csv/write-csv writer 
                                 (into [(cd/column-names filtdset)]
                                       (mapv m/to-vector (m/rows filtdset)))))
                (.toString baos))
            res {:dependencies [ast]
                 :results [{:param-name :filtered-dataset
                            :type :asset
                            :content res-data}]}]
        res))))

(deftype FilterRowsClass [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args filter-rows))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process args filter-rows)))

  prot/PParams
  (get-params [_]
    ::params))

(defn new-filter-rows
  [jobs jobids]
  (FilterRowsClass. jobs jobids))
