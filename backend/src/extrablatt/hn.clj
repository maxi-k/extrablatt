(ns extrablatt.hn
  (:require
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.set :refer [rename-keys union difference]]
   [matchbox.core :as m]
   [affable-async.core :as aff])
  (:import java.time.Instant))

(def hn-base-url
  "The hacker news base api url"
  "https://hacker-news.firebaseio.com/v0/")

(def hn-default-depth
  "The default depth to fetch for the children of a thread/item."
  4)

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

(def stories-to-fetch
  "Set of story ids that should be fetched in the background."
  (ref #{}))

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
             (>! output story)
             (close! output)))
         output)))))

(def background-fetcher-max-concurrency 64)
(def story-detail-fetcher
  "Fetches story details in the background by watching
  the stories-to-fetch ref. Can be stopped by calling it as a function."
  (let [stop-chan (async/chan)
        watch-chan (async/chan)
        fetcher-chan (async/chan)
        watcher (add-watch stories-to-fetch :story-detail-fetcher
                           (fn [key r old new]
                             (when-not (empty? new)
                               (go
                                 (dosync (alter stories-to-fetch difference new))
                                 (>! watch-chan new)))))]
    (go-loop [to-fetch #{}]
      (let [[ids ch] (if (not (empty? to-fetch))
                       [(async/poll! watch-chan) nil]
                       (alts! [stop-chan watch-chan]))]
        (when-not (= ch stop-chan)
          (let [all (union to-fetch ids)
                batch (take background-fetcher-max-concurrency all)
                next-level (->> batch
                                (map (fn [{:keys [id depth]}]
                                       (go {:depth depth :thread (<! (fetch-and-cache-story id))})))
                                (async/merge)
                                (async/into [])
                                (<!)
                                ((fn [arg] (transduce (comp
                                                       (filter #(< 0 (:depth %)))
                                                       (map #(update % :depth dec))
                                                       (mapcat (fn [{:keys [depth thread]}]
                                                                 (map (fn [c] {:depth depth :id c})
                                                                      (:comments thread)))))
                                                      conj #{} arg))))]
            (recur (difference (union all next-level)
                               (into #{} batch)))))))
    (fn [] (async/>!! stop-chan))))

(defn fetch-story-details-async
  [start-depth items]
  (let [tagged (transduce
                (comp
                 (filter is-dirty-id?)
                 (map (fn [id] {:depth start-depth :id id}))
                 ) conj #{} items)]
    (dosync
     (alter stories-to-fetch union tagged))))

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
       (fetch-story-details-async hn-default-depth stories)))))

(defn front-page
  "Return the front page, loading missing entries if necessary."
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

(defn- collect-thread-detail-recur
  "Recursively get the children of the given thread,
  collecting only those that are available locally.
  Also return ids of those children that aren't cached locally
  so that the callee can request them from the remote."
  [start-thread depth]
  ;; TODO parallelize this if it makes sense
  ;; TODO also place dummy elements in :comments of thread for loading/non-requested children?
  (if (= 0 depth)
    {:to-fetch #{} :thread (assoc start-thread :comments [])}
    (reduce (fn [acc id]
              (if-let [cached (get @story-cache id)]
                (let [{:keys [to-fetch thread]}
                      (collect-thread-detail-recur cached (- depth 1))]
                  (-> acc
                      (update :to-fetch merge-with union to-fetch)
                      (update-in [:thread :comments] conj thread)))
                (update-in acc [:to-fetch depth] id)))
            {:to-fetch {depth #{}} :thread (assoc start-thread :comments [])}
            (:comments start-thread))))

(defn thread-detail
  "Get the thread detail of the given item id and child items up to the given depth."
  ([id] (thread-detail id hn-default-depth))
  ([id depth]
   (let [root (<!! (fetch-and-cache-story id))
         {:keys [to-fetch thread]} (collect-thread-detail-recur root depth)]
     (fetch-story-details-async depth to-fetch)
     thread)))
