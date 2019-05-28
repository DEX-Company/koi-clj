(ns koi.middleware.basic-auth
  (:require
   [buddy.auth.middleware :refer [wrap-authentication]]
   [koi.auth-resources.basic-auth-backend :refer [basic-backend]]))

(defn basic-auth-mw
  "Middleware used on all routes requiring basic authentication"
  [handler]
  (wrap-authentication handler basic-backend))

