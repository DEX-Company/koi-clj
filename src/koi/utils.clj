(ns koi.utils
  (:require
   [starfish.core :as s]
   [clojure.walk :refer [keywordize-keys stringify-keys]]
   [koi.config :as config :refer [get-config default-surfer]]
   [mount.core :refer [defstate]]
   [cheshire.core :as ch]
   [clojure.java.io :as io]))

(defn register-asset
  ([surfer content]
   (let [a1 (s/asset (s/memory-asset content))
         remote-asset (s/register surfer a1)
         _ (s/upload surfer a1)]
     (s/asset-id remote-asset))))

(defn get-asset-content
  ([surfer did]
   (->> did
        (s/get-asset surfer)
        (s/content)
        s/to-string)))

(def prime-metadata 
  (->> (clojure.java.io/resource "prime_asset_metadata.json") slurp))

(defstate surfer :start (default-surfer))
