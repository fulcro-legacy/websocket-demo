(ns websocket-demo.server
  (:require
    [fulcro.easy-server :as easy]
    ; MUST require this, or you won't get API handlers installed
    [websocket-demo.api :as api]
    [fulcro.server :as server]
    [com.stuartsierra.component :as component]
    [fulcro.websockets.components.channel-server :as ws]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.util.response :as rsp :refer [response file-response resource-response]]
    [ring.middleware.params :as params]
    [ring.middleware.keyword-params :as keyword-params]
    [clojure.java.io :as io]))

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
                              (handler request))))
        wrap-root       (fn [handler] (fn [req] (handler (update req :uri #(if (= "/" %) "/index.html" %)))))]
    (-> (not-found-handler)
      (wrap-websockets)
      (server/wrap-transit-params)
      (server/wrap-transit-response)
      (keyword-params/wrap-keyword-params)
      (params/wrap-params)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)
      (wrap-root)
      (wrap-gzip))))

(defrecord Handler [config]
  component/Lifecycle
  (start [this]
    (assoc this :middleware (handler config)))
  (stop [this] nil))

(defn system [port]
  (component/system-map
    :server (easy/make-web-server)
    :channel-server (ws/simple-channel-server)
    :channel-listener (api/make-channel-listener)
    :config (server/new-config "config/dev.edn")
    :handler (component/using (map->Handler {}) [:config])))

(defn build-server
  [{:keys [port]}]
  (system port))

