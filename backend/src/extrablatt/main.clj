(ns extrablatt.main
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :refer [run-jetty]]
   [extrablatt.app :as app]
   [extrablatt.hn :as hn]
   )
  (:gen-class))

(defrecord EnvironmentComponent []
  component/Lifecycle
  (start [self]
    (let [processors (max 8 (.availableProcessors (java.lang.Runtime/getRuntime)))
          port (Integer/valueOf (or (System/getenv "port") "3000"))]
      (java.lang.System/setProperty "clojure.core.async.pool-size"
                                    (str processors))
      (-> self
          (assoc :async-pool-size processors)
          (assoc :port port))))
  (stop [self]))

(defn new-environment
  []
  (map->EnvironmentComponent {}))

(defrecord ServerComponent [environment app]
  component/Lifecycle

  (start [self]
    (assoc self :server (run-jetty (app) (:port environment))))

  (stop [{:as self :keys [server]}]
    (.stop server)))

(defn new-server []
  (map->ServerComponent {}))

(defn app-system
  []
  (component/system-map
   :environment (new-environment)
   :hackernews (hn/new-hackernews)
   :app (component/using (app/new-app) [:hackernews])))

(defn server-system
  "The main server system, responsible for starting and stopping all used components."
  []
  (merge (app-system)
         (component/system-map
          :server (component/using (new-server) [:environment :app]))))

(defn dev-app-handler
  [config]
  (let [system (app-system)]
    (:api (:app system))))

;; TODO use https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded in user.clj
(defn -main [& args]
  (component/start (server-system)))
