(ns poker.discord.playing
  (:require [poker.discord.component :refer [handle-component-interaction]]
            [poker.discord.i18n :as i18n]
            [poker.discord.state :refer [rest-conn active-games]]
            [poker.discord.card-display :as disp]
            [poker.discord.util :refer [respond-interaction]]
            [poker.logic.core :as cards]
            [poker.logic.game :as poker]
            [clojure.core.async :as a :refer [go go-loop <! >!]]
            [clojure.set :as set]
            [discljord.formatting :as dfmt]
            [discljord.messaging :as dr]
            [slash.component.structure :as cmp]
            [slash.response :as rsp]))

(defn status-message [game]
  {})

(defn turn-message
  [bundle {[player-id] :cycle :keys [budgets] :as game}]
  (let [moves (poker/possible-moves game)
        move-map (zipmap (map :action moves) (map :cost moves))]
    {:content (i18n/loc-msg bundle :playing/turn (dfmt/mention-user player-id) (budgets player-id))
     :components
     [(cmp/action-row
       (cmp/button
        :secondary "view"
        :label (i18n/loc-msg bundle :playing/view-cards)))
      (cmp/action-row
       (cmp/button
        :danger "fold"
        :label (i18n/loc-msg bundle :playing.move/fold))
       (if (zero? (poker/required-bet game))
         (cmp/button
          :secondary "check"
          :label (i18n/loc-msg bundle :playing.move/check)
          :disabled (not (:check move-map)))
         (cmp/button
          :secondary "call"
          :label (str (i18n/loc-msg bundle :playing.move/call)
                      (when-let [cost (:call move-map)] (str " (" cost ")")))
          :disabled (not (:call move-map))))
       (cmp/button
        :primary "raise"
        :label (str (i18n/loc-msg bundle :playing.move/raise)
                    (when-let [cost (:raise move-map)]
                      (str " (" (i18n/loc-msg bundle :playing.move.raise/min) " " cost ")")))
        :disabled (not (:raise move-map)))
       (cmp/button
        :primary "all-in"
        :label (str (i18n/loc-msg bundle :playing.move/all-in) " (" (:all-in move-map) ")")))]}))


(defmethod handle-component-interaction "view"
  [{:keys [id token channel-id guild-id] {{user-id :id} :user} :member}]
  (when-let [{{cards user-id} :player-cards :as _game} (@active-games channel-id)]
    (->>
     {:content
      (if cards
        (disp/cards->str cards)
        (i18n/loc-msg guild-id :playing/not-a-player))}
     rsp/channel-message
     rsp/ephemeral
     (respond-interaction id token))))

(defn valid-move [game move-name]
  (->> (poker/possible-moves game) (filter (comp #{move-name} name :action)) first))

(defn handle-move-input [{:keys [id token channel-id guild-id] {{user-id :id} :user} :member} move-name if-valid]
  (when-let [{[current] :cycle :as game} (@active-games channel-id)]
    (let [allowed? (= current user-id)
          move (and allowed? (valid-move game move-name))]
      (cond
        (not allowed?) (respond-interaction id token (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :playing/not-your-turn)}) rsp/ephemeral))
        (not move) (respond-interaction id token (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :bot/general-error)}) rsp/ephemeral))
        :else
        (if-valid game move)))))

(doseq [move-cmd ["fold" "check" "call" "all-in"]]
  (defmethod handle-component-interaction move-cmd
    [{:keys [id token guild-id] {{user-id :id} :user} :member :as interaction}]
    (handle-move-input
     interaction
     move-cmd
     (fn [{:keys [move-chan]} {:keys [cost] :as move}]
       (go
         (respond-interaction
          id token
          (rsp/update-message
           {:content (dfmt/block-quote
                      (i18n/loc-msg
                      guild-id
                      (keyword "playing.move.done" move-cmd)
                      (dfmt/mention-user user-id)
                      cost))
            :components []}))
         (a/put! move-chan move))))))

(defmethod handle-component-interaction "raise"
  [{:keys [id token guild-id] :as interaction}]
  (let [bundle (i18n/guild-bundle guild-id)]
    (handle-move-input
     interaction
     "raise"
     (fn [{:keys [message-id] :as game} _]
       (respond-interaction
        id token
        (rsp/modal
         (i18n/loc-msg bundle :playing.move/raise)
         message-id
         (cmp/action-row
          (cmp/text-input
           :short
           "amount"
           (i18n/loc-msg bundle :playing.move.raise/amount)
           :required true
           :placeholder (i18n/loc-msg bundle :playing.move.raise/constraints (poker/minimum-raise game) (poker/possible-bet game))))))))))

