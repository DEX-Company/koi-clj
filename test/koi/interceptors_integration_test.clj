(ns koi.interceptors-integration-test
  (:require [clojure.test :as t
             :refer [deftest is testing use-fixtures]]
            [starfish.core :as s]
            [sieppari.core :as si]
            [koi.utils :refer [resolve-op]]
            [koi.examples.simple :refer [sha-raw-hash sha-asset-hash
                                         fail]]
            [koi.op-handler :as oph]
            [koi.interceptors :as ki]
            [koi.test-utils :as tu :refer [remote-agent remote-agent-map
                                           agent-setup-fixture
                                           ]]
            [clojure.data.json :as json]
            [koi.config :as cf :refer [get-config]]
            [clojure.java.io :as io])
  (:import [org.json.simple.parser JSONParser]))

(use-fixtures :once agent-setup-fixture)

(deftest input-asset-retrieval-test
  (testing "positive case "
    (let [ragent (deref remote-agent) 
          ;;lets pre-register an asset that will be used
          test-input-asset (s/asset (s/memory-asset
                                     "content"))
          asset-id (do (s/register ragent test-input-asset)
                       (s/asset-id (s/upload ragent test-input-asset)))
          ret-fn (partial s/get-asset ragent)]
      (->> {:to-hash {:did asset-id}}
           ((ki/run-chain
             [(ki/input-asset-retrieval ret-fn)]
             sha-asset-hash))
           :hash-val
           s/asset
           is)))

  (testing "retrieve an asset that doesn't exist"
    (let [ragent (deref remote-agent) 
          ret-fn (partial s/get-asset ragent)]
      ;;use a non-existent asset
      (->> {:to-hash {:did "abcd"}}
           ((ki/run-chain
             [(ki/input-asset-retrieval ret-fn)]
             sha-asset-hash))
           :error
           map?
           is))))

(deftest asset-upload-test
  (testing "positive case"
    (let [ragent (deref remote-agent)
          ;;lets pre-register an asset that will be used
          test-input-asset (s/asset (s/memory-asset
                                     {"test" "metadata"}
                                     "content"))
          asset-id ((ki/asset-reg-upload ragent) test-input-asset)
          ret-fn (partial s/get-asset ragent)
          resp (->> {:to-hash {:did asset-id}}
               ((ki/run-chain
                 [(ki/output-asset-upload (ki/add-prov (deref remote-agent-map)))
                  (ki/input-asset-retrieval ret-fn)]
                 sha-asset-hash)))
          generated-asset(ret-fn (-> resp :hash-val :did))]
      ;resp
      (is (map? resp))
      ;;verify that the generated asset is available via the agent
      ;(println " metadata of returned asset " (s/metadata generated-asset))
      (is (s/asset? generated-asset))))

  (testing "upload-fn fail case"
    (let [ragent (deref remote-agent)
          ;;lets pre-register an asset that will be used
          test-input-asset (s/asset (s/memory-asset
                                     {"test" "metadata"}
                                     "content"))
          upload-fn (fn[ast]
                      (throw (Exception. "could not upload")))
          asset-id ((ki/asset-reg-upload ragent) test-input-asset )
          ret-fn (partial s/get-asset ragent)
          resp (->> {:to-hash {:did asset-id}}
                    ((ki/run-chain
                      [(ki/input-asset-retrieval ret-fn)
                       (ki/output-asset-upload upload-fn)]
                      sha-asset-hash)))]
     (-> resp :error map? is))))

;;run the same as above, but load the entire configuration from a single map
(def config
  (get-config (clojure.java.io/resource "test-config.edn")))

(deftest middleware-config
  (let [test-input-asset (s/asset (s/memory-asset
                                   {"test" "metadata"}
                                   "content"))
        remote-agent (cf/get-remote-agent (:agent-conf config))
        ragent (:remote-agent remote-agent)

        asset-id ((ki/asset-reg-upload ragent) test-input-asset)
        wrapped-handler (ki/middleware-wrapped-handler config)]

    (testing "positive test case"
      (let [op-handler (wrapped-handler :asset-hashing)
            resp (->> (op-handler {:to-hash {:did asset-id}}))
            did (-> resp :results :hash-val :did)]
        resp
        (is (string? did))
        (->> (s/get-asset ragent did) s/asset? is)))))

(comment 
  (let [test-input-asset (s/asset (s/memory-asset
                                   {"test" "metadata"}
                                   "content"))
        remote-agent (cf/get-remote-agent (:agent-conf config))
        ragent (:remote-agent remote-agent)

        asset-id ((ki/asset-reg-upload ragent) test-input-asset)
        wrapped-handler (ki/middleware-wrapped-handler config)
        ]
    ((oph/invoke-handler wrapped-handler)
     {:body-params {:to-hash {:did asset-id}}
      :route-params {:did :hashing}})))
