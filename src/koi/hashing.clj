(ns koi.hashing
  (:require
   [clojure.spec.alpha :as sp]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [clojure.java.io :as io]
   [spec-tools.json-schema :as jsc]))

(sp/def ::did (sp/and string? #(= 64 (count %))))
(sp/valid? ::did "1234567890123456789012345678901234567890123456789012345678901234")
(sp/def ::purchase-token string?)
(sp/def ::asset (sp/keys :req-un [::did]
                         :opt-un [::purchase-token]) )
(sp/valid? ::asset
           {:did "1234567890123456789012345678901234567890123456789012345678901234"
            :purchase-token "1234567890123456789012345678901234567890123456789012345678901234"})

(sp/def ::string string?)
(sp/def ::type #{::asset ::string})
(sp/def ::position int?)
(sp/def ::required boolean?)

(sp/def ::param-val (sp/keys :req-un [::type]
                             :opt-un [::position ::required]))
(sp/valid? ::param-val {:type ::asset})
(sp/valid? ::param-val {:type ::string})
(sp/valid? ::param-val {:type ::asset
                        :position 0
                        :required true
                        })
(sp/valid? ::param-val {:type ::string
                        :position 0
                        :required true
                        })

(sp/def ::to-hash ::param-val)
(sp/def ::params-def (sp/keys :req-un [::to-hash]))

(sp/valid? ::params-def {:to-hash {:type ::string
                                :position 0
                                :required true
                                }})
(sp/def ::to-hash string?)

(sp/def ::params (sp/keys :req-un [::to-hash]))
(sp/valid? ::params {:to-hash "abc"})
(sp/valid? ::params {:to-hash 1})

(deftype Hashing [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [to-hash (:to-hash args)]
      (println " called hashing with " to-hash)
      (str (hash to-hash))))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [to-hash (:to-hash args)
          jobid (swap! jobids inc)]
      (swap! jobs assoc jobid {:status :completed
                               :results {:hash_value (str (hash to-hash))}})
      jobid))
  
  prot/PParams
  (get-params [_]
    ::params))

(defn new-hashing
  [jobs jobids]
  (Hashing. jobs jobids))
