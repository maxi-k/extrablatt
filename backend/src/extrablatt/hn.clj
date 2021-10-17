(ns extrablatt.hn
  (:require
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.core.reducers :as r]
   [clojure.set :refer [rename-keys]]
   [cheshire.core :refer [parse-string]]
   [clj-http.client :as http]
   [matchbox.core :as m]
   [matchbox.async :as ma])
  (:import java.time.Instant))

(def hn-base-url
  "The hacker news base api url"
  "https://hacker-news.firebaseio.com/v0/")

(def hn-default-depth
  "The default depth to fetch for the children of a thread/item."
  2)

(defn convert-hn-item
  "Converts a hackernews api item to our application-internal format for top stories."
  [item]
  (-> item
      (rename-keys {:by :author, :kids :comments})
      (assoc :previewImage "https://cataas.com/cat")))

(def fb-root (m/connect hn-base-url))

(def top-stories
  "The list of current top story ids."
  (ref []))

(def story-cache
  "Map from story-id to {:fetched timestamp, :story story}"
  (ref {}))

(defn is-fresh?
  "Returns true if the given cache item is still fresh enough"
  [item]
  (and item
       (> 60
          (- (.getEpochSecond (Instant/now))
             (.getEpochSecond (:time item))))))

(defn is-fresh-id?
  "Returns true if the cache-item associated with the given id is fresh."
  [id]
  (is-fresh? (get @story-cache id)))

(defn is-dirty-id?
  "Returns true if the cache-item associated with the given id is fresh."
  [id]
  (not (is-fresh-id? id)))

(defn fetch-and-cache-story
  "Fetch the given story if not in the story cache already or the last fetched timestamp is too old.
  Put it into the story cache and update the timestamp.
  Returns a channel where the story will be delivered."
  ([id] (fetch-and-cache-story id false))
  ([id force?]
   (let [current (get @story-cache id)]
     (if (and (not force?) (is-fresh? current))
       (async/to-chan [(:data current)])
       (let [output (async/chan)
             ch (async/chan)
             closer (m/listen-to fb-root
                                 ["item" (str id)]
                                 :value
                                 (fn [res]
                                   (when res (go (>! ch res)))))]
         (go
           (let [[key raw-story] (<! ch)
                 story (convert-hn-item raw-story)]
             (dosync
              (alter story-cache assoc (:id story) {:time (Instant/now)
                                                    :data story}))
             (close! ch)
             (closer)
             (>! output story)))
         output)))))

(def story-prefetch-max-depth 4)
(def story-detail-backlog (async/chan 1024))
(def story-detail-fetcher
  "Fetch story details and put them into the story map."
  (let [stop-chan (async/chan)]
    (go-loop []
      (let [[data ch] (alts! [story-detail-backlog stop-chan])]
        (when (not= ch stop-chan)
          (try
            (let [{:keys [depth items]} data]
              (when (< depth story-prefetch-max-depth)
                (doseq [id (filter is-dirty-id? items)]
                  (go
                    (let [detail (<! (fetch-and-cache-story id))
                          com (filter is-dirty-id? (:comments detail))]
                      (when (seq? com)
                        (>! story-detail-backlog {:depth (+ depth 1) :items com})))))))
            (catch Exception e
              (println "Caught " (.getMessage e) " with data " data)))
          (recur))))
    stop-chan))

(def ignore-top-stories
  "Whether to stop caching top stories even if updates arrive."
  (atom false))

(def top-story-fetcher
  "Fetches the top story ids and puts their ids into the top-stories list.
  Also triggers fetching their details recursively."
  (m/listen-list
   fb-root :topstories
   (fn [stories]
     (when (not @ignore-top-stories)
       (dosync
        (ref-set top-stories stories))
       (go (>! story-detail-backlog {:depth 0 :items (filter is-dirty-id? stories)}))))))


(defn front-page
  ([] (front-page 50))
  ([n]
   (let [ids (take n @top-stories)
         len (count ids)
         chs (async/merge (map (fn [id] (fetch-and-cache-story id)) ids))]
     (filter some?
             (<!!
              (go-loop [left len
                        res []]
                (if (= 0 left)
                  res
                  (recur (- left 1)
                         (conj res (dissoc (<! chs) :comments))))))))))


#_(defn- fetch-thread-details-recur
  [thread depth]
   (let [converted (convert-hn-item thread)]
     (println "fetching " (:id thread) " at depth " depth)
     (if (= 0 depth)
       (assoc converted :comments [])
       (assoc converted :comments
              (fetch-items-parallel (:kids thread)
                                    #(fetch-thread-details-recur % (- depth 1)))))))

(defn fetch-thread-details
  "Fetch the thread details (recurring on child ids) for a given thread id."
  ([id] (fetch-thread-details id hn-default-depth))
  ([id depth]
   {:status :todo}
   ;;(fetch-thread-details-recur (fetch-item-by-id id) depth)
   ))
