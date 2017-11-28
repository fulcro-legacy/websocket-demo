(ns websocket-demo.ui
  (:require
    [fulcro.client.core :as fc :refer [defsc]]
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
    [garden.core :as g]
    [fulcro.client.logging :as log]))

(declare Root)                                              ; so we can pull CSS classnames from the localized root css

(defsc ^:once User [this {:keys [db/id ::schema/name]} {:keys [highlight?]} _]
  {:query [:db/id ::schema/name]
   :ident [:USER/BY-ID :db/id]}
  (bs/row nil
    (bs/col {:sm 2}
      (bs/label {:kind (if highlight? :primary :default)}
        (bs/glyphicon nil :user)))
    (bs/col {:sm 10} name)))

(def ui-user (om/factory User {:keyfn :db/id}))

(defui ^:once ActiveUsers
  static om/IQuery
  (query [this] [{[:root/current-user '_] (om/get-query User)}
                 {[:root/all-users '_] (om/get-query User)}])
  static om/Ident
  (ident [this props] [:UI-ACTIVE-USERS :UI])
  static fc/InitialAppState
  (initial-state [c params] {})
  Object
  (render [this]
    (let [{:keys [root/current-user root/all-users]} (om/props this)
          {:keys [full-height]} (css/get-classnames Root)
          ; this component will short-circuit rendering unless the list of users has changed, so this is ok to do here...
          all-users (sort-by ::name all-users)]
      (bs/panel {:className full-height}
        (bs/panel-heading nil
          (bs/panel-title nil "Active Users"))
        (bs/panel-body nil
          (map (fn [u]
                 (ui-user (om/computed u {:highlight? (= u current-user)}))) all-users))))))

(def ui-active-users (om/factory ActiveUsers {:keyfn :db/id}))

(defsc ChatRoomMessage [this {:keys [db/id ::schema/message ::schema/name]} _ _]
  {:query [:db/id ::schema/message ::schema/name]
   :ident [:MESSAGE/BY-ID :db/id]}
  (bs/row nil
    (bs/col {:sm 2}
      (bs/label nil name))
    (bs/col {:sm 10} message)))

(def ui-message (om/factory ChatRoomMessage {:keyfn :db/id}))

(defn new-message-control [component send-message new-message sender-name]
  (bs/row {}
    (bs/col {:sm 10}
      (dom/input #js {:type      "text" :value new-message :id "new-message" :placeholder "Message" :className "form-control"
                      :onChange  #(m/set-string! component :ui/new-message :event %)
                      :onKeyDown (fn [evt] (when (evt/enter-key? evt) (send-message)))}))
    (bs/col {:sm 2} (bs/button {:kind :primary :onClick send-message} "Send!"))))

(defui ^:once ChatRoom
  static om/IQuery
  (query [_] [:db/id
              :ui/new-message
              {::schema/chat-room-messages (om/get-query ChatRoomMessage)}
              ::schema/chat-room-title
              {[:root/current-user '_] (om/get-query User)}
              {:active-user-panel (om/get-query ActiveUsers)}])
  static om/Ident
  (ident [_ {:keys [db/id]}] [:CHAT-ROOM/BY-ID id])
  static css/CSS
  (local-rules [this] [[:.drop-parent {:position "relative"}]
                       [:.overlay {:z-index 1 :position "relative"}]
                       [:.scrolling-messages {:overflow "scroll" :position "absolute" :width "100%" :top "50px" :bottom "40px"}]
                       [:.drop-footer {:position "absolute" :width "100%" :bottom "0"}]])
  (include-children [this] [])
  Object
  (componentDidMount [this] (when-let [message-pane (.-message-pane this)] (set! (.-scrollTop message-pane) (.-scrollHeight message-pane))))
  (componentDidUpdate [this pprops pstate]
    ; scroll to bottom
    (when-let [message-pane (.-message-pane this)] (set! (.-scrollTop message-pane) (.-scrollHeight message-pane))))
  (render [this]
    (let [{:keys [ui/new-message root/current-user ::schema/chat-room-messages ::schema/chat-room-title active-user-panel]} (om/props this)
          {:keys [full-height]} (css/get-classnames Root)
          {:keys [drop-parent drop-footer scrolling-messages overlay]} (css/get-classnames ChatRoom)
          sender-name  (::schema/name current-user)
          send-message (fn send-message []
                         (m/set-string! this :ui/new-message :value "")
                         (om/transact! this `[(api/add-chat-message ~{:db/id (om/tempid) ::schema/message new-message ::schema/name sender-name})]))]
      (bs/row {}
        (bs/col {:sm 4}
          (ui-active-users active-user-panel))
        (bs/col {:sm 8}
          (bs/panel {:className (str drop-parent " " full-height)}
            (bs/panel-heading {:kind :primary :className overlay}
              (bs/panel-title {:className overlay} (dom/h4 nil chat-room-title)))
            (bs/panel-body {:className scrolling-messages
                            :ref       (fn set-message-pane-ref [r] (when r (set! (.-message-pane this) r)))}
              (map ui-message chat-room-messages))
            (bs/panel-footer {:className drop-footer} (new-message-control this send-message new-message sender-name))))))))

(def ui-chat-room (om/factory ChatRoom))

(defsc LoginForm [this {:keys [db/id ui/username]} _ _]
  {:query         [:ui/username :db/id]
   :initial-state {:db/id :UI :ui/username ""}
   :css           [[:.login-class {:margin-top "10em"}]]
   :ident         [:LOGIN-FORM-UI :db/id]}
  ;(componentDidMount [this] (when-let [inp (.-login-input this)] (.focus inp)))
  (let [{:keys [login-class]} (css/get-classnames LoginForm)
        login (fn []
                (m/set-string! this :ui/username :value "")
                (om/transact! this `[(api/login ~{:db/id (om/tempid) ::schema/name username}) :root/current-user :root/all-users]))]
    (bs/row {:className login-class}
      (bs/col {:sm 6 :sm-offset 3}
        (bs/panel {:kind :success}
          (bs/panel-heading {}
            (bs/panel-title nil "Welcome to the Chat Application!"))
          (bs/panel-body {:className "form-horizontal"}
            (bs/labeled-input {:id        "login" :type "text"
                               :value     username
                               :ref       (fn [r] (set! (.-login-input this) r)) ; caches the real DOM element, for focus
                               :split     3
                               :onChange  #(m/set-string! this :ui/username :event %)
                               :onKeyDown (fn [evt] (when (evt/enter-key? evt) (login)))} "Who are you?")
            (bs/labeled-input {:id              "join"
                               :split           3
                               :input-generator #(bs/button {:kind :primary :onClick login} "Join Chat Room")} "")))))))

(def ui-login-form (om/factory LoginForm {:keyfn :db/id}))

(defn style-element
  "Returns a React Style element with the (recursive) CSS of the given component. Useful for directly embedding in your UI VDOM."
  [component]
  (dom/style (clj->js {:dangerouslySetInnerHTML {:__html (g/css (css/get-css component))}})))

(defsc Root [this {:keys [ui/react-key root/current-user root/login-form ::schema/chat-room]} _ _]
  {:query         [:ui/react-key
                   {:root/all-users (om/get-query User)}
                   {:root/login-form (om/get-query LoginForm)}
                   {:root/current-user (om/get-query User)}
                   {::schema/chat-room (om/get-query ChatRoom)}]
   :css           [[:.full-height {:height "100vh"}]]
   :css-include   [LoginForm ActiveUsers ChatRoom]
   :initial-state {:root/login-form {} :root/all-users [] ::schema/chat-room {}}}
  (dom/div nil
    (style-element Root)
    (dom/div #js {:key react-key :className "container-fluid"}
      (if (empty? current-user)
        (ui-login-form login-form)
        (ui-chat-room chat-room)))))

