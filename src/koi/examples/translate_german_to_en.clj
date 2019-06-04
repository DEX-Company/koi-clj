(ns koi.examples.translate-german-to-en
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [koi.invokespec :as ispec]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [org.httpkit.client :as http]
   [koi.utils :as utils :refer [put-asset get-asset-content get-asset remote-agent keccak512
                                async-handler
                                process]])
  (:import [sg.dex.crypto Hash]
           [java.util UUID]
           [sg.dex.starfish.util Hex JSON]
           [org.bouncycastle.jcajce.provider.digest Keccak$Digest512 ]))

(sp/def ::dataset ::ispec/asset)
(sp/def ::params (sp/keys :req-un [::dataset]))

(defn translate
  [content]
  (let [resp @(http/request {:url "http://localhost:18001/translator/translate"
                             :method :post
                             :body (->> content json/write-str)})]
    (if (= 200 (-> resp :status ))
      (clojure.string/join "\r\n" (json/read-str (:body resp)) )
      (throw (Exception. "error running translate ")))))

(defn translate-dataset
  [agent {:keys [dataset]}]
  (let [ast (get-asset agent dataset)
        cont (-> ast s/content s/to-string)]
    (fn []
      (let [translation (translate cont)  
            res {:dependencies [ast]
                 :results [{:param-name :translations
                            :type :asset
                            :content translation}]}]
        res))))

(deftype TranslateClass [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args translate-dataset))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process args translate-dataset)))

  prot/PParams
  (get-params [_]
    ::params))

(defn new-german-en-translator
  [jobs jobids]
  (TranslateClass. jobs jobids))
