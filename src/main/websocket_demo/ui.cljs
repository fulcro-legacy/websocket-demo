(ns websocket-demo.ui
  (:require
    [fulcro.client.core :as fc]
    [fulcro.client.data-fetch :as df]
    [fulcro.client.mutations :as m]
    [websocket-demo.api :as api]
    [websocket-demo.schema :as schema]
    [fulcro.websockets.networking :as wn]
    [fulcro.ui.bootstrap3 :as bs]
    [om.dom :as dom]
    [om.next :as om :refer [defui]]
    [fulcro-css.css :as css]
    [fulcro.events :as evt]
    [garden.core :as g]))

(defui ^:once User
  static om/IQuery
  (query [_] [:db/id ::schema/name])
  static om/Ident
  (ident [_ {:keys [db/id]}] [:user/by-id id])
  Object
  (render [this]
    (let [{:keys [db/id ::schema/name]} (om/props this)
          {:keys [:highlight?]} (om/get-computed this)]
      (bs/row nil
        (bs/col {:sm 2}
          (bs/label {:kind (if highlight? :primary :default)}
            (bs/glyphicon nil :user)))
        (bs/col {:sm 10} name)))))

(def ui-user (om/factory User {:keyfn :db/id}))

(defui ^:once ActiveUsers
  static om/IQuery
  (query [this] [{[:current-user '_] (om/get-query User)}
                 {[:all-users '_] (om/get-query User)}])
  static om/Ident
  (ident [this props] [:active-users :UI])
  static css/CSS
  (local-rules [this] [])
  (include-children [this] [])
  static fc/InitialAppState
  (initial-state [c params] {})
  Object
  (render [this]
    (let [{:keys [current-user all-users]} (om/props this)
          ; this component will short-circuit rendering unless the list of users has changed, so this is ok to do here...
          all-users (sort-by ::name all-users)]
      (bs/panel {}
        (bs/panel-title nil "Active Users")
        (bs/panel-body nil
          (map (fn [u]
                 (ui-user (om/computed u {:highlight? (= u current-user)}))) all-users))))))

(def ui-active-users (om/factory ActiveUsers {:keyfn :db/id}))

(defui ^:once ChatRoomMessage
  static om/IQuery
  (query [_] [:db/id ::schema/message ::schema/name])
  static om/Ident
  (ident [_ {:keys [db/id]}] [:message/by-id id])
  Object
  (render [this]
    (let [{:keys [db/id ::schema/message ::schema/name]} (om/props this)]
      (bs/row nil
        (bs/col {:sm 2}
          (bs/label nil name))
        (bs/col {:sm 10} message)))))

(def ui-message (om/factory ChatRoomMessage {:keyfn identity}))

(defn new-message-control [component new-message]
  (dom/div #js {:className "form-inline"} " "
    (dom/div #js {:className "form-group"} " "
      (dom/div #js {:className "input-group"}
        (dom/div #js {:className "input-group-addon"} "Message: ")
        (dom/input #js {:type  "text" :onChange #(m/set-string! component :ui/new-message :event %)
                        :value new-message :id "new-message" :placeholder "Message" :className "form-control"})))
    (dom/button #js {:type      "submit"
                     :onClick   (fn []
                                  (m/set-string! component :ui/message "")
                                  (om/transact! component `[(api/add-chat-message ~{:db/id           (om/tempid)
                                                                                    ::schema/message new-message
                                                                                    ::schema/name    name})]))
                     :className "btn btn-primary"} "Send!")))

(defui ^:once ChatRoom
  static om/IQuery
  (query [_] [:db/id
              :ui/new-message
              {::schema/chat-room-messages (om/get-query ChatRoomMessage)}
              ::schema/chat-room-title
              {:active-user-panel (om/get-query ActiveUsers)}])
  static om/Ident
  (ident [_ {:keys [db/id]}] [:chat-room/by-id id])
  Object
  (render [this]
    (let [{:keys [:ui/new-message ::schema/chat-room-messages ::schema/chat-room-title active-user-panel]} (om/props this)]
      (bs/row {}
        (bs/col {:sm 4} (ui-active-users active-user-panel))
        (bs/col {:sm 8}
          (bs/panel {}
            (bs/panel-title nil chat-room-title)
            (bs/panel-body nil
              (map ui-message chat-room-messages))
            (bs/panel-footer {} (new-message-control this new-message))))))))

(def ui-chat-room (om/factory ChatRoom))

(defui ^:once LoginForm
  static om/IQuery
  (query [this] [:ui/username])
  static om/Ident
  (ident [this props] [:login-form :UI])
  static css/CSS
  (local-rules [this] [[:.login-class {:margin-top "10em"}]])
  (include-children [this] [])
  static fc/InitialAppState
  (initial-state [c params] {:ui/username ""})
  Object
  (componentDidMount [this] (when-let [inp (.-login-input this)] (.focus inp)))
  (render [this]
    (let [{:keys [:db/id :ui/username]} (om/props this)
          {:keys [login-class]} (css/get-classnames LoginForm)
          login (fn []
                  (m/set-string! this :ui/username :value "")
                  (om/transact! this `[(api/login {:db/id (om/tempid) ::schema/name username})]))]
      (bs/row {:className login-class}
        (bs/col {:sm 6 :sm-offset 3}
          (bs/panel {}
            (bs/panel-heading {} "Welcome to the Chat Application!")
            (bs/panel-body {:className "form-horizontal"}
              (bs/labeled-input {:id        "login" :type "text"
                                 :value     username
                                 :ref       (fn [r] (set! (.-login-input this) r)) ; caches the real DOM element, for focus
                                 :split     3
                                 :onKeyDown (fn [evt] (when (evt/enter-key? evt) (login)))
                                 :onChange  #(m/set-string! this :ui/username :event %)} "Who are you?")
              (bs/labeled-input {:id              "join"
                                 :split           3
                                 :input-generator #(bs/button {:onClick login} "Join Chat Room")} ""))))))))

(def ui-login-form (om/factory LoginForm {:keyfn :db/id}))

(defn style-element
  "Returns a React Style element with the (recursive) CSS of the given component. Useful for directly embedding in your UI VDOM."
  [component]
  (dom/style (clj->js {:dangerouslySetInnerHTML {:__html (g/css (css/get-css component))}})))

(defui ^:once Root
  static fc/InitialAppState
  (initial-state [c p] {:login-form        (fc/get-initial-state LoginForm {})
                        ::schema/chat-room (fc/get-initial-state ChatRoom {})})
  static css/CSS
  (local-rules [this] [])
  (include-children [this] [LoginForm])
  static om/IQuery
  (query [this] [:ui/react-key
                 {:login-form (om/get-query LoginForm)}
                 {:current-user (om/get-query User)}
                 {::schema/chat-room (om/get-query ChatRoom)}])
  Object
  (render [this]
    (let [{:keys [ui/react-key current-user login-form ::schema/chat-room]} (om/props this)]
      (dom/div nil
        (style-element Root)
        (dom/div #js {:key react-key :className "container-fluid"}
          (if (empty? current-user)
            (ui-login-form login-form)
            (ui-chat-room chat-room)))))))

