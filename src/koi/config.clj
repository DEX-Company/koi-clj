(ns koi.config
  (:require 
   [starfish.core :as s]
   [aero.core :refer (read-config)]
   ))


(defn get-config
  ([] (get-config (clojure.java.io/resource "config.edn")))
  ([config-resource]
   (read-config config-resource)))

(defn default-surfer
  ([] (default-surfer (get-config)))
  ([config]
   (let [surfer-url (:surfer-url config)
         did (s/random-did)]
     (s/remote-agent did (s/default-ddo surfer-url)
                     (:username config) (:password config)))))

