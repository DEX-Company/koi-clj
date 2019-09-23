(ns koi.interceptors-test
  (:require [clojure.test :as t
             :refer [deftest is testing use-fixtures]]
            [starfish.core :as s]
            ;;to be removed
            [sieppari.core :as si]
            [koi.utils :refer [resolve-op]]
            [koi.examples.simple :refer [sha-raw-hash sha-asset-hash
                                         fail]]
            [koi.op-handler :as oph]
            [koi.interceptors :as ki]
            [clojure.data.json :as json]
            [koi.config :as cf]
            [clojure.java.io :as io])
  (:import [org.json.simple.parser JSONParser]))

;(defn my-test-fixture [f] (f))

;(use-fixtures :once my-test-fixture)

#_(defn sha-raw-hash
  "accepts a JSON object input against the to-hash key, and returns the hash value as a string"
  [{:keys [to-hash]}]
  {:hash-val (s/digest to-hash)})

#_(defn sha-asset-hash
  "accepts a starfish asset against the to-hash key, and returns a starfish asset as the value
  against the hash-val key"
  [{:keys [to-hash]}]
  {:hash-val (s/asset (s/memory-asset {"meta" "data"}
                                      (-> to-hash s/content s/to-string s/digest)))})

(deftest direct-invocation
  (testing "create-var"
    (is (:hash-val (sha-raw-hash {:to-hash "abc"})))))

(deftest string-invocation
  (testing "raw"
    (is (contains?
         ((resolve-op "koi.examples.simple/sha-raw-hash")
          {:to-hash "abc"})
         :hash-val))))

(deftest param-validator-test
  (testing "simple"
    (let [param-spec {:to-hash {:type "asset", :position 0, :required true}}
          asset (s/memory-asset {:a :b} "content")
          retrieval-fn {"asset1234" asset}]
      (->> {:to-hash {:did "asset1234"}}
           ((ki/run-chain
             [(ki/param-validator param-spec)
              ;;need this to translate the did into a Starfish asset
              (ki/input-asset-retrieval retrieval-fn)]
             sha-asset-hash))
           vals first s/asset? is)))
  (testing "failure case: invalid value for to-hash"
    (let [param-spec {:to-hash {:type "asset", :position 0, :required true}}]
      (is (thrown? Exception (->> {:to-hash "abcd"}
                                  ((ki/run-chain
                                    [(ki/param-validator param-spec)]
                                    sha-asset-hash))
                                  )))))
  (testing "failure case: invalid key instead of to-hash"
    (let [param-spec {:to-hash {:type "asset", :position 0, :required true}}]
      (->> {:abc "abcd"}
           ((ki/run-chain
             [(ki/param-validator param-spec)]
             sha-asset-hash))
           (thrown? Exception)
           is)))
  )

(deftest result-validator-test
  (testing "positive case "
    (let [param-spec {:hash-val {:type "asset", :position 0, :required true}}]
      (->> {:to-hash (s/memory-asset {:a :b} "content")}
           ((ki/run-chain
             [(ki/result-validator param-spec)]
             sha-asset-hash))
           :hash-val
           s/asset?
           is)))

  (testing "failure case case "
    (let [param-spec {:hash-valxxxx {:type "asset", :position 0, :required true}}]
      (->> {:to-hash (s/memory-asset {:a :b} "content")}
           ((ki/run-chain
             [(ki/result-validator param-spec)]
             sha-asset-hash))
           :error
           map?
           is))))

(deftest local-asset-retrieval
  (testing "positive case "
    (let [ret-fn {"did:op:1234/4567" (s/memory-asset
                                      {:meta :data}
                                      "content")}
          res 
          (->> {:to-hash {:did "did:op:1234/4567"}}
               ((ki/run-chain
                 [(ki/input-asset-retrieval ret-fn)]
                 sha-asset-hash)))]
      (is (-> res :hash-val s/asset?))))
  (testing "input asset not found "
    (let [ret-fn {"did:op:1234/4567" (s/memory-asset
                                      {:meta :data}
                                      "content")}
          res 
          (->> {:to-hash {:did "did:op:1234/abcd"}}
               ((ki/run-chain
                 [(ki/input-asset-retrieval ret-fn)]
                 sha-asset-hash)))]
      (-> res :error map? is))))
