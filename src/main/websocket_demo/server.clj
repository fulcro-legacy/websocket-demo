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
  "Ring middleward. Requires a config object because the web sockets support looks up config parameters like whitelisting in there.
  Returns Ring middleware"
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

; The web server component injects :handler, and expects it to have a :middleware key. This is how the components hook
; middleware into the raw server component. If you used your own server code, you'd depend on this and pull out middleware...
(defrecord Handler [config]
  component/Lifecycle
  (start [this]
    (assoc this :middleware (handler config)))
  (stop [this] nil))

(defn system []
  (component/system-map
    :server (easy/make-web-server)                          ; read the code...it is quite simple. Just expects injection of :config and :handler
    :channel-server (ws/simple-channel-server)              ; the actual Sente component
    :channel-listener (api/make-channel-listener)           ; A component that injects the channel server, and subscribes to traffic
    :config (server/new-config "config/prod.edn")           ; the config
    :handler (component/using (map->Handler {}) [:config]))) ; the middleware (injectable into server)

(defn build-server [] (system))

