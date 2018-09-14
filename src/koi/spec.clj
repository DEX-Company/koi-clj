(ns koi.spec
  (:require 
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   ))

(s/def ::assetId string?)
(s/def ::providerId string?)
(s/def ::consumerId string?)
(s/def ::name string?)

(s/def ::accountId string?)
(s/def ::algorithmAssetId string?)
(s/def ::invoke-job-keys (s/keys :req [::accountId ::algorithmAssetId]))
