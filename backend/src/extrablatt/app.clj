(ns extrablatt.app
  (:require [compojure.core :refer [routes defroutes context GET PUT POST]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :refer [response]]
            [extrablatt.hn :as hn]))

(defn- parse-number-param
  "Parse a string parameter and return the given default if it fails."
  [str-param default]
  (try (Integer/parseInt str-param)
       (catch NumberFormatException e default)))

(defroutes api-routes
  (GET "/" [count] (response (hn/front-page (parse-number-param count hn/default-front-page-count))))
  (GET ["/thread/:id" :id #"[0-9]+"] [id depth]
       (response (hn/thread-detail id
                                   (parse-number-param depth hn/default-thread-depth)))))

(def app
  "Main definition for the webserver endpoints."
  (-> api-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (handler/api)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get])))
