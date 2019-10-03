(ns koi.examples.filter-empty-rows
  (:require
   [clojure.core.matrix :as m]
   [clojure.core.matrix.random :as crand]
   [clojure.core.matrix.stats :as ist]
   [clojure.core.matrix.dataset :as cd]
   [clojure.data.csv :as csv]
   [starfish.core :as s ]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [clojure.java.io :as io]))

(defn filter-rows
  [{:keys [dataset max-empty-columns] :or {max-empty-columns 10} :as m}]
  (let [cont (-> dataset s/content)
        _ (println " filter-rows content " (class cont))
        max-ec (if (number? max-empty-columns) max-empty-columns
                   (try (Integer/parseInt max-empty-columns)
                        (catch Exception e 100)))
        csv-vector (csv/read-csv (io/reader (io/input-stream cont)))
        _ (println " csv-vector " csv-vector)
        dset (cd/dataset (first csv-vector) (rest csv-vector))
        k (->> dset
               cd/row-maps
               (remove #(let [freq (frequencies (vals %))
                              iv (get freq "")]
                          (and iv (< max-ec iv)))))
        filtdset (cd/dataset k)
        _ (println " filtdset " filtdset)
        dset (cd/dataset (first csv-vector) (rest csv-vector))
        baos (new java.io.ByteArrayOutputStream)
        res-data (do (with-open [writer (io/writer baos)]
                       (csv/write-csv writer 
                                      (into [(cd/column-names filtdset)]
                                            (mapv m/to-vector (m/rows filtdset)))))
                     (.toString baos))
        res {:filtered-dataset
             res-data }]

    (println " filter-rows response " filtdset)
    res))

