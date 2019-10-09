(ns koi.apitest
  (:require  [clojure.test :as t :refer [deftest is testing use-fixtures]]
             [starfish.core :as s]
             [cheshire.core :as cheshire]
             [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                                internal-server-error
                                                                not-found
                                                                bad-request]]
             [ring.mock.request :as mock]
             [koi.utils :as utils ]
             [koi.test-utils :as tu :refer [remote-agent remote-agent-map
                                            agent-setup-fixture
                                            load-agent]]
             [koi.interceptors :as ki]
             [clojure.walk :refer [keywordize-keys]]
             [koi.op-handler :as oph :refer [operation-registry ]]
             [koi.protocols :as prot :refer [get-asset put-asset]]
             [koi.api :as api :refer [koi-routes]]
             [com.stuartsierra.component :as component]
             [clojure.data.csv :as csv]
             [clojure.data.json :as json]
             [clojure.zip :as zip]
             [clojure.java.io :as io]
             [koi.config :as config :refer [get-config]]
             [mount.core :as mount])
  (:import [sg.dex.crypto Hash]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(def token (atom 0))

(def iripath "/api/v1/invoke")

(defn get-auth-token
  "get the bearer token and use it for rest of the tests"
  [app]
  (let [response (app
                  (-> (mock/request :post (str iripath "/auth/token"))
                      (mock/header "Authorization" "Basic QWxhZGRpbjpPcGVuU2VzYW1l")))
        body     (parse-body (:body response))]
    (reset! token body)
    (is (= (:status response) (:status (ok))))))

(defn test-fixture [f]
  (let [{:keys [conf remagent]} (load-agent "test-config.edn")]
    (reset! remote-agent
            (:remote-agent remagent))
    (def app (koi-routes conf)))
  (f))

(use-fixtures :once test-fixture)

(deftest testerrorresponses
  (testing "Test request to hash operation"
    (let [input "stringtohash"
          hashval (s/digest input)
          response (app (-> (mock/request :post (str iripath "/sync/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:to-hash input}))))
          body     (parse-body (:body response))
          ]
      (is (= hashval (-> body :results :hash-val)))
      (is (= (:status response) (:status (ok))))))
  (testing "Test unauthorized request to hash operation"
    (let [response (app (-> (mock/request :post (str iripath "/sync/hashing"))
                            (mock/content-type "application/json")
                            ;;no auth header
                            ;(mock/header "Authorization" (str "token faketoken" ))
                            (mock/body (cheshire/generate-string {:to-hash ""}))))
          body     (parse-body (:body response))]
      (is (= (:status response) 401))))
  (testing "Test request to nonexisting operation"
    ;;fakehashing isn't a valid operation did
    (let [response (app (-> (mock/request :post (str iripath "/sync/assetthatdoesntexist"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "abc"}))))]
      (is (= (:status response) (:status (not-found))))))
  (testing "Test bad params to valid operation"
    (let [response (app (-> (mock/request :post (str iripath "/sync/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            ;;hashing needs to-hash as an argument
                            (mock/body (cheshire/generate-string {:abc "def"}))))]
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test non-json inputs to valid operation"
    (let [response (app (-> (mock/request :post (str iripath "/sync/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body "abc")))]
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test failing operation"
    (let [response (app (-> (mock/request :post (str iripath "/sync/fail"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))]
      ;;should this be a bad request or server error (500)
      ;response
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test async hashin operation, get jobid"
    (let [response (app (-> (mock/request :post (str iripath "/async/hashing"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "def"}))))
          jobid     (:jobid (parse-body (:body response)))]
      (-> jobid number? is)))
  #_(testing "Test async hashing operation status"
    (let [response (app (-> (mock/request :post (str iripath "/async/hashing"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "def"}))))
          jobid     (:jobid (parse-body (:body response)))
          _ (try (Thread/sleep 1000)
                 (catch Exception e ))
          jobres (app (-> (mock/request :get (str iripath "/jobs/status/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          job-body (parse-body (:body jobres))
          ]
      (is (= (:status response) (:status (created))))
      (is (= (:status jobres) (:status (ok))))
      (is (= "succeeded" (:status job-body)))))
  (testing "Test async hashing operation combined status and response "
    (let [response (app (-> (mock/request :post (str iripath "/async/hashing"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "def"}))))
          jobid     (:jobid (parse-body (:body response)))
          _ (try (Thread/sleep 1000)
                 (catch Exception e ))
          jobres (app (-> (mock/request :get (str iripath "/jobs/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          job-body (parse-body (:body jobres))
          ]
      (is (= (:status response) (:status (created))))
      (is (= (:status jobres) (:status (ok))))
      (is (= "succeeded" (:status job-body)))
      (is (= #{:status :results} (-> job-body keys set)))
      ))
  #_(testing "Test async hashing operation result"
    (let [response (app (-> (mock/request :post (str iripath "/async/hashing"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "def"}))))
          jobid     (:jobid (parse-body (:body response)))
          _ (try (Thread/sleep 1000)
                 (catch Exception e ))
          jobres (app (-> (mock/request :get (str iripath "/jobs/status/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          ;job-body (parse-body (:body jobres))
          #_jobres2 #_(when (= "succeeded" (:status job-body))
                  (app (-> (mock/request :get (str iripath "/jobs/result/" jobid))
                           (mock/header "Authorization" (str "token " @token))
                           (mock/content-type "application/json"))))
          #_job-body2 #_(parse-body (:body jobres2))]
      jobres
      ;job-body
     ; (-> job-body :results :hash-val string? is)
      ))

  (testing "Test async failing operation"
    (let [response (app (-> (mock/request :post (str iripath "/async/fail"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))

          jobid     (:jobid (parse-body (:body response)))
          _ (try (Thread/sleep 1000) (catch Exception e ))
          jobstat (app (-> (mock/request :get (str iripath "/jobs/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          job-stat-body (parse-body (:body jobstat))
          #_jobres #_(app (-> (mock/request :get (str iripath "/jobs/result/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          #_job-body #_(parse-body (:body jobres))]
      (is (= (:status jobstat) (:status (ok))))
      (is (every? (set (keys (:results job-stat-body))) [:error :errorcode :description]))))
  (testing "Test nonexistent job"
    (let [jobres (app (-> (mock/request :get (str iripath "/jobs/1234" ))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))]
      (is (= (:status jobres) (:status (not-found)))))))

(deftest get-handler-test
  (testing "positive case: existing operation"
    (let [response (app (-> (mock/request :get (str "/api/v1" "/meta/data/primes"))
                            (mock/content-type "application/json")))
          body     (parse-body (:body response))]
      (-> body map? is)
      (let  [ast-hash (-> body json/write-str s/digest)
             resp2
             (app (-> (mock/request :get (str "/api/v1" "/meta/data/" ast-hash))
                      (mock/content-type "application/json")))]
        ;;hash the metadata and use it as asset id
        ;;it should return the same map
        (is (= body (parse-body (:body resp2))))))))

(deftest run-notebook-test
  (testing "positive case: run notebook operation"
    (let [input-asset-name "train.csv"
          notebook-name "test.ipynb"
          result-asset-name "train2.csv"
          ;;upload the input asset
          input-asset (s/asset (s/file-asset (io/file (io/resource input-asset-name))))
          ast1 ((ki/asset-reg-upload (deref remote-agent)) input-asset)

          ;;upload the notebook
          notebook (s/asset (s/file-asset (io/file (io/resource notebook-name))))
          nb1 ((ki/asset-reg-upload (deref remote-agent)) notebook)

          response (app (-> (mock/request :post (str iripath "/sync/run-notebook"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string
                                        {:input-asset {:did ast1}
                                         :notebook {:did nb1}
                                         :notebook-name notebook-name
                                         :input-asset-name input-asset-name
                                         :result-asset-name result-asset-name}))))]
      (-> (parse-body (:body response)) :results :output-file :did string? is))))

(deftest consuming-assets
    (testing "Test request to primes operation"
      (let [response (app (-> (mock/request :post (str iripath "/sync/primes"))
                              (mock/content-type "application/json")
                              (mock/header "Authorization" (str "token " @token))
                              (mock/body (cheshire/generate-string {:first-n "20"}))))
            body     (parse-body (:body response))]
        (is (string? (-> body :results :primes :did)))))
  (testing "Test request to asset hash operation"
    (let [ast (s/asset (s/memory-asset {"hello" "world"} "abc"))
          remid ((ki/asset-reg-upload (deref remote-agent)) ast)
          response (app (-> (mock/request :post (str iripath "/sync/asset-hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:to-hash {:did remid}}))))
          body     (parse-body (:body response))]
      (is (string? (-> body :results :hash-val :did)))))
  (testing "Test request to iris prediction "
          (let [dset (slurp "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv")
                ast (s/memory-asset {"iris" "prediction"} dset)
                remid ((ki/asset-reg-upload (deref remote-agent)) ast)
                response (app (-> (mock/request :post (str iripath "/sync/iris-predictor"))
                                  (mock/content-type "application/json")
                                  (mock/header "Authorization" (str "token " @token))
                                  (mock/body (cheshire/generate-string {:dataset {:did remid}}))))
                body     (parse-body (:body response))
                ret-dset (s/to-string (s/content (s/get-asset (deref remote-agent)
                                                              (-> body :results :predictions :did))))
                dset-rows (clojure.string/split ret-dset #"\n")
                first-row "sepal_length,sepal_width,petal_length,petal_width,species,predclass"]
            ;body
            (is (= first-row (first dset-rows))))))

#_(deftest filterrows
  (testing "Test request to filter rows"
    (let [ifn
          (fn [max-ec]
            (let [dset (slurp "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv")
                  dset (str dset ",,,,,\n,,,,,\n")
                  ast (s/memory-asset {"test" "dataset"} dset)
                  remid ((ki/asset-reg-upload (deref remote-agent)) ast)

                  response (app (-> (mock/request :post (str iripath "/sync/filter-rows"))
                                    (mock/content-type "application/json")
                                    (mock/header "Authorization" (str "token " @token))
                                    (mock/body (cheshire/generate-string {:dataset {:did remid}
                                                                          :max-empty-columns max-ec}))))
                  ;body     (parse-body (:body response))
                  ;ret-dset (s/to-string (s/content (s/get-asset (deref remote-agent) (-> body :results :filtered-dataset :did))))
                 ; dset-rows (clojure.string/split ret-dset #"\n")
                  ]
              ;(count dset-rows)
              ;body
              response
              ))]
      (ifn 1)
      ;;added 2 rows that are empty
      ;;if max-empty-columns is 1, it should remove the 2 rows
      ;;(is (= 151 (ifn 1)))

      ;;if max-empty-columns is 6, it should keep the 2 rows
      ;;(is (= 153 (ifn 6)))
      )))

(deftest prov-retrieval
  (testing "test that provenance is created"
    (let [vpath (io/resource "veh.json")
          wpath (io/resource "workshop.json")
          veh-dset (s/memory-asset {"cars" "dataset"}
                                   (slurp vpath))
          w-dset (s/memory-asset {"workshop" "dataset"}
                                 (slurp wpath))
          upload-fn (ki/asset-reg-upload (deref remote-agent))
          veh-id (upload-fn veh-dset)

          w-id (upload-fn w-dset)

          response (app (-> (mock/request :post (str iripath "/sync/merge-maps"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string
                                        {:vehicle-dataset {:did veh-id}
                                         :workshop-dataset {:did w-id}}))))
          body     (parse-body (:body response))
          _ (println " body returned in prov-retrieval " body)
          resp-did (-> body :results :joined-dataset :did)
          res (s/metadata (s/get-asset (deref remote-agent) resp-did))]
      (is (-> res :provenance nil? not )))))
