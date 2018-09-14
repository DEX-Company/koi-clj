(defproject koi-clj "0.1.0-SNAPSHOT"
  :description "Notebook service provider "
  ;:url "https://github.com/oceanprotocol/koi"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
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
                 [metosin/compojure-api "2.0.0-alpha19"]

                 ;;components
                 [mount "0.1.12"]

                 ;;crypto
                 [mvxcvi/clj-pgp "0.9.0"]
                 ;;mocking 
                 [ring/ring-mock "0.3.2"]
                 ;;ring utilities
                 [ring "1.6.3"]
                 [metosin/ring-http-response "0.9.0"]
                 [metosin/muuntaja "0.5.0"]
                 [ring-cors "0.1.12"]
                 ]

  :source-paths ["src/"]
  :test-paths ["test/"]

  :profiles {:test {;:jvm-opts ["-Dconfig.edn=resources/test-config.edn"]
                    :dependencies [[ring/ring-mock "0.3.2"]]
                    }
             ;:prod {:jvm-opts ["-Dconfig.edn=resources/prod-config.edn"]}
             :default {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]
                       :plugins [[lein-ring "0.12.0"]]
                       :jvm-opts ["-Dconfig.edn=resources/dev-config.edn"]
                       }
             :dev [:default :test]}
  ;:uberjar-name "server.jar"
  :ring {
         :handler koi.api/app
         :init koi.server/start-server
         }
)
