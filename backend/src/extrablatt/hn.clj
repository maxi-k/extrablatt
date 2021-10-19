(ns extrablatt.hn
  (:require
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.set :refer [rename-keys union difference]]
   [matchbox.core :as m])
  (:import java.time.Instant))

(def hn-base-url
  "The hackernews base api url"
  "https://hacker-news.firebaseio.com/v0/")

(def default-thread-depth
  "The default depth to fetch for the children of a thread/item."
  4)

(def default-front-page-count
  "Default count of entries to fetch for the front-end."
  50)

(defn convert-hn-item
  "Converts a hackernews api item to our application-internal format for top stories."
  [item]
  (-> item
      (rename-keys {:by :author, :kids :comments})
      (assoc :previewImage "https://cataas.com/cat")))

(def fb-root
  "Firebase root context for the hackernews firebase api."
  (m/connect hn-base-url))

(def top-stories
  "The list of current top thread ids."
  (ref []))

(def thread-cache
  "Map from thread-id to {:fetched timestamp, :data thread-data}"
  (ref {}))

(def stories-to-fetch
  "Set of thread ids that should be fetched in the background."
  (ref #{}))

(defn is-fresh?
  "Returns true if the given cache item is still fresh enough"
  [item]
  (and item
       (> 60
          (- (.getEpochSecond (Instant/now))
             (.getEpochSecond (:time item))))))

(defn is-fresh-id?
  "Returns true if the item associated with the given id is cached and fresh."
  [id]
  (is-fresh? (get @thread-cache id)))

(defn is-dirty-id?
  "Returns true if the item associated with the given id is not cached or not fresh."
  [id]
  (not (is-fresh-id? id)))

(defn get-cached-thread
  "Get a cached thread by id."
  [id]
  (when-let [cached (get @thread-cache id)]
    (:data cached)))

(defn fetch-and-cache-thread
  "Fetch the given thread if not in the thread cache already or the last fetched timestamp is too old.
  Put it into the thread cache and update the timestamp.
  Returns a channel where the thread will be delivered."
  ([id] (fetch-and-cache-thread id false))
  ([id force?]
   (let [current (get @thread-cache id)]
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
           (let [[key raw-thread] (<! ch)
                 thread (convert-hn-item raw-thread)]
             (dosync
              (alter thread-cache assoc (:id thread) {:time (Instant/now)
                                                    :data thread}))
             (close! ch)
             (closer)
             (>! output thread)
             (close! output)))
         output)))))

(def background-fetcher-max-concurrency 64)
(defn- setup-thread-detail-fetcher
  "Fetches thread details in the background by watching
  the stories-to-fetch ref. Can be stopped by calling it as a function."
  []
  (let [stop-chan (async/chan)
        watch-chan (async/chan)
        fetcher-chan (async/chan)
        watcher (add-watch stories-to-fetch :thread-detail-fetcher
                           (fn [key r old new]
                             (when-not (empty? new)
                               (go
                                 (dosync (alter stories-to-fetch difference new))
                                 (>! watch-chan new)))))
        transduce-fetched (fn [arg]
                            (transduce (comp
                                        (filter #(< 0 (:depth %)))
                                        (map #(update % :depth dec))
                                        (mapcat (fn [{:keys [depth thread]}]
                                                  (map (fn [c] {:depth depth :id c})
                                                       (:comments thread)))))
                                       conj #{} arg))]
    (go-loop [to-fetch #{}]
      (let [[ids ch] (if (not (empty? to-fetch))
                       [(async/poll! watch-chan) nil]
                       (alts! [stop-chan watch-chan]))]
        (when-not (= ch stop-chan)
          (let [all (union to-fetch ids)
                batch (take background-fetcher-max-concurrency all)
                next-level (->> batch
                                (map (fn [{:keys [id depth]}]
                                       (go {:depth depth :thread (<! (fetch-and-cache-thread id))})))
                                (async/merge)
                                (async/into [])
                                (<!)
                                (transduce-fetched))]
            (recur (difference (union all next-level)
                               (into #{} batch)))))))
    (fn [] (async/>!! stop-chan))))

(defn fetch-thread-details-async
  "Asynchronously fetch the thread details of the given items
  tagged as starting with the given depth up to `default-thread-depth`."
  [start-depth items]
  (let [tagged (transduce
                (comp
                 (filter is-dirty-id?)
                 (map (fn [id] {:depth start-depth :id id})))
                conj #{} items)]
    (dosync
     (alter stories-to-fetch union tagged))))

(def ignore-top-stories
  "Whether to stop caching top stories even if updates arrive."
  (atom false))

(defn- setup-top-thread-fetcher
  "Fetches the top thread ids and puts their ids into the top-stories list.
  Also triggers fetching their details recursively."
  []
  (m/listen-list
   fb-root :topstories
   (fn [stories]
     (when (not @ignore-top-stories)
       (dosync
        (ref-set top-stories stories))
       (fetch-thread-details-async default-thread-depth stories)))))

(defn front-page
  "Return the front page, loading missing entries if necessary."
  ([] (front-page default-front-page-count))
  ([n]
   (let [ids (take n @top-stories)
         len (count ids)
         chs (async/merge (map (fn [id] (fetch-and-cache-thread id)) ids))]
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
  ;; XXX also place dummy elements in :comments of thread for loading/non-requested children?
  (if (= 0 depth)
    {:to-fetch {} :thread (assoc start-thread :comments [])}
    (reduce (fn [acc id]
              (if-let [cached (get-cached-thread id)]
                (let [{:keys [to-fetch thread]}
                      (collect-thread-detail-recur cached (- depth 1))]
                  (-> acc
                      (update :to-fetch #(merge-with union % to-fetch))
                      (update-in [:thread :comments] conj thread)))
                (update-in acc [:to-fetch depth] conj id)))
            {:to-fetch {depth #{}} :thread (assoc start-thread :comments [])}
            (:comments start-thread))))

(defn thread-detail
  "Get the thread detail of the given item id and child items up to the given depth."
  ([id] (thread-detail id default-thread-depth))
  ([id depth]
   (let [root (<!! (fetch-and-cache-thread id))
         {:keys [to-fetch thread]} (collect-thread-detail-recur root depth)]
     (doseq [[rel-depth ids] to-fetch] ;; XXX how deep to fetch the rest of the details? (- depth d)
       (fetch-thread-details-async depth ids))
     thread)))

(def ^:private hn-processes
  (atom {}))

(defn hn-setup
  []
  (reset! hn-processes
          {:detail-fetcher (setup-thread-detail-fetcher)
           :story-fetcher (setup-top-thread-fetcher)})
)
