(defproject poker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Hippocratic 2.1"
            :url  "https://firstdonoharm.dev/version/2/1/license.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [org.clojure/core.match "1.0.0"]
                 [com.github.discljord/discljord "1.3.1"]
                 [org.apache.logging.log4j/log4j-core "2.17.1"]
                 [org.apache.logging.log4j/log4j-api "2.17.1"]
                 [com.github.johnnyjayjay/slash "0.5.0-SNAPSHOT"]
                 [com.github.johnnyjayjay/edn-bundle "0.1.1-SNAPSHOT"]
                 [mount "0.1.16"]
                 [datalevin "0.6.8"]]
  :jvm-opts ["-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"
             "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"]
  :main poker.discord.bot
  :repl-options {:init-ns poker.logic.game})
