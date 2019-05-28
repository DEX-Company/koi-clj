(ns koi.auth-resources.basic-auth-backend
  (:require
   [buddy.auth.backends.httpbasic :refer [http-basic-backend]]))

(def user-db {"Aladdin"
              {:username "Aladdin" :password "OpenSesame"}})

(defn get-user-info
  "The username and email values are stored in-memory for this reference implementation."
  [identifier]
  (let [registered-user (user-db identifier)]
    (when-not (nil? registered-user)
      {:user-data {:identity registered-user} 
       :password (:password registered-user)})))

(defn basic-auth
  "This function will delegate determining if we have the correct username and
   password to authorize a user. The return value will be added to the request
   with the keyword of :identity. We will accept either a valid username or
   valid user email in the username field. It is a little strange but to adhere
   to legacy basic auth api of using username:password we have to make the
   field do double duty."
  [request {:keys [username password]}]
  (let [user-info (get-user-info username)]
    (when user-info 
      (:user-data user-info))))

(def basic-backend
  "Use the basic-auth function defined in this file as the authentication
   function for the http-basic-backend"
  (http-basic-backend {:authfn basic-auth}))

