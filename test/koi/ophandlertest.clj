(ns koi.ophandlertest
  (:require  [clojure.test :as t :refer [is deftest testing]]
             [koi.op-handler :as oph]
             [starfish.core :as s]
             [koi.config :as cf :refer [get-config]]
             [clojure.data.json :as json]))

(def config
  (get-config (clojure.java.io/resource "test-config.edn")))

(deftest get-hashing
  (testing "positive case"
    (let [res ((-> config oph/meta-handler :get-handler) {:route-params {:asset-id "hashing"}})]
      (-> res :body :operation map? is))))

