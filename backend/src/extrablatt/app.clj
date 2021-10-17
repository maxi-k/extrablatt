(ns extrablatt.app
  (:require [compojure.core :refer [routes defroutes context GET PUT POST]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :refer [response]]
            [extrablatt.hn :as hn]))

(defn- setup
  []
  ;; set the core.async thread pool size to the amount of available cores
  (java.lang.System/setProperty "clojure.core.async.pool-size"
                                (str (.availableProcessors (java.lang.Runtime/getRuntime)))))

(setup)

(defroutes api-routes
  (GET "/" request (response (hn/fetch-top-items)))
  (GET ["/thread/:id" :id #"[0-9]+"] [id] (response (hn/fetch-thread-details id))))

(def app
  (-> api-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (handler/api)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get])))
