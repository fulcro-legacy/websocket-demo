(ns websocket-demo.ui
  (:require
    [fulcro.client :as fc]
    [fulcro.client.data-fetch :as df]
    [fulcro.client.mutations :as m]
    [websocket-demo.api :as api]
    [websocket-demo.schema :as schema]
    [fulcro.websockets.networking :as wn]
    [fulcro.ui.bootstrap3 :as bs]
    [fulcro.client.dom :as dom]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro-css.css :as css]
    [fulcro.events :as evt]
    [garden.core :as g]
    [fulcro.ui.html-entities :as entities]
    [fulcro.client.logging :as log]))

(declare Root)                                              ; so we can pull CSS classnames from the localized root css

(defsc User [this {:keys [db/id ::schema/name]} {:keys [highlight?]} _]
  {:query [:db/id ::schema/name]
   :css   [[:.user-label {:font-size "10pt"}]               ; co-located CSS. This localizes the classnames to namespace/class.
           [:.user-row {:height "20pt"}]]
   :ident [:USER/BY-ID :db/id]}                             ; I'm using upper-case for table names to make them easier to spot in app state
  (let [{:keys [user-label user-row]} (css/get-classnames User)] ; this translates from the long localized names back to a simple binding.
    (bs/row {:className user-row}                           ; the bindings for the long class names are just strings
      (bs/col {:sm 12}
        (bs/label {:className user-label :kind (if highlight? :primary :default)}
          (bs/glyphicon {:size ".9em"} :user)
          (str " " name))))))

(def ui-user (prim/factory User {:keyfn :db/id}))

(defsc ActiveUsers [this {:keys [root/current-user root/all-users]}]
  ; this component only has link queries. Unfortunately , these won't work if there isn't *something* in app state for this component
  ; In order to fix that, we have to have a post-mutation after we load the chat room (since this is embedded below the chat room)
  {:query         [{[:root/current-user '_] (prim/get-query User)} {[:root/all-users '_] (prim/get-query User)}]
   :ident         (fn [] [:UI-ACTIVE-USERS :UI])
   :initial-state {}}
  (let [{:keys [full-height]} (css/get-classnames Root)     ; get classnames from the top
        all-users (sort-by ::schema/name all-users)]        ; this component will short-circuit rendering unless the list of users has changed, so this is ok to do here...
    (bs/panel {:className full-height}
      (bs/panel-heading nil
        (bs/panel-title nil "Active Users"))
      (bs/panel-body nil
        (map (fn [u]
               (ui-user (prim/computed u {:highlight? (= u current-user)}))) all-users)))))

(def ui-active-users (prim/factory ActiveUsers {:keyfn :db/id}))

(defsc ChatRoomMessage [this {:keys [db/id ::schema/message ::schema/name]}]
  {:query [:db/id ::schema/message ::schema/name]
   :ident [:MESSAGE/BY-ID :db/id]}
  (bs/row nil
    (bs/col {:sm 12} (bs/label nil name) (str " " message))))

(def ui-message (prim/factory ChatRoomMessage {:keyfn :db/id}))

(defn new-message-control [component send-message new-message sender-name]
  (bs/row {}
    (bs/col {:sm 10}
      (dom/input #js {:type      "text" :value new-message :id "new-message" :placeholder "Message" :className "form-control"
                      :ref       "new-message-input"        ; string ref can be looked up with om/react-ref. See lifecycle method in ChatRoom
                      :onChange  #(m/set-string! component :ui/new-message :event %)
                      :onKeyDown (fn [evt] (when (evt/enter-key? evt) (send-message)))}))
    (bs/col {:sm 2} (bs/button {:kind :primary :onClick send-message} "Send!"))))

