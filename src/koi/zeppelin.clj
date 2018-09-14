(ns koi.zeppelin
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [koi.spec :as ps ]
            [clojure.data.json :as json]
            [org.httpkit.client :as ht]
            [promenade.core :as prom])
  (:import [java.util UUID]))

(defn invoke-job
  "invokes a job"
  ([{:keys [::ps/accountId ::ps/algorithmAssetId] :as job-args}]
   (let [note-uri "https://github.com/hortonworks-gallery/zeppelin-notebooks/raw/master/2C23PDD5H/note.json"
         k (ht/get note-uri)
     resp @(ht/request {:url "http://localhost:8080/api/notebook/import"
                            :body (-> @k :body)
                            :method :post})
         resp @(ht/request {:url (str "http://localhost:8080/api/notebook/job/"
                                      (-> resp :body json/read-str (get "body")))
                            :method :post})])
   (println " invoked job with " job-args)))

(s/fdef invoke-job :args (s/cat :job-args ::ps/invoke-job-keys))
(stest/instrument `invoke-job)

(invoke-job {::ps/algorithmAssetId "89" ::ps/accountId "89"})
