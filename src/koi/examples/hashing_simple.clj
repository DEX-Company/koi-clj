(ns koi.examples.hashing-simple
  (:require
   [starfish.core :as s]))

(defn sha-raw-hash
  "accepts a JSON object input against the to-hash key, and returns the hash value as a string"
  [{:keys [to-hash]}]
  {:hash-val (s/digest to-hash)})

(defn sha-asset-hash
  "accepts a starfish asset against the to-hash key, and returns a starfish asset as the value
  against the hash-val key"
  [{:keys [to-hash]}]
  {:hash-val (s/asset (s/memory-asset {"meta" "data"}
                                      (-> to-hash s/content s/to-string s/digest)))})

(defn fail
  "a test function that always throws an error"
  [{:keys [dummy]}]
  (throw (Exception. "test exception")))
