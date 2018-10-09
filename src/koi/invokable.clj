(ns koi.invokable)

(defprotocol Invokable
  "a protocol for invokable services"
  (invoke [this arg-map])
  (get-status [this job-id])
  (get-result [this job-id])
  (get-proof [this job-id]))
