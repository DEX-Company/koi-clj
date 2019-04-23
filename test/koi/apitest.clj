(ns koi.apitest
  (:require  [clojure.test :as t :refer [deftest is testing]]
             [cheshire.core :as cheshire]
             [ring.util.http-response :as http-response :refer [ok header created unprocessable-entity
                                                                bad-request]]
             [ring.mock.request :as mock]
             [koi.api :as api :refer [app]]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(deftest testerrorresponses
  (testing "Test request to hash operation"
    (let [input "stringtohash"
          hashval (str (hash input))
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
      (is (= (:status response) (:status (bad-request)))))))


