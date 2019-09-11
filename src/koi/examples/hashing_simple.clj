(ns koi.examples.hashing-simple
  (:require
   [starfish.core :as s]
   [koi.utils :as utils :refer [keccak512]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [koi.invokespec :as ispec]
   [clojure.java.io :as io]
   [clojure.data.json :as json])
  (:import [sg.dex.crypto Hash]))

(defn compute-hash
  [{:keys [to-hash algorithm] :as m}]
  (println " called compute-hash with " m)
  (let [cont (-> to-hash s/content s/to-string)
        hashval (cond (= algorithm "keccak256")
                      (Hash/keccak256String cont)
                      (= algorithm "keccak512")
                      (keccak512 cont)
                      :default 
                      (Hash/keccak256String cont))
        res {:hash-value
             (s/memory-asset {:test :metadata} hashval)}]
    (info " result of compute-hash " res)
    res))
