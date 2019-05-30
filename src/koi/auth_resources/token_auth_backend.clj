(ns koi.auth-resources.token-auth-backend
  (:require
   [koi.route-functions.auth.get-auth-credentials :refer [generated-tokens]]
   [buddy.auth.backends.token :as btoken]
   [buddy.auth.backends :refer [token]]

   ))

(def token-backend
  "Tests if the token exists in the token store. If token is valid the decoded
  contents of the token will be added to the request with the keyword of
  `:identity`"
  (btoken/token-backend {
                         :token-name "token"
                         :authfn (fn[k cur-token ]
                                   (let [valid-tokens (-> generated-tokens deref vals set)
                                         resp (valid-tokens cur-token)]
                                     (println " received token " cur-token)
                                     ;resp
                                     cur-token
                                     ))}))
