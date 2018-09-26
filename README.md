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

See `client.cljs`.

## Adding Server Support

See `server.clj` and `api.clj`.

## Pushing Messages

See `api.clj`. Specifically trace through `notify-others`.

## Handling Push Messages

See `push-received` in `api.cljs`.

## Other Thing In This Demo

This demo is meant to be a bit of a show-piece for several things in Fulcro:

1. Hand rolling a server with Ring.
2. Using websockets with that server to handle API traffic.
3. Websocket bidirection communication.
4. React refs for input focus.
5. Co-locating CSS on components.
6. Using some of the bootstrap3 helpers.
7. If you install the Fulcro Inspect chrome plugin, you can use it in Devtools to see networking, database, and more.
8. Using Clojure Spec to protect your APIs from accidents…and inform you when you screw up.
9. Associating a “login” with a websocket establish client.

The UI is fully defined in `ui.cljs`.

The source code is heavily commented.

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
