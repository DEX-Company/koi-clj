(ns koi.config
  (:require [outpace.config :refer [defconfig]]))

(defconfig ^{:validate [map? "Must be a map"]}
  connection)

(def notebook-invoke "/api/v1/notebookinvoke")
