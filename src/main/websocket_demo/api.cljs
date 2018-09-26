(ns websocket-demo.api
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.websockets.networking :as wn]
            [fulcro.client.primitives :as prim]
            [clojure.spec.alpha :as s]
            [websocket-demo.schema :as schema]
            [fulcro.client :as fc]
            [fulcro.client.logging :as log]))

(defn remove-ident
  "Removes the given ident from a list of idents. Returns a vector."
  [ident-list ident]
  (into [] (remove #(= ident %)) ident-list))

(defonce reconciler (atom nil))

;;; PUSH NOTIFICATIONS. Incoming messages ;; will have the format {:topic verb :msg edn-content}.
(defmulti push-received
  "Multimethod to handle push events"
  (fn [{:keys [topic msg]}]
    (js/console.log :TOPIC topic)
    topic))

(defmethod push-received :user-left-chat-room [{user-id :msg}]
  (when-not (int? user-id)
    (log/error "Invalid user ID left the room!" user-id))
  (let [state      (prim/app-state @reconciler)
        user-ident [:USER/BY-ID user-id]]
    (swap! state (fn [s]
                   (-> s
                     (update :root/all-users remove-ident user-ident)
                     (update :USER/BY-ID dissoc user-id))))))

(defmethod push-received :user-entered-chat-room [{user :msg}]
  (when-not (s/valid? ::schema/user user)
    (log/error "Invalid user entered chat room" user))
  (let [state         (prim/app-state @reconciler)
        channel-ident (get @state :current-channel)
        user-ident    [:USER/BY-ID (:db/id user)]]
    (swap! state (fn [s] (-> s
                           (assoc-in user-ident user)
                           (m/integrate-ident* user-ident :append [:root/all-users]))))))

(defmethod push-received :add-chat-message [{{id :db/id :as message} :msg}]
  (when-not (s/valid? ::schema/chat-room-message message)
    (log/error "Invalid message added to chat room" message))
  (let [state           (prim/app-state @reconciler)
        message-ident   [:MESSAGE/BY-ID id]
        chat-room-ident (::schema/chat-room @state)]
    (swap! state (fn [s]
                   (-> s
                     (assoc-in message-ident message)
                     (m/integrate-ident* message-ident :append (conj chat-room-ident ::schema/chat-room-messages)))))))

;;; CLIENT MUTATIONS

(defmutation link-active-users
  "Mutation: When the chat room loads, the users in the room are not linked up to the active user UI. This function
  hooks em up as a post-mutation from the initial load in client.cljs."
  [params]
  (action [{:keys [state]}]
    ; The query can do the work, but the active users pane has to exist...
    (let [chat-room-ident (::schema/chat-room @state)]
      (swap! state (fn [s]
                     (-> s
                       (assoc-in [:UI-ACTIVE-USERS :UI] {})
                       (assoc-in (conj chat-room-ident :active-user-panel) [:UI-ACTIVE-USERS :UI])))))))

(defmutation login
  "Mutation: Login in. Sets the current user, records us in the list of users, and lets the server know they're in."
  [{:keys [db/id] :as new-user}]
  (action [{:keys [state]}]
    (let [user-ident [:USER/BY-ID id]]
      (swap! state (fn [s] (-> s
                             (assoc-in user-ident new-user)
                             (m/integrate-ident* user-ident :replace [:root/current-user] :append [:root/all-users])))))
    {})
  (remote [env] true))

(defmutation add-chat-message
  "Mutation: Add a message to the current app state, and sends it to the server. The server will broadcast it to everyone else.
  :db/id in the message should be a tempid, which will be remapped by the server."
  [{:keys [db/id panel] :as message}]
  (action [{:keys [state]}]
    (when-not (s/valid? ::schema/chat-room-message message)
      (log/error "Attempt to add an invalid chat room message!"))
    (let [state           state
          message-ident   [:MESSAGE/BY-ID id]
          chat-room-ident (get @state ::schema/chat-room)]
      (swap! state (fn [s] (-> s
                             (assoc-in message-ident message)
                             (m/integrate-ident* message-ident :append (conj chat-room-ident ::schema/chat-room-messages))))))
    {})
  (remote [env] true))
