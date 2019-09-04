(ns koi.examples.hashing-asset
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.protocols :as prot 
    :refer [invoke-sync invoke-async valid-args?
            get-asset]]
   [koi.invokespec :as ispec]
   [clojure.java.io :as io]
   [koi.utils :as utils :refer [keccak512 process async-handler]]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::to-hash ::ispec/asset)


(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash {:did "1234567890123456789012345678901234567890123456789012345678901234"}})


(defn compute-hash
  [storage {:keys [to-hash algorithm]}]
  (let [ast (get-asset storage to-hash)
        cont (-> ast s/content s/to-string)]
    (fn []
      (let [hashval (cond (= algorithm "keccak256")
                      (Hash/keccak256String cont)
                      (= algorithm "keccak512")
                      (keccak512 cont)
                      :default 
                      (Hash/keccak256String cont))
            res {:dependencies [ast]
                 :results [{:param-name :hash-value
                            :type :asset
                            :content hashval}]}]
        (info " result of execfn " res)
        res))))

(deftype HashingAsset [agent storage jobs jobids]
  :load-ns true
  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process agent storage args compute-hash))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process agent storage args compute-hash)))

  prot/PValidParams
  (valid-args? [_ args]
    {:valid? (sp/valid? ::params args)})
  )
