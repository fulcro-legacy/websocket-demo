(ns websocket-demo.server-main
  (:require
    [com.stuartsierra.component :as component]
    [fulcro.server :as c]
    [taoensso.timbre :as timbre]
    [websocket-demo.server :refer [build-server]])
  (:gen-class))

;; This is a separate file for the uberjar only. We control the server in dev mode from src/dev/user.clj
(defn -main [& args]
  (let [system (build-server)]
    (component/start system)))
