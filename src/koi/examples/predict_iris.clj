(ns koi.examples.predict-iris
  (:require
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [clojure.java.io :as io]))

(defn predict-class
  [{:keys [dataset]}]
  (try 
    (let [cont (-> dataset s/content s/to-string)
          isp (clojure.string/split cont #"\n" )
          predictions (->> (into [(str (first isp) ",predclass")]
                                 (mapv #(str % ",setosa") (rest isp)))
                           (clojure.string/join "\n"))
          res {:predictions (s/asset (s/memory-asset
                                      {"iris" "predictions"}
                                      predictions))}]
      ;(info " result of predict-class " res)
      res)
    (catch Exception e
      (println " error executing predict-class " (.getMessage e))
      {:error "unable to run predictor"})))
