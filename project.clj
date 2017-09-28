(defproject dialog-play-bot-for-layer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [duct/core "0.6.1"]
                 [duct/module.logging "0.3.1"]
                 [duct/module.web "0.6.2"]
                 [duct/module.ataraxy "0.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.7.0"]
                 [environ "1.1.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [clj-time "0.14.0"]]
  :plugins [[duct/lein-duct "0.10.2"]]
  :main ^:skip-aot dialog-play-bot-for-layer.main
  :uberjar-name  "dialog-play-bot-for-layer-standalone.jar"
  :resource-paths ["resources" "target/resources"]
  :prep-tasks     ["javac" "compile" ["run" ":duct/compiler"]]
  :profiles
  {:dev  [:project/dev :profiles/dev]
   :repl {:prep-tasks   ^:replace ["javac" "compile"]
          :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :profiles/dev {}
   :project/dev  {:source-paths   ["dev/src"]
                  :resource-paths ["dev/resources"]
                  :dependencies   [[integrant/repl "0.2.0"]
                                   [eftest "0.3.1"]
                                   [kerodon "0.8.0"]
                                   [com.gearswithingears/shrubbery "0.4.1"]]}})
