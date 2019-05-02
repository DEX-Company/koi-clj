(ns koi.apitest
  (:require  [clojure.test :as t :refer [deftest is testing]]
             [cheshire.core :as cheshire]
             [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                                internal-server-error
                                                                bad-request]]
             [ring.mock.request :as mock]
             [koi.api :as api :refer [app]])
  (:import [sg.dex.crypto Hash])
  )

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(deftest testerrorresponses
  (testing "Test request to hash operation"
    (let [input "stringtohash"
          hashval (Hash/keccak256String input)
          response (app (-> (mock/request :post "/invoke/hashing")
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash input}))))
          body     (parse-body (:body response))]
      (is (= hashval body))
      (is (= (:status response) (:status (ok))))))
  (testing "Test request to nonexisting operation"
    ;;fakehashing isn't a valid operation did
    (let [response (app (-> (mock/request :post "/invoke/fakehashing")
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:to-hash "abc"}))))]
      (is (= (:status response) (:status (unprocessable-entity))))))
  (testing "Test bad params to valid operation"
    (let [response (app (-> (mock/request :post "/invoke/hashing")
                            (mock/content-type "application/json")
                            ;;hashing needs to-hash as an argument
                            (mock/body (cheshire/generate-string {:abc "def"}))))]
      (is (= (:status response) (:status (bad-request))))))
  (testing "Test failing operation"
    (let [response (app (-> (mock/request :post "/invoke/fail")
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))]
      (is (= (:status response) (:status (internal-server-error))))))
  (testing "Test async failing operation"
    (let [response (app (-> (mock/request :post "/invokeasync/fail")
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string {:dummy "def"}))))
          jobid     (:jobid (parse-body (:body response)))
          jobres (app (-> (mock/request :get (str "/jobs/" jobid))
                          (mock/content-type "application/json")))
          job-body (parse-body (:body jobres))
          ]
      (is (= (:status response) (:status (created))))
      (is (= (:status jobres) (:status (ok))))
      (is (every? #{:status :errorcode :description} (keys job-body))))))
