(ns koi.zeppelin
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [koi.spec :as ps ]
            [promenade.core :as prom])
  (:import [java.util UUID]))

(defn invoke-job
  "invokes a job"
  ([{:keys [::ps/accountId ::ps/algorithmAssetId] :as job-args}]
   (println " invoked job with " job-args)))

(s/fdef invoke-job :args (s/cat :job-args ::ps/invoke-job-keys))
(stest/instrument `invoke-job)

(invoke-job {::ps/algorithmAssetId "89" ::ps/accountId "89"})
