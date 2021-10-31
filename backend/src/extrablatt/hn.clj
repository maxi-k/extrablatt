(ns extrablatt.hn
  (:require
   [com.stuartsierra.component :as component]
   [clojure.core.async :as async :refer [<! >! go <!! timeout alts! go-loop close!]]
   [clojure.set :refer [rename-keys union difference]]
   [matchbox.core :as m]
   [extrablatt.image :as img])
  (:import java.time.Instant))

(def logging
  "Whether `log` should log the given messages"
  (atom false))

(defn- log
  "Log the given items to std out if logging is enabled"
  [& args]
  (when @logging
    (apply println args)))

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
      (rename-keys {:by :author, :kids :comments})))

(def top-stories
  "The list of current top thread ids."
  (ref []))

(def thread-cache
  "Map from thread-id to {:fetched timestamp, :data thread-data}"
  (ref {}))

(def stories-to-fetch
  "Set of thread ids that should be fetched in the background."
  (ref #{}))

(def ^:private default-stats {:to-fetch 0
                              :in-cache 0
                              :images-crawled 0
                              :images-found 0
                              :last-startpage-update 0})
(def stats
  "Statistics on the cache performance."
  (ref default-stats))

(def fb-root
  "Firebase root context for the hackernews firebase api."
  (atom nil))

;; TODO hacky global state to glue component systems together
(def active-image-fetcher
  "Image fetcher instance used"
  (atom nil))

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
             closer (m/listen-to @fb-root
                                 ["item" (str id)]
                                 :value
                                 (fn [res]
                                   (when res (go (>! ch res)))))]
         (go
           (let [[key raw-thread] (<! ch)
                 thread (convert-hn-item raw-thread)]
             (when thread
               (when (:url thread)
                 (img/find-image-async @active-image-fetcher
                                       (:id thread)
                                       (:url thread)))
               (dosync
                (alter thread-cache assoc (:id thread) {:time (Instant/now)
                                                        :data thread})))
             (close! ch)
             (closer)
             (>! output (or thread :not-found))
             (close! output)))
         output)))))

(defn- update-cache-stats
  "Update the cache stats."
  [to-fetch]
  (let [img-cache @(:cache @active-image-fetcher)
        cached (count @thread-cache)
        img-crawled (count img-cache)
        img-found (count (filter #(not= :not-found %) img-cache))]
            (dosync
             (alter stats assoc
                    :to-fetch to-fetch
                    :in-cache cached
                    :images-crawled img-crawled
                    :images-found img-found))))

(def background-fetcher-max-concurrency 32)
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
                                        (filter #(not= :not-found (:thread %)))
                                        (filter #(< 0 (:depth %)))
                                        (map #(update % :depth dec))
                                        (mapcat (fn [{:keys [depth thread]}]
                                                  (map (fn [c] {:depth depth :id c})
                                                       (:comments thread)))))
                                       conj #{} arg))
        queue-t (sorted-set-by (fn [v1 v2]
                                 (-
                                  (compare [(:depth v1) (:id v1)]
                                           [(:depth v2) (:id v2)]))))]
    (go-loop [to-fetch queue-t]
      (let [[ids ch] (if (not (empty? to-fetch))
                       (alts! [stop-chan watch-chan] :default #{})
                       (alts! [stop-chan watch-chan]))]
        (if (= ch stop-chan)
          (log "stopping detail fetcher")
          (let [all (into (empty queue-t) (union to-fetch ids))
                batch (take background-fetcher-max-concurrency all)
                next-level (->> batch
                                (map (fn [{:keys [id depth]}]
                                       (go {:depth depth :thread (<! (fetch-and-cache-thread id))})))
                                (async/merge)
                                (async/into [])
                                (<!)
                                (transduce-fetched))]
            (update-cache-stats (max 0 (- (count to-fetch) (count batch))))
            (log "recurring with to-fetch " (count to-fetch) " after batch " (count batch)
                 "with type " (type to-fetch)
                 "and depth of batch " (map :depth batch))
            (recur (into (empty queue-t) (difference (union all next-level)
                                                     (into queue-t batch))))))))
    (fn []
      (go (>! stop-chan ::stop))
      (remove-watch stories-to-fetch :thread-detail-fetcher))))

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
   @fb-root :topstories
   (fn [stories]
     (when (not @ignore-top-stories)
       (let [story-set (into #{} stories)
             sorted (sort (comp - compare) stories)
             old-stories (dosync
                          (let [old @top-stories]
                            (ref-set top-stories sorted)
                            old))
             new-stories (difference story-set old-stories)]
         (fetch-thread-details-async default-thread-depth new-stories)
         (dosync (alter stats assoc :last-startpage-update (.getEpochSecond (Instant/now))))
         (log "Got front page update - currently in cache: " (count @thread-cache) " and to fetch " (count @stories-to-fetch)
              "with new frontend stories " (count new-stories) " (given " (count story-set) ")"))))))

(defn- assoc-cached-image
  "Try to associate the cached image with the given thread."
  [thread]
  (let [cache (:cache @active-image-fetcher)
        saved (@cache (:id thread))]
    (if (and saved (not= :not-found saved))
      (assoc thread :previewImage saved)
      thread)))

(def front-page-timeout 2000)
(defn front-page
  "Return the front page, loading missing entries if necessary."
  ([] (front-page default-front-page-count))
  ([n]
   (let [ids (take n @top-stories)
         len (count ids)
         chs (async/merge (map (fn [id] (fetch-and-cache-thread id)) ids))
         timeout (async/timeout front-page-timeout)]
     (sort (fn [t1 t2] (- (compare (:id t1) (:id t2))))
      (filter some?
              (<!!
               (go-loop [left len
                         res []]
                 (if (= 0 left)
                   res
                   (let [[val ch] (alts! [timeout chs])]
                     (if (= ch timeout)
                       res
                       (recur (- left 1)
                              (conj res (assoc-cached-image (dissoc val :comments))))))))))))))

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

(defn get-stats
  "Get the stats map for the frontend."
  []
  (rename-keys @stats {:to-fetch :itemsToFetch
                       :in-cache :itemsCached
                       :images-crawled :imagesCrawled
                       :images-found :imagesFound
                       :last-startpage-update :lastStartpageUpdate}))

(defrecord HackernewsFetcher [image-fetcher]
    component/Lifecycle
    ;; TODO also put global state (caches etc) into components
    (start [self]
      (reset! active-image-fetcher image-fetcher)
      (reset! fb-root (m/connect hn-base-url))
      (-> self
          (assoc :detail-fetcher (setup-thread-detail-fetcher))
          (assoc :story-fetcher (setup-top-thread-fetcher))))

    (stop [{:as self :keys [detail-fetcher story-fetcher]}]
      (story-fetcher)
      (detail-fetcher)
      (reset! active-image-fetcher nil)
      (reset! fb-root nil)
      (print "resetting global state")
      (dosync
       (ref-set stats default-stats)
       (ref-set top-stories #{})
       (ref-set thread-cache {})
       (ref-set stories-to-fetch #{}))
      (dissoc self :story-fetcher :detail-fetcher)))

(defn new-hackernews []
  (component/system-map
   :image-fetcher (img/new-image-fetcher)
   :hackernews-fetcher (component/using
                        (map->HackernewsFetcher {})
                        [:image-fetcher])))
