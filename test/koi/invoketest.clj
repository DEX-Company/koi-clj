(ns koi.invoketest
  (:require  [clojure.test :as t :refer [deftest is testing]]
             [cheshire.core :as cheshire]
             [ring.mock.request :as mock]
             [koi.api :as api :refer [app]]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(deftest openrefinetest
  (testing "Test request to openrefine "
    (let [svc {:iri "https://petstore.swagger.io/v2/swagger.json"}
          response (app (-> (mock/request :post "/register")
                            (mock/content-type "application/json")
                            (mock/body (cheshire/generate-string svc))))
          body     (parse-body (:body response))]
      
      (is (= (:status response) 200))
      (let [resp2 (app (-> (mock/request :get "/services")))
            pb (-> resp2 :body parse-body)]
        (is (map? pb))
        (is (map? (:services pb)))))))


