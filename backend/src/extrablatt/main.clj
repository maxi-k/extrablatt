(ns extrablatt.main
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [extrablatt.app :refer [app]])
  (:gen-class))

(defn- setup
  []
  ;; set the core.async thread pool size to the amount of available cores
  (java.lang.System/setProperty
   "clojure.core.async.pool-size"
   (str (max 8 (.availableProcessors (java.lang.Runtime/getRuntime))))))

(defn -main [& args]
  (setup)
  (run-jetty app {:port (Integer/valueOf (or (System/getenv "port") "3000"))}))
