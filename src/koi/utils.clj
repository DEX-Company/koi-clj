(ns koi.utils
  (:require
   [starfish.core :as s]
   [clojure.walk :refer [keywordize-keys stringify-keys]]
   [koi.config :as config :refer [get-config default-surfer]]
   [mount.core :refer [defstate]]
   [cheshire.core :as ch]
   [clojure.java.io :as io])
  (:import [sg.dex.crypto Hash]
           [sg.dex.starfish.util Hex]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

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

(defn keccak512
  "returns the keccak512 digest"
  [cont]
  (let [byt (.getBytes cont "UTF-8")
        di (doto (new Keccak$Digest512)
             (.update byt 0 (alength byt)))]
    (Hex/toString (.digest di))))

(def prime-metadata 
  (->> (clojure.java.io/resource "prime_asset_metadata.json") slurp))

(defstate surfer :start (default-surfer))