(defsc ChatRoom [this {:keys [ui/new-message root/current-user ::schema/chat-room-messages ::schema/chat-room-title active-user-panel]} _ {:keys [drop-parent drop-footer scrolling-messages overlay]}]
  {:query              [:db/id :ui/new-message ::schema/chat-room-title
                        {::schema/chat-room-messages (prim/get-query ChatRoomMessage)}
                        {[:root/current-user '_] (prim/get-query User)}
                        {:active-user-panel (prim/get-query ActiveUsers)}]
   :ident              [:CHAT-ROOM/BY-ID :db/id]
   :css                [[:.drop-parent {:position "relative"}]
                        [:.overlay {:z-index 1 :position "relative"}]
                        [:.scrolling-messages {:overflow "scroll" :position "absolute" :width "100%" :top "50px" :bottom "40px"}]
                        [:.drop-footer {:position "absolute" :width "100%" :bottom "0"}]]
   :css-include        [User]
   :componentDidMount  (fn []
                         ; Make sure the messages are all the way scrolled. Using non-string refs here (which don't work with input elements, but are fine on divs)
                         (when-let [message-pane (.-message-pane this)]
                           (set! (.-scrollTop message-pane) (.-scrollHeight message-pane)))
                         ; Make sure the input has focus
                         (when-let [inp (prim/react-ref this "new-message-input")]
                           (.focus (js/ReactDOM.findDOMNode inp))))
   :componentDidUpdate (fn [pprops pstate]                  ; scroll to bottom on each new message
                         (when-let [message-pane (.-message-pane this)]
                           (set! (.-scrollTop message-pane) (.-scrollHeight message-pane))))}
  (let [{:keys [full-height]} (css/get-classnames Root)
        sender-name  (::schema/name current-user)
        send-message (fn send-message []
                       (m/set-string! this :ui/new-message :value "")
                       (prim/transact! this `[(api/add-chat-message ~{:db/id (prim/tempid) ::schema/message new-message ::schema/name sender-name})]))]
    (bs/row {}
      (bs/col {:sm 4}
        (ui-active-users active-user-panel))
      (bs/col {:sm 8}
        (bs/panel {:className (str drop-parent " " full-height)}
          (bs/panel-heading {:kind :primary :className overlay}
            (bs/panel-title {:className overlay} (dom/h4 nil chat-room-title)))
          (bs/panel-body {:className scrolling-messages
                          ; recommended ref usage, but does not currently work on Om inputs
                          :ref       (fn set-message-pane-ref [r] (when r (set! (.-message-pane this) r)))}
            (map ui-message chat-room-messages))
          (bs/panel-footer {:className drop-footer} (new-message-control this send-message new-message sender-name)))))))

(def ui-chat-room (prim/factory ChatRoom))

(defsc LoginForm [this {:keys [db/id ui/username]} _ _]
  {:query             [:ui/username :db/id]
   :initial-state     {:db/id :UI :ui/username ""}
   :css               [[:.login-class {:margin-top "10em"}]]
   :ident             [:LOGIN-FORM-UI :db/id]
   :componentDidMount (fn [] (when-let [inp (prim/react-ref this "login-input")]
                               (.focus (js/ReactDOM.findDOMNode inp))))}
  (let [{:keys [login-class]} (css/get-classnames LoginForm)
        login (fn []
                (m/set-string! this :ui/username :value "") ; clear the input
                ; do local/remote login mutation. Note the tempid. This will get remapped to a normal ID on server return.
                ; the keywords at the end cause refreshes of the UI that query for those bits
                (prim/transact! this `[(api/login ~{:db/id (prim/tempid) ::schema/name username}) :root/current-user :root/all-users]))]
    (bs/row {:className login-class}
      (bs/col {:sm 6 :sm-offset 3}
        (bs/panel {:kind :success}
          (bs/panel-heading {}
            (bs/panel-title nil "Welcome to the Chat Application!"))
          (bs/panel-body {:className "form-horizontal"}
            (bs/labeled-input {:id        "login" :type "text"
                               :value     username
                               :ref       "login-input"     ; caches the real DOM element, for focus
                               :split     3                 ; Bootstrap split. 3/9 small columns
                               :onChange  #(m/set-string! this :ui/username :event %)
                               :onKeyDown (fn [evt] (when (evt/enter-key? evt) (login)))} "Who are you?")
            (bs/labeled-input {:id              "join"
                               :split           3
                               :input-generator #(bs/button {:kind :primary :onClick login} "Join Chat Room")} "")))))))

(def ui-login-form (prim/factory LoginForm {:keyfn :db/id}))

(defn style-element
  "Returns a React Style element with the (recursive) CSS of the given component. Useful for directly embedding in your UI VDOM."
  [component]
  (dom/style (clj->js {:dangerouslySetInnerHTML {:__html (g/css (css/get-css component))}})))

(defsc Root [this {:keys [ui/react-key root/current-user root/login-form ::schema/chat-room]} _ _]
  {:query         [:ui/react-key
                   {:root/all-users (prim/get-query User)}
                   {:root/login-form (prim/get-query LoginForm)}
                   {:root/current-user (prim/get-query User)}
                   {::schema/chat-room (prim/get-query ChatRoom)}]
   :css           [[:.full-height {:height "95vh" :margin-top "5px"}]]
   ; these can really be composed from any components. If you want general composition, you'd compose them at each level to preserve refactoring and such.
   :css-include   [LoginForm ChatRoom]
   :initial-state {:root/login-form {} :root/all-users [] ::schema/chat-room {}}}
  (dom/div nil
    (style-element Root)                                    ; embed the localized CSS rules here
    (dom/div #js {:key react-key :className "container-fluid"}
      (if (empty? current-user)
        (ui-login-form login-form)
        (ui-chat-room chat-room)))))

