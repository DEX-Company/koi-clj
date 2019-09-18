(ns koi.examples.hashing-simple
  (:require
   [starfish.core :as s]))

(defn compute-hash
  "Compute the sha3 hash of the asset keyed by to-hash. Returns a asset
  with the hash as the value"
  [{:keys [to-hash] :as m}]
  {:hash-value
   (s/memory-asset {:test :metadata}
                   (-> to-hash s/content s/to-string s/digest))})
