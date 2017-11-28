(ns websocket-demo.api
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.websockets.networking :as wn]
            [om.next :as om]
            [clojure.spec.alpha :as s]
            [websocket-demo.schema :as schema]
            [fulcro.client.core :as fc]
            [fulcro.client.logging :as log]))

(defn remove-ident
  "Removes the given ident from a list of idents. Returns a vector."
  [ident-list ident]
  (into [] (remove #(= ident %)) ident-list))

;;; PUSH NOTIFICATIONS

(defmethod wn/push-received :user-left-chat-room [{:keys [reconciler] :as app} user-id]
  (when-not (int? user-id)
    (log/error "Invalid user ID left the room!" user-id))
  (let [state      (om/app-state reconciler)
        user-ident [:user/by-id user-id]]
    (swap! state (fn [s]
                   (-> s
                     (update ::schema/users remove-ident user-ident)
                     (update :user/by-id dissoc user-id))))))

(defmethod wn/push-received :user-entered-chat-room [{:keys [reconciler] :as app} user]
  (when-not (s/valid? ::schema/user user)
    (log/error "Invalid user entered chat room" user))
  (let [state         (om/app-state reconciler)
        channel-ident (get @state :current-channel)
        user-ident    [:user/by-id (:db/id user)]]
    (swap! state fc/integrate-ident user-ident :append [::schema/users])))

(defmethod wn/push-received :add-chat-message [{:keys [reconciler] :as app} {:keys [:db/id] :as message}]
  (when-not (s/valid? ::schema/chat-room-message message)
    (log/error "Invalid message added to chat room" message))
  (let [state         (om/app-state reconciler)
        message-ident [:message/by-id id]]
    (swap! state (fn [s]
                   (-> s
                     (assoc-in message-ident message)
                     (fc/integrate-ident message-ident :append [::schema/chat-room ::schema/chat-room-message]))))))

;;; CLIENT MUTATIONS

(defmutation login
  "Mutation: Login in. Sets the current user, and joins the default channel."
  [{:keys [db/id] :as new-user}]
  (action [{:keys [state]}]
    (let [user-ident [:user/by-id id]]
      (swap! state (fn [s] (-> s
                             (assoc-in user-ident new-user)
                             (fc/integrate-ident user-ident :replace [:curent-user] :append [::schema/users])))))
    {})
  (remote [env] true))

(defmutation add-chat-message
  "Mutation: Add a message to the current app state, and send it to the server. The server will push it to everyone else."
  [{:keys [db/id] :as message}]
  (action [{:keys [state]}]
    (when-not (s/valid? ::chat-room-message message)
      (log/error "Attempt to add an invalid chat room message!"))
    (let [state         state
          message-ident [:message/by-id id]]
      (swap! state (fn [s] (-> s
                             (assoc-in message-ident message)
                             (fc/integrate-ident message-ident :append [::schema/chat-room ::schema/chat-room-messages])))))
    {})
  (remote [env] true))
