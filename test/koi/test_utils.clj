(ns koi.test-utils
  (:require [clojure.test :as t
             :refer [deftest is testing use-fixtures]]
            [starfish.core :as s]
            [sieppari.core :as si]
            [koi.utils :refer [resolve-op]]
            [koi.examples.simple :refer [sha-raw-hash sha-asset-hash
                                         fail]]
            [koi.op-handler :as oph]
            [koi.interceptors :as ki]
            [clojure.data.json :as json]
            [koi.config :as cf :refer [get-config]]
            [clojure.java.io :as io])
  (:import [org.json.simple.parser JSONParser]))

(def remote-agent (atom nil))
(def remote-agent-map (atom nil))

(defn load-agent
  [config-resource]
  (let [conf (get-config (clojure.java.io/resource config-resource))
        agent-conf (:agent-conf conf)
        remagent (cf/get-remote-agent agent-conf)]
    {:conf conf :remagent remagent}))

(defn agent-setup-fixture [f]
  (let [{:keys [conf remagent]}
        (load-agent "test-config.edn")]
    (reset! remote-agent-map
            remagent)
    (reset! remote-agent
            (:remote-agent remagent)))
  (f))
