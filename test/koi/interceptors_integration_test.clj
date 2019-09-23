(ns koi.interceptors-integration-test
  (:require [clojure.test :as t
             :refer [deftest is testing use-fixtures]]
            [starfish.core :as s]
            ;;to be removed
            [sieppari.core :as si]
            [koi.utils :refer [resolve-op]]
            [koi.op-handler :as oph]
            [koi.interceptors :as ki]
            [clojure.data.json :as json]
            [koi.config :as cf]
            [clojure.java.io :as io])
  (:import [org.json.simple.parser JSONParser]))

(def remote-agent (atom nil))
(defn my-test-fixture [f]
  (let [agent-conf {:agent-url "http://13.70.20.203:8090"
                    :username "Aladdin"
                    :password "OpenSesame"}]
    (reset! remote-agent
            (:remote-agent (cf/get-remote-agent agent-conf))))
  (f))

(use-fixtures :once my-test-fixture)

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
          upload-fn (ki/asset-reg-upload ragent)
          asset-id (upload-fn test-input-asset )
          ret-fn (partial s/get-asset ragent)
          resp (->> {:to-hash {:did asset-id}}
               ((ki/run-chain
                 [(ki/input-asset-retrieval ret-fn)
                  (ki/output-asset-upload upload-fn)]
                 sha-asset-hash)))]
      (is (map? resp))
      ;;verify that the generated asset is available via the agent
      (is (s/asset? (ret-fn (-> resp :hash-val :did))))))

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
  {:operation-registry
   {:hashing {:handler "koi.interceptors-integration-test/sha-asset-hash"
              :metadata
              {:results {:hash-val {:type "asset", :position 0, :required true}}
               :params {:to-hash {:type "asset", :position 0, :required true}}}}}

   :agent-conf
   {:agent-url "http://13.70.20.203:8090"
    :username "Aladdin"
    :password "OpenSesame"}})

(deftest middleware-config
  (let [test-input-asset (s/asset (s/memory-asset
                                   {"test" "metadata"}
                                   "content"))
        remote-agent (cf/get-remote-agent (:agent-conf config))
        ragent (:remote-agent remote-agent)

        asset-id ((ki/asset-reg-upload ragent) test-input-asset)
        wrapped-handler (ki/middleware-wrapped-handler config)]

    (testing "positive test case"
      (let [op-handler (wrapped-handler :hashing)
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
