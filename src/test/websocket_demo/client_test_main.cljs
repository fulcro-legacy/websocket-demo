(ns websocket-demo.client-test-main
  (:require websocket-demo.tests-to-run
            [fulcro-spec.selectors :as sel]
            [fulcro-spec.suite :as suite]))

(enable-console-print!)

(suite/def-test-suite client-tests {:ns-regex #"websocket-demo..*-spec"}
  {:default   #{::sel/none :focused}
   :available #{:focused}})

