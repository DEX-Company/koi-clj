(ns koi.utils
  (:require
   [starfish.core :as s]
   [clojure.walk :refer [keywordize-keys stringify-keys]]
   [koi.protocols :as prot :refer [get-asset put-asset]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.config :as config :refer [get-config get-remote-agent]]
   [mount.core :refer [defstate]]
   [cheshire.core :as ch]
   [clojure.java.io :as io])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(deftype RemoteStorage
    [agent]
  prot/PAssetStorage
  (put-asset
    [_ asset]
    (println " agent is " agent)
    (let [ag (:agent agent)
          remote-asset (s/register ag asset)]
      (s/upload ag asset)
      (s/asset-id remote-asset)))

  (get-asset
    [_ asset-param]
    (->> asset-param
         :did
         (s/get-asset (:agent agent)))))

(defn keccak512
  "returns the keccak512 digest"
  [cont]
  (let [byt (.getBytes cont "UTF-8")
        di (doto (new Keccak$Digest512)
             (.update byt 0 (alength byt)))]
    (Hex/toString (.digest di))))

#_(def prime-metadata 
  (->> (clojure.java.io/resource "prime_asset_metadata.json") slurp))

;;(defstate remote-agent :start (get-remote-agent))


(defn invoke-metadata
  "creates invoke metadata given result parameter name, a list of dependencies (which are Assets),
  and a string representation of input params"
  [remote-agent param-name dependencies params]
  (s/invoke-prov-metadata (.toString (UUID/randomUUID))
                          ;;this is incorrect, it should be koi's did, not surfer's. 
                          (.toString (:did remote-agent))
                          dependencies
                          params 
                          param-name))

(defn process
  "this takes a map of input arguments, and a function to execute which returns a map with 2 keys: a
  list of Asset dependencies and results, which is a map of result params names and the value is content.

  It executes the function to compute the results, creates provenance metadata , registers the asset(s)
  and uploads the contents"
  [remote-agent storage params execfn]
  (let [agent (:agent remote-agent)
        _ (println " process-fn storage " storage " agent " agent)
        to-exec (execfn storage params)
        {:keys [dependencies results]} (to-exec)
        res (->> results
                 ;(filter (fn[{:keys [type]}] (= :asset type)))
                 (mapv (fn[{:keys [param-name content type
                                   metadata] :or {metadata {}} :as c}]
                         (if (= type :asset)
                           (let [inv-metadata (invoke-metadata
                                               remote-agent
                                               (name param-name) dependencies (JSON/toString params))
                                 asset (s/asset (s/memory-asset (merge metadata
                                                                       inv-metadata)
                                                                content))
                                 reg-asset-id (put-asset storage asset)]
                             {param-name {:did
                                          ;;(str (:did remote-agent) "/" reg-asset-id)
                                          ;;when the caller uses universal resolver, put this back
                                          ;;else the caller cannot find the asset thanks to non-unique DIDs for
                                          ;;Surfer
                                          reg-asset-id}})
                           {param-name content})))
                 (apply merge))]
    {:results res }))

(defn async-handler
  [jobids jobs exec-fn]
  (let [jobid (swap! jobids inc)]
    (doto (Thread.
           (fn []
             (swap! jobs assoc jobid {:status :accepted})
             (try (let [res (exec-fn)]
                    (swap! jobs assoc jobid
                           {:status :succeeded
                            :results (:results res)}))
                  (catch Exception e
                    (error " Caught exception running async job " (.getMessage e))
                    (swap! jobs assoc jobid
                           {:status :error
                            :errorcode 8005
                            :description (str "Got exception " (.getMessage e))})))))
      .start)
    {:jobid jobid}))

(defn resolve-op
  "Takes an string representation of a var and returns it as a function.
  E.g. ((resolve-op \"koi.examples.hashing-simple/compute-hash\")
         {:to-hash (s/memory-asset {:test :metadata} \"content\")})
  "
  [fstr]
  (-> fstr symbol resolve))
