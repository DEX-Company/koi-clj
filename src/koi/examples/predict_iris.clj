(ns koi.examples.predict-iris
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
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::dataset ::ispec/asset)


(sp/def ::params (sp/keys :req-un [::dataset]))

(defn predict-class
  [agent {:keys [dataset]}]
  (let [ast (get-asset agent dataset)
        cont (-> ast s/content s/to-string)]
    (fn []
      (let [isp (clojure.string/split cont #"\n" )
            predictions (->> (into [(str (first isp) ",predclass")]
                       (mapv #(str % ",setosa") (rest isp)))
                 (clojure.string/join "\n"))
            res {:dependencies [ast]
                 :results [{:param-name :predictions
                            :type :asset
                            :content predictions}]}]
        ;(info " result of predict-class " res)
        res))))

(deftype PredictIrisClass [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args predict-class))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process args predict-class)))

  prot/PParams
  (get-params [_]
    ::params))

(defn new-iris-predictor
  [jobs jobids]
  (PredictIrisClass. jobs jobids))
