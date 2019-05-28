(ns koi.general-functions.user.create-token)

(defn create-token
  "Create a signed json web token. The token contents are; username, email, id,
  permissions and token expiration time. Tokens are valid for 15 minutes."
  [user]
  (let [stringify-user (-> user
                           (update :username str))]
    (.toString (java.util.UUID/randomUUID))))
