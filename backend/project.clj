(defproject extrablatt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "1.3.618"]
                 [com.stuartsierra/component "1.0.0"]
                 [cheshire "5.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors "0.1.13"]
                 ;; TODO: using jetty in production, maybe switch to something else later
                 [ring/ring-jetty-adapter "1.8.2"]
                 [matchbox "0.0.9"]
                 [clj-http "3.12.3"]
                 [enlive "1.1.6"]]
  :plugins [[lein-ring "0.12.5"]]
  :uberjar-name "extrablatt-standalone.jar"
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]
                        [ring/ring-jetty-adapter "1.8.2"]
                        [org.clojure/tools.namespace "1.1.0"]]
         :source-paths ["dev" "src"]}
   :uberjar {:aot :all
             :main extrablatt.main
             :source-paths ["src"]}})
