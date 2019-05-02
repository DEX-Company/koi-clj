(ns koi.config
  (:require 
   [koi.hashing :as h]
   [koi.hashing-asset :as ha]
   [mount.core :refer [defstate]]))

(def jobids (atom 0))
(def jobs (atom {}))

(defn default-service-registry
  []
  {:hashing (h/new-hashing jobs jobids)
   :assethashing (ha/new-hashing jobs jobids)})

(defstate ^:dynamic service-registry :start (default-service-registry))
