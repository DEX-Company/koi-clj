(ns koi.protocols)

(defprotocol PSyncInvoke
  "Protocol for synchronous implementation"
  (invoke-sync [m args]
    "This method invokes the invoke job"))

(defprotocol PAsyncInvoke
  "Protocol for synchronous implementation"
  (invoke-async [m args]
    "This method invokes the invoke job. Should return a job id"))

(defprotocol PParams
  "returns metadata relevant to a service"
  (get-params [m]
    "returns the params for validation"))
