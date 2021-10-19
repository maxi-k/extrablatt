(defproject extrablatt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "1.3.618"]
                 [cheshire "5.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors "0.1.13"]
                 ;; TODO: using jetty in production, maybe switch to something else later
                 [ring/ring-jetty-adapter "1.8.2"]
                 [matchbox "0.0.9"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler extrablatt.app/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]
                        [ring/ring-jetty-adapter "1.8.2"]]}
   :uberjar {:aot [extrablatt.main] :main extrablatt.main}})
