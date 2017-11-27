(ns websocket-demo.server
  (:require
    [fulcro.easy-server :as easy]
    ; MUST require these, or you won't get them installed.
    [websocket-demo.api.read]
    [websocket-demo.api.mutations]
    [fulcro.server :as server]
    [com.stuartsierra.component :as component]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.java.io :as io]
    [om.next.server :as om]
    [taoensso.timbre :as timbre]
    [fulcro.server :as server]
    [bidi.bidi :as bidi]
    [org.httpkit.server :refer [run-server]]
    [fulcro.websockets.components.channel-server :as ws]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.util.response :as rsp :refer [response file-response resource-response]]
    [ring.middleware.params :as params]
    [ring.middleware.keyword-params :as keyword-params]))

(defn not-found-handler []
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/html"}
     :body    (io/file (io/resource "public/not-found.html"))}))

(defn handler
  [config]
  (let [wrap-websockets (fn [handler]
                          (fn [request]
                            (if (= "/chsk" (:uri request))
                              (ws/route-handlers {:config config :request request} {})
                              (handler request))))]
    (-> (not-found-handler)
      (wrap-websockets)
      (server/wrap-transit-params)
      (server/wrap-transit-response)
      (keyword-params/wrap-keyword-params)
      (params/wrap-params)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)
      (wrap-gzip))))

(defrecord Handler [config]
  component/Lifecycle
  (start [this]
    (assoc this :middleware (handler config)))
  (stop [this] nil))

(defn system [port]
  (component/system-map
    :server (easy/make-web-server)
    :websockets (ws/simple-channel-server)
    :config (server/new-config "config/dev.edn")
    :handler (component/using (map->Handler {}) [:config])))

(defn build-server
  [{:keys [port]}]
  (system port))



