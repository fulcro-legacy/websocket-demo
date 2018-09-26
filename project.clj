(defproject websocket-demo "0.1.0"
  :description "A Demo of Fulcro Websockets"
  :license {:name "MIT" :url "https://opensource.org/licenses/MIT"}
  :min-lein-version "2.7.0"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [fulcrologic/fulcro "2.6.3"]
                 [http-kit "2.2.0"]
                 [ring/ring-core "1.6.3"]
                 [bk/ring-gzip "0.3.0"]
                 [bidi "2.1.3"]
                 [commons-codec "1.11"]

                 [com.taoensso/sente "1.13.1" :exclusions [org.clojure/tools.reader]]]

  :uberjar-name "websocket_demo.jar"

  :source-paths ["src/main"]
  :test-paths ["src/test"]
  :clean-targets ^{:protect false} ["target" "resources/public/js"]
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-Xmx1024m" "-Xms512m"]

  ; Notes  on production build:
  ; - The hot code reload stuff in the dev profile WILL BREAK ADV COMPILATION. So, make sure you
  ; use `lein with-profile production cljsbuild once production` to build!
  :cljsbuild {:builds [{:id           "production"
                        :source-paths ["src/main"]
                        :jar          true
                        :compiler     {:asset-path    "js/prod"
                                       :main          websocket-demo.client-main
                                       :optimizations :advanced
                                       :source-map    "resources/public/js/websocket_demo.js.map"
                                       :output-dir    "resources/public/js/prod"
                                       :output-to     "resources/public/js/websocket_demo.js"}}]}

  :profiles {:uberjar    {:main           websocket-demo.server-main
                          :aot            :all
                          :jar-exclusions [#"public/js/prod" #"com/google.*js$"]
                          :prep-tasks     ["clean" ["clean"]
                                           "compile" ["with-profile" "production" "cljsbuild" "once" "production"]]}
             :production {}
             :dev        {:source-paths ["src/dev" "src/main"]

                          :figwheel     {:css-dirs ["resources/public/css"]}

                          :cljsbuild    {:builds
                                         [{:id           "dev"
                                           :figwheel     {:on-jsload "cljs.user/mount"}
                                           :source-paths ["src/dev" "src/main"]
                                           :compiler     {:asset-path           "js/dev"
                                                          :main                 cljs.user
                                                          :optimizations        :none
                                                          :output-dir           "resources/public/js/dev"
                                                          :output-to            "resources/public/js/websocket_demo.js"
                                                          :preloads             [devtools.preload #_fulcro.inspect.preload]
                                                          :external-config      {:fulcro.inspect/config {:launch-keystroke "ctrl-f"}}
                                                          :source-map-timestamp true}}]}

                          :plugins      [[lein-cljsbuild "1.1.7"]]

                          :dependencies [[binaryage/devtools "0.9.10"]
                                         [fulcrologic/fulcro-inspect "2.2.3"]
                                         [org.clojure/tools.namespace "0.3.0-alpha4"]
                                         [org.clojure/tools.nrepl "0.2.13"]
                                         [com.cemerick/piggieback "0.2.2"]
                                         [figwheel-sidecar "0.5.16" :exclusions [org.clojure/tools.reader]]]
                          :repl-options {:init-ns          user
                                         :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
