(ns websocket-demo.client
  (:require [fulcro.client :as fc]
            [fulcro.websockets.networking :as wn]
            [fulcro.client.data-fetch :as df]
            [websocket-demo.ui :as ui]
            [websocket-demo.schema :as schema]
            [websocket-demo.api :as api]))

(defonce cs-net (wn/make-channel-client "/chsk" :global-error-callback (constantly nil)))

(defonce app (atom (fc/new-fulcro-client
                     :networking cs-net
                     :started-callback (fn [{:keys [reconciler] :as app}]
                                         ; Install the push handlers. If you don't do this, you won't see server pushes
                                         (wn/install-push-handlers cs-net app)
                                         ; Load the list of active users. Changes to this list happen through push notifications, but we need the initial set.
                                         (df/load app ::schema/users ui/User {:target [:root/all-users]})
                                         ; Load the chat room. This will have the current users and messages. The post mutation
                                         ; will set up the UI so it can display the active users.
                                         (df/load app ::schema/chat-room ui/ChatRoom {:post-mutation `api/link-active-users})))))

