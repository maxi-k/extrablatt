(ns extrablatt.hn
  (:require
   [clojure.core.async :as async :refer [<! >! go <!!]]
   [clojure.core.reducers :as r]
   [clojure.set :refer [rename-keys]]
   [cheshire.core :refer [parse-string]]
   [clj-http.client :as http])
  (:import java.time.Instant))

(def hn-base-url
  "The hacker news base api url"
  "https://hacker-news.firebaseio.com/v0/")

(def hn-default-depth
  "The default depth to fetch for the children of a thread/item."
  2)

(defn- hn-cache-request-time
  "How long to cache requests for before re-fetching based on endpoint."
  [endpoint]
  (let [map {:topstories 10
             :item 30}
        default 30]
    (get map endpoint default)))

(defn- make-hn-endpoint
  "Format a hacker-news api url with the given key."
  [endpoint]
  (str hn-base-url (name endpoint) ".json"))

(defn- fetch-hn-endpoint-nocache
  "Fetch a hackernews endpoint, throwing any exceptions that occur.
  Returns a clojure-data-coerced body."
  [endpoint]
  (let [result
        (-> (make-hn-endpoint (name endpoint))
            (http/get {:accept :json}))]
    {:status (:status result)
     :body (parse-string (:body result) true)}))

;; also look at https://clojuredocs.org/clojure.core/delay for possible implementation
(def ^:private fetch-hn-endpoint-memoized
  (memoize (fn [endpoint time]
             ;; (println "fetching item from remote from " endpoint " with time " time)
             (fetch-hn-endpoint-nocache endpoint))))

(defn fetch-hn-endpoint
  "Fetch a hackernews endpoint, potentially using the cache to answer the request.
  Bubbles errors."
  ([endpoint additional]
   (fetch-hn-endpoint (str (name endpoint) "/" additional)))
  ([endpoint]
   (let [secs (.getEpochSecond (Instant/now))
         rounded (- secs (mod secs (hn-cache-request-time endpoint)))]
     (fetch-hn-endpoint-memoized endpoint rounded))))

(defn fetch-top-story-ids
  "Fetch the hacker-news top story ids."
  []
  (:body (fetch-hn-endpoint :topstories)))

(defn- convert-hn-top-item
  "Converts a hackernews api item to our application-internal format for top stories."
  [item]
  (-> item
      (rename-keys {:by :author})
      (dissoc :kids)
      (assoc :previewImage "https://cataas.com/cat")))

(defn fetch-item-by-id
  "Fetch a hackernews item (comment, story) by id."
  [id]
  (:body (fetch-hn-endpoint :item id)))

(defn fetch-items-parallel
  "Given a list of item ids, fetch their respective threads in parallel
  using the given fetcher function (default: fetch-item-by-id).
  Additionaly, apply the given function f in each parallel execution context
  to the result (defaults to identity).
  Return a vector of fetched items, not necesseraly in the input order."
  ([ids] (fetch-items-parallel ids identity))
  ([ids f] (fetch-items-parallel ids f fetch-item-by-id))
  ([ids f fetcher]
   (let [n (count ids)]
     (if (zero? n)
       []
       (let [c (async/chan n)
             results (transient [])]
         (doseq [id ids] (go (>! c (f (fetcher id)))))
         (doseq [id ids] (conj! results (<!! c)))
         (persistent! results))))))

(defn fetch-top-items
  ;; TODO find out if HN top endpoint takes an n parameter
  "Fetch the top n news items."
  ([] (fetch-top-items 50))
  ([n] (fetch-items-parallel
        (take n (fetch-top-story-ids))
        convert-hn-top-item )))

(defn- fetch-thread-details-recur
  [thread depth]
   (let [converted (convert-hn-top-item thread)]
     (if (= 0 depth)
       (assoc converted :comments [])
       (assoc converted :comments
              (fetch-items-parallel (:kids thread)
                                    #(fetch-thread-details-recur % (- depth 1)))))))

(defn fetch-thread-details
  "Fetch the thread details (recurring on child ids) for a given thread id."
  ([id] (fetch-thread-details id hn-default-depth))
  ([id depth]
   (fetch-thread-details-recur (fetch-item-by-id id) depth)))
