(ns extrablatt.main
  (:require
   [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn- setup
  []
  ;; set the core.async thread pool size to the amount of available cores
  (java.lang.System/setProperty
   "clojure.core.async.pool-size"
   (str (max 8 (.availableProcessors (java.lang.Runtime/getRuntime))))))

(defn -main [& args]
  (setup)
  (require 'extrablatt.app)
  (require 'extrablatt.hn)
  ((resolve 'extrablatt.hn/hn-setup))
  (run-jetty (resolve 'extrablatt.app/app) {:port (Integer/valueOf (or (System/getenv "port") "3000"))}))
