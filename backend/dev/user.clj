(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :as repl]
            [extrablatt.main :as main :refer [server-system]]))

(repl/set-refresh-dirs "dev" "src")

(defonce system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (server-system {:block? false}))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system #(.start %)))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (.stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/go))
