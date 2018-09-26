(ns websocket-demo.api
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [fulcro.server :refer [defquery-root defquery-entity defmutation server-mutate]]
            [websocket-demo.schema :as schema]
            [fulcro.websockets.protocols :refer [WSListener client-dropped client-added add-listener remove-listener push]]
            [clojure.spec.alpha :as s]
            [clojure.future :refer :all]
            [fulcro.client.primitives :as prim]))

(def db
  (atom {::schema/users           []
         ::schema/next-message-id 1
         ::schema/next-user-id    1
         ::schema/chat-room       {:db/id                      1
                                   ::schema/chat-room-messages []
                                   ::schema/chat-room-title    "General Discussion"}}))

(defn validate
  "Prints a log message if the spec for things doesn't work out."
  [spec thing]
  (when-not (s/valid? spec thing)
    (timbre/error "Invalid " spec ":" thing)))

(defn swap-db!
  "Does a swap operation on the database, and then checks the validity. Logs an error if it is invalid. This is useful
  for catching errors in our database updates when they accidentally invalidate the content."
  [& args]
  (apply swap! db args)
  (when-not (s/valid? ::schema/database @db)
    (timbre/error "Database update invalid" (ex-info "" {}))))

(def client-map
  "A map from Sente UUID to logged-in user ID. Sente assigns each user a UUID, but it doesn't know who's who. When we get
  a login mutation, we map that UUID to a simple numeric ID that we assign."
  (atom {}))

(defquery-root ::schema/users
  "Retrieve all of the current users."
  (value [env params]
    (let [users (::schema/users @db)]
      (validate ::schema/users users)
      users)))

(defquery-root ::schema/chat-room
  "Retrieve the chat room (with the current messages)."
  (value [env params]
    (let [chat-room (::schema/chat-room @db)]
      (validate ::schema/chat-room chat-room)
      chat-room)))

(defn notify-others
  "Send verb/edn to all clients *except* sender-id. In this case, sender-id is the UUID from Sente."
  [ws-net sender-id verb edn]
  (timbre/info "Broadcasting " verb edn)
  (timbre/info "key " (keys ws-net))
  (let [clients        (:any @(:connected-uids ws-net))
        all-but-sender (disj clients sender-id)]
    (doseq [id all-but-sender]
      (push ws-net id verb edn))))

(s/fdef notify-others
  :args (s/cat :net any? :sender-id int? :verb ::schema/push-verb :message ::schema/push-message)
  :ret any?)

(defmutation add-chat-message
  "Process a new message from a client. We remap the incoming tempid to a real one that we generate on the server. Only
  the last 100 messages are kept in the room. (we purge 10 at 110). Clients keep as many as they've seen, if they want."
  [{:keys [db/id] :as message}]
  (action [{:keys [websockets cid] :as env}]
    (validate ::schema/database @db)
    (validate ::schema/chat-room-message message)
    (when-let [user-id (get @client-map cid)]
      (timbre/info "New message from " user-id " message:" message)
      (try
        (swap-db! update ::schema/next-message-id inc)
        (let [real-id (::schema/next-message-id @db)
              message (assoc message :db/id real-id)]
          (swap-db! update-in [::schema/chat-room ::schema/chat-room-messages] conj message)
          (when (< 110 (count (-> @db ::schema/chat-room ::schema/chat-room-message)))
            (swap-db! update-in [::schema/chat-room ::schema/chat-room-messages] (fn [messages] (vec (drop 10 messages)))))
          (notify-others websockets cid :add-chat-message message)
          (validate ::schema/database @db)
          {:tempids {id real-id}})
        (catch Exception e
          (timbre/error e))))))

(defmutation login
  "Server mutation: Respond to a login request. Technically we've already got their web socket session, but we don't know
  who they are. This associates a user with their websocket client ID.

  The incoming user will have a tempid for :db/id. We'll generate a numeric version of that, return it to the client
  (for an automatic remap in state), and also remember it in our local client map of Sente websocket sessions.
  "
  [{:keys [db/id] :as user}]
  (action [{:keys [cid websockets]}]
    (validate ::schema/database @db)
    (validate ::schema/user user)
    (swap-db! update ::schema/next-user-id inc)
    (let [real-id (::schema/next-user-id @db)
          user    (assoc user :db/id real-id)]
      (timbre/info "User logged in" user)
      (swap! client-map assoc cid real-id)
      (swap-db! update ::schema/users conj user)
      (notify-others websockets cid :user-entered-chat-room user)
      {:tempids {id real-id}})))

(defn user-disconnected
  "Called when websockets detects that a user has disconnected. We immediately remove them from the room. See ChannelListener."
  [ws-net sente-client-id]
  (when-let [real-id (get @client-map sente-client-id)]
    (timbre/info "User left " real-id)
    (swap-db! update ::schema/users (fn [users] (vec (filter #(not= real-id (:db/id %)) users))))
    (notify-others ws-net sente-client-id :user-left-chat-room real-id)))

; The channel server gets the websocket events (add/drop). It can send those to any number of channel listeners. This
; is ours. By making it depend on the websockets, we can hook in when it starts.
(defrecord ChannelListener [websockets]
  WSListener
  (client-dropped [this ws-net cid] (user-disconnected ws-net cid))
  (client-added [this ws-net cid])

  component/Lifecycle
  (start [component]
    (add-listener websockets component)
    component)
  (stop [component]
    (remove-listener websockets component)
    component))

(defn make-channel-listener []
  (component/using
    (map->ChannelListener {})
    [:websockets]))
