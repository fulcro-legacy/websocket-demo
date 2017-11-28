(ns cljs.user
  (:require
    [fulcro.client.core :as fc]
    [om.next :as om]
    [websocket-demo.client :as client]
    [websocket-demo.ui :as ui]
    [cljs.pprint :refer [pprint]]
    [fulcro.client.logging :as log]))

(enable-console-print!)

(log/set-level :all)

; User mode mount, to get the client on the app div of index.html. Defined as a function, because hot code reload calls it.
(defn mount [] (swap! client/app fc/mount ui/Root "app"))

; This is the initial mount
(mount)

(defn app-state [] @(:reconciler @client/app))

(defn log-app-state [& keywords]
  (pprint (let [app-state (app-state)]
            (if (= 0 (count keywords))
              app-state
              (select-keys app-state keywords)))))
