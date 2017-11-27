(ns websocket-demo.client
  (:require [om.next :as om]
            [fulcro.client.core :as fc]
            [fulcro.websockets.networking :as wn]
            [fulcro.client.data-fetch :as df]))

(defonce cs-net (wn/make-channel-client "/chsk" :global-error-callback (constantly nil)))

(defonce app (atom (fc/new-fulcro-client
                     :networking cs-net
                     :started-callback (fn [{:keys [reconciler] :as app}]
                                         (wn/install-push-handlers cs-net app)))))

