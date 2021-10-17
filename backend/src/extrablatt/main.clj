(ns extrablatt.main
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [extrablatt.app :refer [app]])
  (:gen-class))

(defn -main [& args]
  (run-jetty app {:port (Integer/valueOf (or (System/getenv "port") "3000"))}))
