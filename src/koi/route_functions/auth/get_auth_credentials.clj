(ns koi.route-functions.auth.get-auth-credentials
  (:require
    [ring.util.http-response :as respond]
    [koi.general-functions.user.create-token :refer [create-token]]))

;;store the generated tokens in an in-memory token store
(def generated-tokens (atom {}))

(defn auth-credentials-response
  "Route requires basic authentication and will generate a new
   token."
  [request]
  (let [user (:identity request)
        new-token (create-token user)]
    (swap! generated-tokens assoc (:username user) new-token)
    (respond/ok new-token)))
