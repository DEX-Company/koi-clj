(ns koi.utils
  (:require
   [starfish.core :as s]
   [aero.core :refer (read-config)]
   [clojure.java.io :as io]))

(def config (read-config (clojure.java.io/resource "config.edn")))
(def surfer-url (:surfer-url config))

(def surfer (s/surfer surfer-url (s/remote-account (:username config) (:password config))))

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
