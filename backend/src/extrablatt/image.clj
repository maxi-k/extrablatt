(ns extrablatt.image
  (:require
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.set :refer [rename-keys union difference]]))

(def image-folder
  "The local path where article images are cached."
  "/tmp/extrablatt-images")

(def image-cache
  "Image cache mapping key to local image path."
  (ref {}))
