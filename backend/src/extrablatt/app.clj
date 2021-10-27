(ns extrablatt.app
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [routes context GET PUT POST]]
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

(defn- api-routes
  [hackernews]
  (routes
   (GET "/" [count] (response (hn/front-page (parse-number-param count hn/default-front-page-count))))
   (GET ["/thread/:id" :id #"[0-9]+"] [id depth]
        (response (hn/thread-detail id
                                    (parse-number-param depth hn/default-thread-depth))))))

(defn- api-handler
  "Main definition for the webserver endpoints."
  [hackernews]
  (-> (api-routes hackernews)
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (handler/api)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get])))

(defrecord AppComponent [hackernews]
    component/Lifecycle
    (start [self]
      (assoc self :api (api-handler hackernews)))
    (stop [self]
      (dissoc self :api)))

(defn new-app
  "Create a new app component"
  []
  (map->AppComponent {}))
