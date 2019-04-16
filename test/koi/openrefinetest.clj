(ns koi.openrefinetest
  (:require  [clojure.test :as t :refer [deftest is testing]]
             [cheshire.core :as cheshire]
             [ring.mock.request :as mock]
             [koi.api :as api :refer [app]]
             [koi.middleware :as mw]
             ))

#_(deftest refine-test
  (let [adid "1234567890123456789012345678901234567890123456789012345678901234"
        aurl "https://gist.githubusercontent.com/curran/a08a1080b88344b0c8a7/raw/d546eaee765268bf2f487608c537c05e22e4b221/iris.csv"
        asset-urls {adid aurl}]
    (with-redefs [mw/get-asset-url (fn[i] (asset-urls i))]
      (let [inp {:operation :openrefine
                 :params {:csv {:asset-did adid
                                :service-agreement-id adid}}}
            resp (mw/invoke-handler {:body-params inp})]
        (is resp)
        (is (-> resp :body (.startsWith "http")))))))
