(ns extrablatt.image
  (:require
   [com.stuartsierra.component :as component]
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-http.client :as http]
   [net.cgrand.enlive-html :as html]))

(def image-folder
  "The local path where article images are cached."
  "/tmp/extrablatt-images")

(defrecord ImageFetcher []
  component/Lifecycle
  (start [self]
    (io/make-parents (str image-folder "/empty.png"))
    (assoc self :cache (ref {})))
  (stop [self]
    (dissoc self :cache)
    (io/delete-file image-folder)))

(defn new-image-fetcher
  "Construct a new image fetcher component."
  []
  (map->ImageFetcher {}))

(def meta-image-tags
  "Meta tags that are used for assigning an image to an article/site."
  #{"og:image" "twitter:image:src"})

(defn extract-image-url
  [html-string url]
  (let [doc (html/html-snippet html-string)
        img (or ;; images from meta nodes
             (->> (html/select doc [:meta])
                  (map :attrs)
                  (filter (fn [attr-list]
                            (or
                             (contains? meta-image-tags (:name attr-list))
                             (contains? meta-image-tags (:property attr-list)))))
                  (map :content))
             ;; first image from body
             (->> (html/select doc [:img])
                  (map #(-> % :attrs :src))))]
    (->> img
         (map (fn [img-src]
                (if (or (str/starts-with? img-src "http")
                        (str/starts-with? img-src "www"))
                  img-src
                  (str (subs url 0 (str/last-index-of url "/")) "/" img-src))))
         (first))))

(defn find-image-for-url
  [url]
  (when url
    (if-let [res (try (http/get url) (catch Exception e nil))]
      (when (and (= 200 (:status res)) (:body res))
        (extract-image-url (:body res) url)))))

;; <meta name="twitter:image:src" content="https://blog.pwabuilder.com/posts/announcing-ios/ios-announcement.png">
;; <meta name="og:image" content="https://blog.pwabuilder.com/posts/announcing-ios/ios-announcement.png">
(defn find-image-async
  "Tries to find an image from the given url to use as a preview for the frontend.
  Loads it and caches it locally."
  [{:as fetcher :keys [cache]} key url]
  (go
    (let [existing (@cache key)]
      (when (not= :not-found existing)
        (dosync
         (alter cache assoc key
                (or
                 (find-image-for-url url)
                 :not-found)))))))
