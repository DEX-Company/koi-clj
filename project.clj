(defproject sg.dex/koi-clj "0.1.6-SNAPSHOT"
  :description "Ocean Invoke API implementation in Clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]

                 ;;configuration
                 [com.outpace/config "0.10.0"]
                 ;;error handling
                 [promenade "0.5.1"]

                 ;;http requests
                 [http-kit "2.2.0"]

                 ;;logging
                 [com.taoensso/timbre "4.10.0"]

                 ;;json
                 [cheshire "5.8.0"]

                 ;;swagger
                 [metosin/compojure-api "2.0.0-alpha28"]

                 ;;components
                 [com.stuartsierra/component "0.4.0"]

                 [ring "1.6.3"]
                 [metosin/ring-http-response "0.9.0"]
                 [metosin/muuntaja "0.5.0"]
                 [ring-cors "0.1.12"]
                 ;;schema tools such as descriptions
                 [metosin/spec-tools "0.8.2"]
                 [mount "0.1.15"]
                 [org.apache.httpcomponents/httpclient "4.5.5" :exclusions [commons-logging]]

                 ;;json schema
                 [metosin/scjsv "0.5.0"]
                 ;;starfish
                 [sg.dex/starfish-clj "0.7.3"]
                 [com.googlecode.json-simple/json-simple "1.1.1"]
                 ;;configuration management
                 [aero "1.1.3"]
                 [ring/ring-mock "0.3.2"]

                 ;;auth
                 [buddy/buddy-auth "2.1.0" :exclusions [org.clojure/clojure org.bouncycastle/bcprov-jdk15on]]

                 ;;csv manipulation
                 [net.mikera/core.matrix "0.58.0"]
                 [org.clojure/data.csv "0.1.4"]

                 ;;interceptors
                 [metosin/sieppari "0.0.0-alpha7"]
                 ]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:output-path "codox"}
  :source-paths ["src/"]
  :test-paths ["test/"]
  :profiles {:test {;:jvm-opts ["-Dconfig.edn=resources/test-config.edn"]
                    :dependencies [[ring/ring-mock "0.3.2"]]
                    }
             :prod {:jvm-opts ["-Dconfig.edn=resources/prod-config.edn"]}
             :default {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]
                       :plugins [[lein-ring "0.12.5"] ]
                       :jvm-opts ["-Dconfig.edn=resources/dev-config.edn"]
                       }
             :dev [:default :test]
             :uberjar{:aot :all 
                      :jvm-opts ["-Dconfig.edn=resources/dev-config.edn"]
                      :main koi.api}}
  :main koi.api
)
