(ns websocket-demo.client
  (:require [fulcro.client :as fc]
            [fulcro.websockets :as wn]
            [fulcro.client.data-fetch :as df]
            [websocket-demo.ui :as ui]
            [websocket-demo.schema :as schema]
            [websocket-demo.api :as api]))

(defonce app (atom (fc/new-fulcro-client
                     :networking (wn/make-websocket-networking {:push-handler api/push-received})
                     :started-callback (fn [{:keys [reconciler] :as app}]
                                         (reset! api/reconciler reconciler)
                                         ; Load the list of active users. Changes to this list happen through push notifications, but we need the initial set.
                                         (df/load app ::schema/users ui/User {:target [:root/all-users]})
                                         ; Load the chat room. This will have the current users and messages. The post mutation
                                         ; will set up the UI so it can display the active users.
                                         (df/load app ::schema/chat-room ui/ChatRoom {:post-mutation `api/link-active-users})))))

