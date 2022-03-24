(ns poker.discord.state
  (:require [mount.core :refer [defstate]]
            [clojure.edn :as edn]
            [clojure.core.async :as async]
            [discljord.messaging :as dr]
            [discljord.connections :as dg])
  (:import (java.util.concurrent Executors ExecutorService)))

(defstate config
  :start (edn/read-string (slurp "config.clj")))

(defstate rest-conn
  :start (dr/start-connection! (:token config))
  :stop (dr/stop-connection! rest-conn))

(defstate app-id
  :start (:id @(dr/get-current-user! rest-conn)))

(defstate event-ch
  :start (async/chan (:buffer-size config)))

(defstate ws-conn
  :start (dg/connect-bot! (:token config) event-ch :intents #{:guild-messages})
  :stop (dg/disconnect-bot! ws-conn))

;; channel id -> {poker stuff, :move-chan chan, :channel-id id}
(defstate active-games
  :start (atom {}))

;; channel id -> {:host <initiator-id>, :participants #{set of user ids},
;; :abort-chan chan, :start-chan chan, :game-opts {opts}, :interaction-token "token", :guild-id id, :channel-id id}
(defstate waiting-games
  :start (atom {}))

(defstate ^ExecutorService event-pool
  :start (Executors/newFixedThreadPool 4)
  :stop (.shutdown event-pool))
