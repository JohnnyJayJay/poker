(defproject poker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Hippocratic 2.1"
            :url  "https://firstdonoharm.dev/version/2/1/license.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [org.suskalo/discljord "0.2.8"]]
  :jvm-opts ["-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]
  :main poker.discord.bot
  :repl-options {:init-ns poker.logic.game})