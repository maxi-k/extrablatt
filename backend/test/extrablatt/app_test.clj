(ns extrablatt.app-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [extrablatt.app :refer :all]
            [cheshire.core :refer [parse-string]]))


(deftest test-app
  (testing "init route"
    (let [req (mock/request :get "/")
          response (app req)]
      (is (= (:status response) 200))
      (let [body (:body response)
            parsed (parse-string body true)]
        (is (string? body))
        (is (seq? parsed))
        (is (< 0 (count parsed)))))))
