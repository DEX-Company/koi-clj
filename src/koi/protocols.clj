(ns koi.protocols)

(defprotocol PSyncInvoke
  "Protocol for synchronous implementation"
  (invoke-sync [m args]
    "This method invokes the invoke job"))

(defprotocol PAsyncInvoke
  "Protocol for synchronous implementation"
  (invoke-async [m args]
    "This method invokes the invoke job. Should return a job id"))

(defprotocol PValidParams
  "returns a map with keys valid? and description. If valid? is true,
  then the invoke request is delegated to invoke or invoke async"
  (valid-args? [m args]
    "returns a map with keys valid? and description. If valid? is true,
  then the invoke request is delegated to invoke or invoke async"))
