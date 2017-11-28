(ns websocket-demo.api
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.websockets.networking :as wn]
            [om.next :as om]
            [fulcro.client.core :as fc]))

;;; PUSH MUTATIONS

(defn remove-ident
  "Removes the given ident from a list of idents. Returns a vector."
  [ident-list ident]
  (into [] (remove #(= ident %)) ident-list))

(defmutation push-user-left
  "Mutation to process a push notification from the server that indicates that the given user has left. See push-received."
  [{:keys [user]}]
  (action [{:keys [state]}]
    (let [state         state
          {:keys [db/id]} user
          channel-ident (get @state :current-channel)
          user-ident    [:user/by-id id]]
      (swap! state (fn [s]
                     (-> s
                       (update :app/users remove-ident user-ident)
                       (update :user/by-id dissoc id)
                       (update-in (conj channel-ident :channel/users) remove-ident user-ident)))))))

(defmutation push-user-new
  "Mutation to process when the server sends a push notification about a new user. See push-received."
  [{:keys [user]}]
  (action [{:keys [state] :as env}]
    (let [channel-ident (get @state :current-channel)
          user-ident    [:user/by-id (:db/id user)]]
      (swap! state (fn [s] (-> s
                             (fc/integrate-ident user-ident :append [:app/users])
                             (fc/integrate-ident user-ident :append (conj channel-ident :channel/users))))))))

(defmutation push-message-new
  "Mutation to process when the server sends a push notification about a new message. See push-received."
  [{:keys [message]}]
  (action [{:keys [state] :as env}]
    (let [channel-ident (get @state :current-channel)
          message-ident [:message/by-id (:db/id message)]]
      (swap! state (fn [s] (-> s
                             (assoc-in message-ident message)
                             (fc/integrate-ident message-ident :append (conj channel-ident :channel/messages))))))))

; register to receive the push notifications we care about
(defmethod wn/push-received :user/left [{:keys [reconciler] :as app} {:keys [msg]}]
  (om/transact! reconciler `[(push-user-left ~{:user msg})]))

(defmethod wn/push-received :user/new [{:keys [reconciler] :as app} {:keys [msg]}]
  (om/transact! reconciler `[(push-user-new ~{:user msg})]))

(defmethod wn/push-received :message/new [{:keys [reconciler] :as app} {:keys [msg]}]
  (om/transact! reconciler `[(push-message-new ~{:message msg})]))

;;; CLIENT MUTATIONS

(defmutation channel-set [params]
  (action [{:keys [state ast] :as env}]
    (swap! state assoc :current-channel params)))

(defmutation login
  "Mutation: Login in. Sets the current user, and joins the default channel."
  [{:keys [db/id] :as new-user}]
  (action [{:keys [state]}]
    (let [state         state
          user-ident    [:user/by-id id]
          channel-ident (first (-> @state :app/channels))
          channel-id    (second channel-ident)]
      (swap! state (fn [s] (-> s
                             (assoc-in user-ident new-user) ; add the user to our db
                             (assoc :current-channel channel-ident
                                    :current-user user-ident)
                             (fc/integrate-ident user-ident :append [:app/users])
                             (fc/integrate-ident user-ident :append [:channel/by-id channel-id :channel/users]))))
      )
    {})
  (remote [env] true))

(defmutation add-chat-message
  "Mutation: Add a message to the current app state, and send it to the server. The server will push it to everyone else."
  [{:keys [db/id] :as new-message}]
  (action [{:keys [state]}]
    (let [state         state
          channel-id    (get @state :current-channel)
          message-ident [:message/by-id id]]
      (swap! state (fn [s] (-> s
                             (assoc-in message-ident new-message) ; add the message to our tables
                             (fc/integrate-ident message-ident :append [:app/channels channel-id :channel/messages])))))
    {})
  (remote [{:keys [ast state]}]
    (let [channel (get @state :current-channel)]
      (update ast :params assoc :channel channel))))

(defmutation change-channels [_]
  (action [env] "Not implemented")
  (remote [env] false))


