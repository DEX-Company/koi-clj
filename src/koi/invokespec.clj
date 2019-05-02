(ns koi.invokespec
  (:require
   [clojure.spec.alpha :as sp]))

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




