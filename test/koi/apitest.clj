(ns koi.apitest
  (:require  [clojure.test :as t :refer [deftest is testing use-fixtures]]
             [starfish.core :as s]
             [cheshire.core :as cheshire]
             [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                                internal-server-error
                                                                not-found
                                                                bad-request]]
             [ring.mock.request :as mock]
             [koi.utils :as utils :refer [put-asset get-asset-content remote-agent keccak512]]
             [clojure.walk :refer [keywordize-keys]]
             [koi.op-handler :as oph :refer [service-registry ]]
             [koi.api :as api :refer [app]]
             [clojure.data.csv :as csv]
             [clojure.data.json :as json]
             [clojure.zip :as zip]
             [clojure.java.io :as io]
             [mount.core :as mount])
  (:import [sg.dex.crypto Hash]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(def token (atom 0))

(def iripath "/api/v1")
(defn get-auth-token
  "get the bearer token and use it for rest of the tests"
  []
  (let [response (app (-> (mock/request :post (str iripath "/auth/token"))
                          (mock/header "Authorization" "Basic QWxhZGRpbjpPcGVuU2VzYW1l")))
        body     (parse-body (:body response))]
    (reset! token body)
    (is (= (:status response) (:status (ok))))))

(defn my-test-fixture [f]
  (mount/start)
  (get-auth-token)
  (f)
  (mount/stop))

(use-fixtures :once my-test-fixture)

(deftest testerrorresponses
  (testing "Test request to hash operation"
    (let [input "stringtohash"
          hashval (Hash/keccak256String input)
          response (app (-> (mock/request :post (str iripath "/invoke/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:to-hash input}))))
          body     (parse-body (:body response))]
      (is (= hashval (-> body :results :keccak256)))
      (is (every? #{:keccak256 :keccak512} (keys (:results body))))
      (is (= (:status response) (:status (ok))))))
  (testing "Test unauthorized request to hash operation"
    (let [response (app (-> (mock/request :post (str iripath "/invoke/hashing"))
                            (mock/content-type "application/json")
                            ;(mock/header "Authorization" (str "token faketoken" ))
                            (mock/body (cheshire/generate-string {:to-hash ""}))))
          body     (parse-body (:body response))]
      (is (= (:status response) 401))))
  (testing "Test request to nonexisting operation"
    ;;fakehashing isn't a valid operation did
    (let [response (app (-> (mock/request :post (str iripath "/invoke/assetthatdoesntexist"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "abc"}))))]
      (is (= (:status response) (:status (not-found))))))
  (testing "Test bad params to valid operation"
    (let [response (app (-> (mock/request :post (str iripath "/invoke/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            ;;hashing needs to-hash as an argument
                            (mock/body (cheshire/generate-string {:abc "def"}))))]
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test non-json inputs to valid operation"
    (let [response (app (-> (mock/request :post (str iripath "/invoke/hashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body "abc")))]
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test failing operation"
    (let [response (app (-> (mock/request :post (str iripath "/invoke/fail"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))]
      (is (= (:status response) (:status (internal-server-error))))))
  (testing "Test async failing operation"
    (let [response (app (-> (mock/request :post (str iripath "/invokeasync/fail"))
                            (mock/header "Authorization" (str "token " @token))
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))
          jobid     (:jobid (parse-body (:body response)))
          jobres (app (-> (mock/request :get (str iripath "/jobs/" jobid))
                          (mock/header "Authorization" (str "token " @token))
                          (mock/content-type "application/json")))
          job-body (parse-body (:body jobres))]
      (is (= (:status response) (:status (created))))
      (is (= (:status jobres) (:status (ok))))
      (is (every? #{:status :errorcode :description} (keys job-body))))))

(deftest consuming-assets
  (testing "Test request to asset hash operation"
    (let [ast (s/memory-asset {"hello" "world"} "abc")
          remid (put-asset (:agent remote-agent) ast)
          response (app (-> (mock/request :post (str iripath "/invoke/assethashing"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:to-hash {:did remid}
                                                                  :algorithm "keccak256"}))))
          body     (parse-body (:body response))]
      (is (string? (-> body :results :hash-value :did)))))

  (testing "Test request to primes operation"
    (let [response (app (-> (mock/request :post (str iripath "/invoke/primes"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:first-n "20"}))))
          body     (parse-body (:body response))]
      (is (string? (-> body :results :primes :did)))))
  (testing "Test request to iris prediction "
    (let [dset (slurp "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv")
          ast (s/memory-asset {"iris" "prediction"} dset)
          remid (put-asset (:agent remote-agent) ast)

          response (app (-> (mock/request :post (str iripath "/invoke/irisprediction"))
                            (mock/content-type "application/json")
                            (mock/header "Authorization" (str "token " @token))
                            (mock/body (cheshire/generate-string {:dataset {:did remid}}))))
          body     (parse-body (:body response))
          ret-dset (s/to-string (s/content (s/get-asset (:agent remote-agent) (-> body :results :predictions :did))))
          dset-rows (clojure.string/split ret-dset #"\n")
          first-row "sepal_length,sepal_width,petal_length,petal_width,species,predclass"]
      (is (= first-row (first dset-rows))))))

(deftest oper-registration
  (testing "primes operation "
    (do 
        (let [prime-metadata (->> (clojure.java.io/resource "prime_asset_metadata.json")
                                  slurp
                                  cheshire/parse-string)
              ast (s/memory-asset prime-metadata "abc")
              remote-asset (s/register (:agent remote-agent) ast)
              res (s/asset-id remote-asset)
              rem-metadata (s/metadata remote-asset)]
          (is (=  (s/asset-id ast) res))))))

(deftest filterrows 
  (testing "Test request to filter rows"
    (let [ifn (fn [max-ec](let [dset (slurp "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv")
                      dset (str dset ",,,,,\n,,,,,\n")
                      ast (s/memory-asset {"test" "dataset"} dset)
                      remid (put-asset (:agent remote-agent) ast)

                      response (app (-> (mock/request :post (str iripath "/invoke/filter-rows"))
                                        (mock/content-type "application/json")
                                        (mock/header "Authorization" (str "token " @token))
                                        (mock/body (cheshire/generate-string {:dataset {:did remid}
                                                                              :max-empty-columns max-ec}))))
                      body     (parse-body (:body response))
                      ret-dset (s/to-string (s/content (s/get-asset (:agent remote-agent) (-> body :results :filtered-dataset :did))))
                      dset-rows (clojure.string/split ret-dset #"\n")]
                            (count dset-rows)))]
      ;;added 2 rows that are empty
      ;;if max-empty-columns is 1, it should remove the 2 rows
      (is (= 151 (ifn 1)))

      ;;if max-empty-columns is 6, it should keep the 2 rows
      (is (= 153 (ifn 6)))
      )))

(deftest prov-retrieval
  (testing "retrieval"
    (let [vpath (io/resource "veh.json")
              wpath (io/resource "workshop.json")
          veh-dset (s/memory-asset {"cars" "dataset"}
                                   (slurp vpath))
              w-dset (s/memory-asset {"workshop" "dataset"}
                                     (slurp wpath))
              veh-id (put-asset (:agent remote-agent) veh-dset)
              w-id (put-asset (:agent remote-agent) w-dset)

              response (app (-> (mock/request :post (str iripath "/invoke/workshop-join-cars"))
                                (mock/content-type "application/json")
                                (mock/header "Authorization" (str "token " @token))
                                (mock/body (cheshire/generate-string
                                            {:vehicle-dataset {:did veh-id}
                                             :workshop-dataset {:did w-id}}))))
              body     (parse-body (:body response))
              resp-did (-> body :results :joined-dataset :did) 
              res (s/metadata (s/get-asset (:agent remote-agent) resp-did))
              response2 (app (-> (mock/request :post (str iripath "/invoke/prov"))
                                (mock/content-type "application/json")
                                (mock/header "Authorization" (str "token " @token))
                                (mock/body (cheshire/generate-string
                                            {:asset {:did resp-did}}))))
          body     (parse-body (:body response2))]
      (is (not (nil? (-> body :results :prov-tree))))
      (is (not (nil? ((json/read-str (-> body :results :prov-tree))
                      "derived-from")))))))

(comment 
  (let [k2 (-> k2json keywordize-keys)
        agent-id (-> k2 :agent keys first)
        df (mapv (fn[imap]
                   (merge (select-keys imap [:asset-id])
                          {:type "asset"}
                          (select-keys (get-in imap [:metadata]) [:name :description :content-hash])))
                 (:derived-from k2))
        df1 (conj df {:asset-id (-> k2 :agent keys first) :type "operation"})
        df2 (conj df1 {:asset-id "this"  :type "asset"})
        edges (merge {agent-id "this"}
                     (zipmap (mapv :asset-id (:derived-from k2)) (repeat agent-id)))
        fin (assoc {} :provenance-vis {:vertices df2 :edges edges})]
    (spit "/tmp/k3.json" (json/write-str fin))
    )
  (spit "/tmp/k2.json " (-> k1 :results :prov-tree)))
