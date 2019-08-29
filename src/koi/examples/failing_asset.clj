(ns koi.examples.failing-asset
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            valid-args?]]
   [clojure.java.io :as io]
   [koi.utils :as utils :refer [put-asset get-asset-content get-asset keccak512
                                async-handler
                                process]]
   [koi.invokespec :as ispec]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc]))

(sp/def ::dummy string?)
(sp/def ::params (sp/keys :req-un [::dummy]))

(defn run-method
  [agent {:keys [dummy]}]
  (throw (Exception. "test exception")))

(deftype FailingAsset [agent jobs jobids]
  :load-ns true

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process agent args run-method))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process agent args run-method)))

  prot/PValidParams
  (valid-args? [_ args]
    {:valid? (sp/valid? ::params args)})
  )

(defn new-failing
  [agent jobs jobids]
  (FailingAsset. agent jobs jobids))
