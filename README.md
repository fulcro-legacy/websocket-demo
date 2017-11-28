# Websockets Demo

A demo of Fulcro websockets. A very simple chat app.

## Layout

The main project source is in `src/main`. The source files have
comments to help you understand the overall structure.

```
├── config                    server configuration files. See Fulcro docs.
│   ├── defaults.edn
│   ├── dev.edn
│   └── prod.edn
├── websocket_demo
    ├── api.clj                read/mutation/push handlers for client and server
    ├── api.cljs
    ├── client.cljs            client creation (shared among dev/prod)
    ├── client_main.cljs       production client main
    ├── server.clj             server creation (shared among dev/prod)
    ├── server_main.clj        production server main
    └── ui.cljs                client UI
```

# Websockets

This is a demo chat-style app. Open this page in more than one tab/browser to see the result.

Websockets act as an alternate networking mechanism for Fulcro apps. As such, they change nothing about how
you write the majority of your application. Everything is still done with the same UI and mutation logic you've
already been doing.

Websockets ensure that each client has a persistent TCP connection to the server, and thus allow the server to push
updates to the client. The client handles these via a predefined multimethod as described below.

# Websockets Setup

There are several steps for setting up Fulcro to use websockets as the primary mechanism for app communication:

1. Set up the client to use websocket networking.
2. Add websockets support to your server
3. Add handlers to the client for receiving server push messages

When you've done this all API and server push traffic will go over the same persistent TCP websocket connection.

NOTE: You are allowed to install more than one networking handler, so it would be legal to have both a websocket
and normal XhrIO-based remote. Doing so just means your queries and mutations would then target the remote of
interest. However, it is not supported to have more than one websocket in a client.

## Setting up the client

To set up the client:

- Create the networking object.
- Add it to the network stack.
- In started callback: install the push handlers.

```
(def cs-net (wn/make-channel-client \"/chsk\" :global-error-callback (constantly nil)))

(fc/new-fulcro-client
:networking cs-net
:started-callback (fn [app] (wn/install-push-handlers cs-net app)))
```

The default route for establishing websockets is `/chsk`. The internals use Sente to provide the websockets.

## Adding Server Support

The server needs a couple of components and a hook for an extra route:

```
(core/make-fulcro-server
; provides the URI on which you've configured the client to connect:
:extra-routes {:routes   [" " {[\"/chsk\"] :web-socket}]
               :handlers {:web-socket cs/route-handlers}}
; Adds the components needed in order to establish and work with clients on persistent sockets
:components {:channel-server   (cs/make-channel-server)
             :channel-listener (wsdemo/make-channel-listener)}))
```

The channel listner is something you create: a component that is told when a client connects or drops. This will allow
you to manage your own internal data structures that track your active users.

Typically this will be a component that injects the channel server. This demo defines it as follows:

```
(defrecord ChannelListener [channel-server subscriptions]
WSListener
(client-dropped [this ws-net cid] ...)
(client-added [this ws-net cid] ...)

component/Lifecycle
(start [component]
  (let [component (assoc component
                    :subscriptions (atom {}))]
    (add-listener channel-server component)
    component))
(stop [component]
  (remove-listener channel-server component)
  (dissoc component :subscriptions :kill-chan)))

(defn make-channel-listener []
(component/using
  (map->ChannelListener {})
  [:channel-server]))
```

Note that the channel server is injected into the component and the `start`/`stop` methods use it to add/remove
the component as a listener of connect/drop events. The `ws-net` parameter is the channel server which
implements the WSNet protocol:

```
(defprotocol WSNet
(add-listener [this ^WSListener listener] \"Add a `WSListener` listener\")
(remove-listener [this ^WSListener listener] \"Remove a `WSListener` listener\")
(push [this cid verb edn] \"Push from server\"))
```

and it is this push method that is most interesting to us. It allows the server to push messages to a specific user
by client id (cid). The `verb` and `edn` parameters are what will arrive on the client.

## Handling Push Messages

Fulcro treats incoming push messages much like mutations, though on a different multimethod
`fulcro.websockets.networking/push-received`. The parameters to this method are the fulcro `app` and the
`message` (which contains the keywords `:topic` with the verb from the server and `:msg` with the EDN. The `:topic`
is used as the dispatch key for the multimethod).

So, a call on the server to `(push ws-net client-id :hello {:name \"Jo\"})` will result in a call on the client
of `(push-received fulcro-app {:topic :hello :msg {:name \"Jo\"}})`.

See Root in ui.cljs:

```
```

## Development Mode

Special code for working in dev mode is in `src/dev`, which is not on
the build for production builds.

Running all client builds:

```
JVM_OPTS="-Ddev" lein run -m clojure.main script/figwheel.clj
dev:cljs.user=> (log-app-state) ; show current state of app
dev:cljs.user=> :cljs/quit      ; switch REPL to a different build
```

Or use a plain REPL in IntelliJ with JVM options of `-Ddev` and parameters of
`script/figwheel.clj`.

Running the server:

Start a clj REPL in IntelliJ, or from the command line:

```
lein run -m clojure.main
user=> (go)
...
user=> (restart) ; stop, reload server code, and go again
user=> (tools-ns/refresh) ; retry code reload if hot server reload fails
```

The URL is then [http://localhost:3000](http://localhost:3000)

## Standalone Runnable Jar (Production, with advanced optimized client js)

```
lein uberjar
java -jar target/websocket_demo.jar
```
