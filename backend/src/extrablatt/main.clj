(ns extrablatt.main
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defrecord EnvironmentComponent []
  component/Lifecycle
  (start [self]
    (let [processors (max 8 (.availableProcessors (java.lang.Runtime/getRuntime)))
          port (Integer/valueOf (or (System/getenv "port") "8080"))
          apache-logging (or (System/getenv "apache-logging") "org.apache.commons.logging.impl.NoOpLog")]
      (java.lang.System/setProperty "clojure.core.async.pool-size"
                                    (str processors))
      (java.lang.System/setProperty "org.apache.commons.logging.Log" apache-logging)
      (assoc self
             :async-pool-size processors
             :port port
             :apache-logging apache-logging)))
  (stop [self] self))

(defn new-environment
  []
  (map->EnvironmentComponent {}))

(defrecord ServerComponent [config environment app]
  component/Lifecycle

  (start [self]
    (assoc self :server (run-jetty (:api app) {:port (:port environment)
                                               :join? (:block? config)})))
  (stop [{:as self :keys [server]}]
    (.stop server)
    (dissoc self :server)))

(defn new-server [block?]
  (map->ServerComponent {:block? block?}))

(defn load-hackernews []
  (require 'extrablatt.hn)
  ((resolve 'extrablatt.hn/new-hackernews)))

(defn load-app []
  (require 'extrablatt.app)
  ((resolve 'extrablatt.app/new-app)))

(defn server-system
  [config]
  (component/system-map
   :environment (new-environment)
   :hackernews (load-hackernews)
   :app (component/using (load-app) [:hackernews])
   :server (component/using (new-server config) [:environment :app])))

;; TODO use https://cognitect.com/blog/2013/06/04/clojure-workflow-reloaded in user.clj
(defn -main [& args]
  (component/start (server-system {:block? true})))
