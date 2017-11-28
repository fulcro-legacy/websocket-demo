(ns websocket-demo.client
  (:require [om.next :as om]
            [fulcro.client.core :as fc]
            [fulcro.websockets.networking :as wn]
            [fulcro.client.data-fetch :as df]
            [websocket-demo.ui :as ui]
            [websocket-demo.schema :as schema]
            [websocket-demo.api :as api]
            [fulcro.client.logging :as log]))

(defonce cs-net (wn/make-channel-client "/chsk" :global-error-callback (constantly nil)))

(defonce app (atom (fc/new-fulcro-client
                     :networking cs-net
                     :started-callback (fn [{:keys [reconciler] :as app}]
                                         (log/info "Installing push handlers")
                                         (wn/install-push-handlers cs-net app)
                                         (df/load app ::schema/users ui/User {:target [:root/all-users]})
                                         (df/load app ::schema/chat-room ui/ChatRoom {:post-mutation `api/link-active-users})))))

