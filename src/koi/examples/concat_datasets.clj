(ns koi.examples.concat-datasets
  (:require [starfish.core :as s]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [sg.dex.starfish.impl.squid SquidResolverImpl SquidAgent]
           [sg.dex.starfish.util DID]))

(defn merge-maps
  "merge maps, which is wrapped by a vector"
  [map-seq slurp-fn]
  (let [maps (mapv (comp first slurp-fn) map-seq)]
    [(apply merge maps)]))

(defn concat-dataset
  [{:keys [dataset1 dataset2 dataset3] :as assets}]
  (println " type of assets " assets)
  (let [slurp-fn #(->> % s/content s/to-string json/read-str)
        merged-map (merge-maps [dataset1 dataset2 dataset3] slurp-fn)
        resp-dset (s/asset (s/memory-asset (json/write-str merged-map)))
        res {:concatenated-dataset resp-dset}
        _ (println " generated concat dataset, now registering with blockchain ")
        squid-agent (SquidAgent/create (SquidResolverImpl.) (DID/createRandom))
        r2 (s/register squid-agent resp-dset)
        ]
    (println " registered with blockchain " (str r2))
    (merge res
           {:onchain-did
            (str (.getDID r2))})))

#_(let [
      resp-dset (s/asset (s/memory-asset {} "memory asset"))
      squid-agent (SquidAgent/create (SquidResolverImpl.) (DID/createRandom))
      r2 (s/register squid-agent resp-dset)
      ]
    (str (.getDID r2)))
(def inp-datasets
  ["input_service_data.json"
   "input_engine_data.json"
   "input_iot_sensor_data.json"])


(comment
  (def t1 (json/read-str (slurp (io/resource (first inp-datasets)))))

  (def t2 (mapv #(s/asset (s/memory-asset {"metadata" "abc"}
                                          (slurp (io/resource %))))
                inp-datasets))
  (-> t2 first s/content s/to-string json/read-str)
  (-> t1 )
  (spit "/tmp/f.json"
        (json/write-str (merge-maps inp-datasets (fn[i] (-> i io/resource slurp json/read-str)))))

  (-> (concat-dataset (zipmap (mapv #(keyword (str "dataset" %)) (range 1 4)) t2))
      s/content s/to-string json/read-str)
  )