(defn handle-raise-submit
  [{:keys [id token channel-id guild-id] {:keys [custom-id components]} :data {{user-id :id} :user} :member}]
  (when-let [{:keys [message-id move-chan] :as game} (@active-games channel-id)]
    (if (= custom-id message-id)
      (if-let [amount (try (Long/parseLong (-> components first :components first :value))
                           (catch NumberFormatException _ nil))]
        (go
          (respond-interaction id token (rsp/update-message {:components [] :content (dfmt/block-quote (i18n/loc-msg guild-id :playing.move.done/raise (dfmt/mention-user user-id) amount))}))
          (a/put! move-chan {:action :raise :amount amount}))
        (respond-interaction id token (-> {:content (i18n/loc-msg guild-id :playing.move.raise/invalid-amount)} rsp/channel-message rsp/ephemeral)))
      (respond-interaction id token (-> {:content (i18n/loc-msg guild-id :playing.move.raise/passed)} rsp/channel-message rsp/ephemeral)))))

(defn game-loop! [bundle timeout game]
  (go-loop [{:keys [state channel-id move-chan] :as game} game]
    ;; Show community cards and pots
    (dr/create-message! rest-conn channel-id :content (disp/game-state-message bundle game))
    (if (poker/end? game)
      ;; End game appropriately
      (do
        (run!
         #(dr/create-message! rest-conn channel-id :content %)
         ((case state :instant-win (comp vector disp/instant-win-message) :showdown disp/showdown-messages) bundle game))
        (swap! active-games dissoc channel-id)
        game)
      ;; Send turn message, wait for player move
      (let [msg (<! (apply dr/create-message! rest-conn channel-id (mapcat identity (turn-message bundle game))))]
        ;; Update game state for the outside
        (swap! active-games assoc channel-id (assoc game :message-id (:id msg)))
        (a/alt!
          ;; Either player times out (no move)
          (a/timeout timeout) (do
                                (dr/create-message! rest-conn channel-id :content (disp/timed-out-message bundle game))
                                (recur (poker/fold game)))
          ;; or they make a move
          move-chan ([{:keys [action amount]}]
                     (recur (case action
                              (:check :call) (poker/call game)
                              :all-in (poker/all-in game)
                              :fold (poker/fold game)
                              :raise (poker/raise game amount)))))))))

(defn calculate-budgets [players buy-in previous-budgets]
  (zipmap (set/difference (set players) (set (keys previous-budgets))) (repeat buy-in)))


(defn notify-players!
  [guild-bundle {:keys [player-cards channel-id] :as _game}]
  (doseq [[user-id cards] player-cards]
    (go
      (let [{dm-id :id} (<! (dr/create-dm! rest-conn user-id))]
        (dr/create-message!
         rest-conn dm-id
         :content (str (i18n/loc-msg guild-bundle :playing/notification (dfmt/mention-channel channel-id))
                       \newline
                       (disp/cards->str cards)))))))

(defn start-game!
  [{:keys [prev-round guild-id participants channel-id] {:keys [buy-in small-blind big-blind]} :opts :as waiting-game} timeout]
  (let [budgets (calculate-budgets participants buy-in (:budgets prev-round {}))
        cards (shuffle cards/deck)
        game (-> (if prev-round
                   (poker/restart-game prev-round participants cards budgets)
                   (poker/start-new-game big-blind small-blind participants cards budgets))
                 (assoc :channel-id channel-id :move-chan (a/chan)))
        guild-bundle (i18n/guild-bundle guild-id)]
    (swap! active-games assoc channel-id game)
    (notify-players! guild-bundle game)
    (dr/create-message! rest-conn channel-id
                        :content
                        (str (i18n/loc-msg guild-bundle :playing/small-blind (dfmt/mention-user (:small-blind game)) small-blind) \newline
                             (i18n/loc-msg guild-bundle :playing/big-blind (dfmt/mention-user (:big-blind game)) big-blind))
                        :allowed-mentions {})
    (game-loop! guild-bundle timeout game)))
