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

(defn- disable-cookie-management [builder request]
  (.disableCookieManagement builder))

(def meta-image-tags
  "Meta tags that are used for assigning an image to an article/site."
  #{"og:image" "twitter:image:src"})

(defn- extract-meta-image
  "Tries to extract an image url from the documents meta tags."
  [doc]
  (->> (html/select doc [:meta])
       (map :attrs)
       (filter (fn [attr-list]
                 (or
                  (contains? meta-image-tags (:name attr-list))
                  (contains? meta-image-tags (:property attr-list)))))
       (map :content)
       (filter #(not (str/includes? % "blank.")))
       (first)))

(defn- extract-img-tag-image
  "Tries to extract an image url from an image tag in the document."
  ([doc] (extract-img-tag-image doc [:img]))
  ([doc selector]
   (->> (html/select doc selector)
        (filter #(> 2 (count (or (-> % :attrs :width) ""))))
        (map #(-> % :attrs :src))
        (filter some?)
        (filter #(not (str/includes? % "blank.")))
        (filter #(not (str/includes? % ".gif")))
        (filter #(not (str/includes? % "1x1")))
        (first))))

(defn- normalize-image-url
  "Tries to normalize a given image url from the given origin so
  that it can be fetched from any origin."
  [origin img]
  (if (or (str/starts-with? img "http")
          (str/starts-with? img "www"))
    img
    (if (str/starts-with? img "//")
      (str "https:" img)
      (if (str/starts-with? img "/")
        (let [url (java.net.URL. origin)]
          (str (.getProtocol url) "://" (.getHost url) img))
        (str (subs origin 0 (str/last-index-of origin "/")) "/" img)))))

(defn extract-image-url
  [html-string url]
  (let [doc (html/html-snippet html-string)
        img (or
             (extract-meta-image doc) ;; images from meta nodes
             (extract-img-tag-image doc [#{:article :.content} :img]) ;; first image from an article tag / .content div
             (extract-img-tag-image doc [:header :img]) ;; first image from some header
             (extract-img-tag-image doc [:aside :img]) ;; first image from some aside
             (extract-img-tag-image doc)) ;; first image from body
        img-url (and img (normalize-image-url url img))]
    (when img-url
      (try
        (http/get img-url {:throw-exceptions true
                           :accept "text/html"
                           :http-builder-fns [disable-cookie-management]})
        img-url
        (catch Exception e nil)))))

(defn find-image-for-url
  [url]
  (when url
    (when-let [res (try
                     (http/get url {:throw-exceptions false
                                    :accept "text/html"
                                    :http-builder-fns [disable-cookie-management]})
                     (catch Exception e (println "Exception thrown while fetching image for" url)))]
      (when (and (= 200 (:status res))
                 (:body res)
                 (str/starts-with? (get (:headers res) "Content-Type") "text/html"))
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
        (let [img (find-image-for-url url)]
          (dosync
           (alter cache assoc key (or img :not-found))))))))
