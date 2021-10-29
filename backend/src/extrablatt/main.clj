(ns extrablatt.main
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :refer [run-jetty]])
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
    (assoc self :server (run-jetty (:api app) {:port (:port environment)})))

  (stop [{:as self :keys [server]}]
    (.stop server)))

(defn new-server []
  (map->ServerComponent {}))

(defn load-hackernews []
  (require 'extrablatt.hn)
  ((resolve 'extrablatt.hn/new-hackernews)))

(defn load-app []
  (require 'extrablatt.app)
  ((resolve 'extrablatt.app/new-app)))

(defn server-system
  []
  (component/system-map
   :environment (new-environment)
   :hackernews (load-hackernews)
   :app (component/using (load-app) [:hackernews])
   :server (component/using (new-server) [:environment :app])))

;; TODO use https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded in user.clj
(defn -main [& args]
  (component/start (server-system)))
