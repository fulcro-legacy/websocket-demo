(ns websocket-demo.api
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [fulcro.server :refer [defquery-root defquery-entity defmutation server-mutate]]
            [websocket-demo.database :as store]
            [websocket-demo.schema :as schema]
            [fulcro.websockets.protocols :refer [WSListener client-dropped client-added add-listener remove-listener push]]
            [clojure.spec.alpha :as s]
            [om.next :as om]))

(def db
  (atom {::schema/users           []
         ::schema/next-user-id    1
         ::schema/next-message-id 1
         ::schema/chat-room       {:db/id                      1
                                   ::schema/chat-room-messages []
                                   ::schema/chat-room-title    "General Discussion"}}))

(defquery-root ::schema/users
  "Retrieve all of the current users."
  (value [env params]
    (::schema/users @db)))

(defquery-root ::schema/chat-room
  "Retrieve the chat room (with the current messages)."
  (value [env params]
    (::schema/chat-room @db)))

(defn notify-others [ws-net sender-id verb edn]
  (timbre/info "Asked to broadcast " verb edn)
  (let [clients        (:any @(:connected-cids ws-net))
        all-but-sender (disj clients sender-id)]
    (doseq [id all-but-sender]
      (timbre/info verb " to: " id)
      (push ws-net id verb edn))))

(defmutation add-chat-message
  "Process a new message from a client. We remap the incoming tempid to a real one that we generate on the server. Only
  the last 100 messages are kept in the room. (we purge 10 at 110). Clients keep as many as they've seen, if they want."
  [{:keys [db/id] :as message}]
  (action [{:keys [ws-net cid] :as env}]
    (timbre/info "New message from " cid " message:" message)
    (if-not (s/valid? ::schema/chat-room-message message)
      (timbre/error "Received invalid message from client!")
      (let [real-id (swap! db ::schema/next-message-id inc)
            message (assoc message :db/id real-id)]
        (swap! db update-in [::schema/chat-room ::schema/chat-room-messages] conj message)
        (when (< 110 (count (-> @db ::schema/chat-room ::schema/chat-room-message)))
          (swap! db update-in [::schema/chat-room ::schema/chat-room-messages]
            (fn [messages]
              (vec (drop 10 messages)))))
        (notify-others ws-net cid :add-chat-message message)
        {:tempids {id real-id}}))))

(defmutation login
  "Server mutation: Respond to a login request. Technically we've already got their web socket session, but we don't know
  who they are. This associates a user with their websocket client ID."
  [{:keys [user]}]
  (action [{:keys [cid ws-net]}]
    (if-not (s/valid? ::schema/user user)
      (timbre/error "Received invalid user from client!")
      (let [{:keys [db/id]} user
            user (assoc user :db/id cid)]
        (timbre/info "User logged in" user)
        (swap! db update ::schema/users conj user)
        (notify-others ws-net cid :user-entered-chat-room user)
        {:tempids {id cid}}))))

(defn user-disconnected
  "Called when websockets detects that a user has disconnected. We immediately remove them from the room"
  [ws-net user-id]
  (timbre/info "User left " user-id)
  (swap! db update ::schema/users (fn [users] (vec (filter #(not= user-id (:db/id %)) users))))
  (notify-others ws-net user-id :user-left-chat-room {:db/id user-id}))

; The channel server gets the websocket events (add/drop). It can send those to any number of channel listeners. This
; is ours. By making it depend on the channel-server, we can hook in when it starts.
(defrecord ChannelListener [channel-server]
  WSListener
  (client-dropped [this ws-net cid] (user-disconnected ws-net cid))
  (client-added [this ws-net cid])

  component/Lifecycle
  (start [component]
    (add-listener channel-server component)
    component)
  (stop [component]
    (remove-listener channel-server component)
    component))

(defn make-channel-listener []
  (component/using
    (map->ChannelListener {})
    [:channel-server]))
