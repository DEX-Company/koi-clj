(ns koi.config
  (:require 
   [starfish.core :as s]
   [aero.core :refer (read-config)])
  (:import [org.json.simple JSONObject]
            [org.json.simple.parser JSONParser ParseException]
           ))


(defn get-config
  ([] (get-config (clojure.java.io/resource "config.edn")))
  ([config-resource]
   (read-config config-resource)))

(defn get-remote-agent
  ([] (get-remote-agent (get-config)))
  ([config]
   (let [agent-url (:agent-url config)
         did (s/random-did)
         ddo (s/create-ddo agent-url)
         res 
         {:did did :ddo ddo :remote-agent
          (s/remote-agent did ddo (:username config) (:password config))}]
     res)))
