(ns koi.server
  (:require  [clojure.test :as t]
             [taoensso.timbre.appenders.core :as appenders]
             [clojure.data.json :as json :refer [read-str]]
             [taoensso.timbre :as timbre
              :refer [log  trace  debug  info  warn  error  fatal  report]]
             [outpace.config :refer [defconfig]]
             [promenade.core :as prom :refer [failure?]])
  (:import [java.util UUID]))

;;logfile set by config file
(defconfig ^:required logfile-path)

;;configure appender
(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender
                     {:fname logfile-path})}})

(defn start-server
  "start the server and initialize any prerequisites"
  []
  ;;create the default marketplace
  (info (str "starting server, appending logs to " logfile-path))
)



