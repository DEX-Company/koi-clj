(ns koi.main
  (:require
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report]]
            [taoensso.timbre.appenders.core :as appenders]
            [outpace.config :refer [defconfig]])
  (:gen-class)
  )

(defconfig ^:required logfile-path)
(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender
                     {:fname logfile-path})}})

(defn -main 
  [args]
  (info (str "starting server, appending logs to " logfile-path))
  ;(start-server)
  )

