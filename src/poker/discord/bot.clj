(ns poker.discord.bot
  (:require [poker.logic.game :as poker]
            [poker.logic.core :as cards]
            [poker.discord.display :as disp]
            [discljord.connections :as conns]
            [discljord.messaging :as msgs]
            [discljord.events :as events]
            [discljord.formatting :refer [mention-user strike-through]]
            [discljord.permissions :as perms]
            [discljord.events.state :refer [prepare-guild]]
            [clojure.core.async :as async]
            [clojure.string :as strings]
            [clojure.set :as sets]
            [clojure.edn :as edn]))

(defonce connection-ch (atom nil))
(defonce message-ch (atom nil))
(defonce config (atom nil))

(defonce active-games (atom {}))
(defonce waiting-channels (atom #{}))

(defn calculate-budgets [players buy-in previous-budgets]
  (zipmap (sets/difference (set players) (set (keys previous-budgets))) (repeat buy-in)))

(defn in-game? [user-id]
  (some #(contains? % user-id) (map :players (vals @active-games))))

(defn send-message! [channel-id content]
  (msgs/create-message! @message-ch channel-id :content content))

(defn game-loop! [game]
  (async/go-loop [{:keys [state channel-id move-channel] :as game} game]
    (swap! active-games assoc channel-id game)
    (send-message! channel-id (disp/game-state-message game))
    (if (poker/end? game)
      (do
        (send-message! channel-id ((case state :instant-win disp/instant-win-message :showdown disp/showdown-message) game))
        (swap! active-games dissoc channel-id)
        game)
      (do
        (send-message! channel-id (disp/turn-message game))
        (async/alt!
          (async/timeout (:timeout @config)) (do
                                               (send-message! channel-id (disp/timed-out-message game))
                                               (recur (poker/fold game)))
          move-channel ([{:keys [action amount]}]
                        (recur (case action
                                 (:check :call) (poker/call game)
                                 :all-in (poker/all-in game)
                                 :fold (poker/fold game)
                                 :raise (poker/raise game amount)))))))))

(defn has-permissions [channel-id]
  (async/go
    (let [{:keys [guild-id]} (async/<! (msgs/get-channel! @message-ch channel-id))
          guild (prepare-guild (async/<! (msgs/get-guild! @message-ch guild-id)))
          {:keys [id]} (async/<! (msgs/get-current-user! @message-ch))]
      (perms/has-permissions?
        #{:send-messages :read-message-history :add-reactions :use-external-emojis}
        guild id channel-id))))


(defn gather-players! [channel-id message-id]
  (msgs/create-reaction! @message-ch channel-id message-id disp/handshake-emoji)
  (async/go
    (async/<! (async/timeout (:wait-time @config)))
    (when (async/<! (has-permissions channel-id))
      (->> (async/<! (msgs/get-reactions! @message-ch channel-id message-id disp/handshake-emoji :limit 20))
           (remove :bot)
           (map :id)
           (remove in-game?)))))

(defn notify-players! [{:keys [players] :as game}]
  (async/go
    (doseq [player players
            :let [{dm-id :id} (async/<! (msgs/create-dm! @message-ch player))]]
      (send-message! dm-id (disp/player-notification-message game player)))))

(defn remove-bust-outs [game]
  (update game :budgets #(into {} (filter (comp pos? second) %))))

(defn start-game! [channel-id buy-in start-message start-fn]
  (async/go
    (let [{join-message-id :id} (async/<! (send-message! channel-id start-message))]
      (swap! waiting-channels conj channel-id
        (let [players (async/<! (gather-players! channel-id join-message-id))]
          (swap! waiting-channels disj channel-id)
          (if (> (count players) 1)
            (let [game (assoc (start-fn players) :channel-id channel-id :move-channel (async/chan))]
              (notify-players! game)
              (send-message! channel-id (disp/blinds-message game))
              (let [{:keys [budgets] :as result} (-> game game-loop! async/<! remove-bust-outs)]
                (start-game!
                  channel-id buy-in
                  (disp/restart-game-message result (:wait-time @config) buy-in)
                  #(poker/restart-game result % (shuffle cards/deck) (calculate-budgets % buy-in budgets)))))
            (msgs/edit-message! @message-ch channel-id join-message-id :content (str (strike-through start-message) \newline "Not enough players."))))))))

(defmulti handle-event (fn [type _] type))

(defn try-parse-int [str]
  (try
    (Integer/parseInt str)
    (catch NumberFormatException _ nil)))

(defmulti
  handle-command
  (fn [command _ _ _]
    (strings/lower-case command)))

(defn valid-move [{[current] :cycle :as game} user-id command]
  (and (= current user-id) (some #(and (= command (name (:action %))) %) (poker/possible-moves game))))

; TODO better and more specific solution for move commands

(doseq [move-cmd ["fold" "check" "call" "all-in"]]
  (defmethod handle-command move-cmd [_ _ user-id channel-id]
    (let [{:keys [move-channel] :as game} (get @active-games channel-id)]
      (when-let [move (valid-move game user-id move-cmd)]
        (async/>!! move-channel move)))))

(defmethod handle-command "raise" [_ args user-id channel-id]
  (let [{:keys [move-channel] :as game} (get @active-games channel-id)]
    (when-let [move (valid-move game user-id "raise")]
      (if-let [amount (try-parse-int (get args 0))]
        (if (<= (poker/minimum-raise game) amount (poker/possible-bet game))
          (async/>!! move-channel (assoc move :amount amount))
          (send-message! channel-id (disp/invalid-raise-message game)))
        (send-message! channel-id (disp/invalid-raise-message game))))))

(defmethod handle-command "holdem!" [_ args user-id channel-id]
  (cond
    (contains? @active-games channel-id) (send-message! channel-id (disp/channel-occupied-message channel-id user-id))
    (contains? @waiting-channels channel-id) (send-message! channel-id (disp/channel-waiting-message channel-id user-id))
    (in-game? user-id) (send-message! channel-id (disp/already-ingame-message user-id))
    :else (let [buy-in (max 100 (or (try-parse-int (get args 0)) (:default-buy-in @config)))
                big-blind (quot buy-in 100)]
            (start-game!
              channel-id buy-in
              (disp/new-game-message user-id (:wait-time @config) buy-in)
              #(poker/start-new-game big-blind % (shuffle cards/deck) (calculate-budgets % buy-in {}))))))

(defmethod handle-event :default [_ _])

(defmethod handle-command :default [_ _ _ _])

(defmethod handle-event :message-create
  [_ {{author-id :id bot? :bot} :author :keys [channel-id content]}]
  (if-not bot?
    (let [split (strings/split content #"\s+")]
      (handle-command (first split) (subvec split 1) author-id channel-id))))

(defn def-ping-commands [bot-id]
  (let [mention (mention-user bot-id)]
    (doseq [command [mention (strings/replace mention "@" "@!")]]
      (defmethod handle-command command [_ _ user-id channel-id]
        (send-message! channel-id (disp/info-message user-id))))))

(defmethod handle-event :ready [_ _]
  (let [{bot-id :id bot-name :username} @(msgs/get-current-user! @message-ch)]
    (def-ping-commands bot-id)
    (conns/status-update! @connection-ch :activity (conns/create-activity :type :music :name (str \@ bot-name)))))

(defn- start-bot! [{:keys [token] :as config}]
  (reset! poker.discord.bot/config config)
  (let [event-ch (async/chan 100)
        connection-ch (conns/connect-bot! token event-ch :intents #{:guild-messages})
        message-ch (msgs/start-connection! token)]
    (reset! poker.discord.bot/connection-ch connection-ch)
    (reset! poker.discord.bot/message-ch message-ch)
    (events/message-pump! event-ch handle-event)
    (msgs/stop-connection! message-ch)
    (conns/disconnect-bot! connection-ch)))

(defn -main [& args]
  (start-bot! (edn/read-string (slurp "./config.clj"))))
