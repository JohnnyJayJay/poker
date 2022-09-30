(ns poker.discord.waiting
  (:require [discljord.formatting :as dfmt]
            [discljord.messaging :as dr]
            [slash.component.structure :as cmp]
            [slash.response :as rsp]
            [clojure.string :as str]
            [clojure.core.async :as a :refer [chan go <!]]
            [poker.discord.state :refer [waiting-games active-games rest-conn app-id]]
            [poker.discord.i18n :as i18n]
            [poker.discord.playing :refer [start-game!]]
            [poker.discord.util :refer [respond-interaction]]
            [poker.discord.component :refer [handle-component-interaction]]))

(defn enough-participants? [{:keys [participants] :as _waiting-game}]
  (>= (count participants) 2))

(defn slots-left [{:keys [participants] {:keys [max-players]} :opts :as _waiting-game}]
  (- max-players (count participants)))

(def full? (comp zero? slots-left))

(defn participant? [{:keys [participants] :as _waiting-game} user-id]
  (some #{user-id} participants))

(defn format-budgets [{:keys [budgets] :as _game}]
  (str/join "\n" (map (fn [[user amount]] (str (dfmt/mention-user user) " - " amount " chips")) budgets)))

(defn waiting-game-message
  [{:keys [host participants start-timestamp guild-id prev-round]
    {:keys [buy-in closed]} :opts
    :as waiting-game}]
  (let [bundle (i18n/guild-bundle guild-id)
        host-mention (dfmt/mention-user host)
        slots-left (slots-left waiting-game)
        participant-list (str/join ", " (map dfmt/mention-user participants))
        start-timestamp (dfmt/timestamp (quot start-timestamp 1000) :relative-time)]
    {:content (str
               (if prev-round
                 (str (i18n/loc-msg bundle :waiting/existing-game (format-budgets prev-round))
                      \newline
                      (when-not closed (i18n/loc-msg bundle :waiting/open-game buy-in)))
                 (i18n/loc-msg bundle :waiting/new-game host-mention buy-in))
               \newline
               (i18n/loc-msg bundle :waiting/msg host-mention slots-left participant-list start-timestamp))
     :components
     [(cmp/action-row
       (cmp/button :primary "leave" :emoji {:name "❎"} :label (i18n/loc-msg bundle :waiting/leave))
       (cmp/button :primary "join" :emoji {:name "✅"} :label (i18n/loc-msg bundle :waiting/join) :disabled (zero? slots-left)))
      (cmp/action-row
       (cmp/button :danger "abort" :emoji {:name "⏹"} :label (i18n/loc-msg bundle :waiting/abort))
       (cmp/button :success "start" :emoji {:name "⏩"} :label (i18n/loc-msg bundle :waiting/start) :disabled (not (enough-participants? waiting-game))))]}))

(defmethod handle-component-interaction "join"
  [{:keys [channel-id id token guild-id] {{user-id :id} :user} :member :as _interaction}]
  (->> (if-let [{{:keys [budgets] :as prev-round} :prev-round {:keys [closed]} :opts :as waiting-game} (@waiting-games channel-id)]
         (cond
           ;; Already joined?
           (participant? waiting-game user-id) (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :waiting/already-joined)}) rsp/ephemeral)
           ;; Game already full? (should usually not occur because the join button will be disabled in that case)
           (full? waiting-game) (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :waiting/full)}) rsp/ephemeral)
           ;; Game is a continuation of a previous round, not open to new players and the user is a new player?
           (and prev-round (not (contains? budgets user-id)) closed) (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :waiting/no-new-players)}) rsp/ephemeral)
           ;; If none of the above, you're good to join
           :else (rsp/update-message (waiting-game-message (get (swap! waiting-games update-in [channel-id :participants] conj user-id) channel-id))))
         (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :bot/general-error)}) rsp/ephemeral))
       (respond-interaction id token)))

(defmethod handle-component-interaction "leave"
  [{:keys [channel-id id token guild-id] {{user-id :id} :user} :member :as _interaction}]
  (->> (if-let [waiting-game (@waiting-games channel-id)] ;; FIXME (for join too - make this a transaction over waiting-games)
         (if (participant? waiting-game user-id)
           (-> (rsp/update-message (waiting-game-message (get (swap! waiting-games update-in [channel-id :participants] #(filterv (complement #{user-id}) %)) channel-id))))
           (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :waiting/didnt-join)}) rsp/ephemeral))
         (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :bot/general-error)}) rsp/ephemeral))
       (respond-interaction id token)))

(defn handle-host-action!
  [{:keys [channel-id id token guild-id] {{user-id :id} :user} :member :as _interaction} key]
  (->> (if-let [{:keys [host] chan key :as _waiting-game} (@waiting-games channel-id)]
         (if (= user-id host)
           (do
             (a/close! chan)
             rsp/deferred-update-message)
           (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :waiting/not-host)}) rsp/ephemeral))
         (-> (rsp/channel-message {:content (i18n/loc-msg guild-id :bot/general-error)}) rsp/ephemeral))
       (respond-interaction id token)))

(defmethod handle-component-interaction "abort"
  [interaction]
  (handle-host-action! interaction :abort-chan))

(defmethod handle-component-interaction "start"
  [interaction]
  (handle-host-action! interaction :start-chan))

(declare init-new-round!)

(defn await-game-start! [channel-id]
  (let [{:keys [start-chan abort-chan message-id guild-id host] {:keys [wait-time timeout] :as opts} :opts} (@waiting-games channel-id)]
    (go
      ;; Wait for abort or start/timeout signal
      (let [[_ ch] (a/alts! [start-chan abort-chan (a/timeout wait-time)])
            ;; Remove game from waiting games map
            [{waiting-game channel-id}] (swap-vals! waiting-games dissoc channel-id)] ;; FIXME dosync w/ waiting-games AND active-games, return function from transaction
        (cond
          ;; if game was aborted
          (= ch abort-chan)
          (dr/edit-message! rest-conn channel-id message-id :content (i18n/loc-msg guild-id :waiting/aborted) :components [])

          ;; otherwise, wait time has run out or start button was pressed
          ;; -> check participant count
          (not (enough-participants? waiting-game))
          (dr/edit-message! rest-conn channel-id message-id :content (i18n/loc-msg guild-id :waiting/not-enough-players) :components [])

          ;; enough participants, we can start
          :else
          (do
            (dr/edit-message! rest-conn channel-id message-id :components [])
            (if-let [result (<! (start-game! waiting-game timeout))]
              (init-new-round! result opts guild-id channel-id host)
              (do
                (swap! active-games dissoc channel-id)
                #_(dr/create-message! rest-conn channel-id :content (i18n/loc-msg guild-id :bot/general-error))))))))))

(defn init-game! [id token {:keys [wait-time] :as opts} guild-id channel-id initiator-id]
  (go
    (let [abort-ch (chan)
          start-ch (chan)
          waiting-game {:host initiator-id,
                        :participants [initiator-id]
                        :token token,
                        :opts opts,
                        :start-timestamp (+ (System/currentTimeMillis) wait-time),
                        :guild-id guild-id,
                        :channel-id channel-id,
                        :abort-chan abort-ch,
                        :start-chan start-ch}
          _ (<! (respond-interaction id token (rsp/channel-message (waiting-game-message waiting-game))))
          {message-id :id} (<! (dr/get-original-interaction-response! rest-conn app-id token))]
      (if message-id
        (swap! waiting-games assoc channel-id (assoc waiting-game :message-id message-id))
        (dr/create-followup-message! rest-conn app-id token :content (i18n/loc-msg guild-id :bot/general-error)))
      (await-game-start! channel-id))))

(defn remove-bust-outs [game]
  (update game :budgets #(into {} (filter (comp pos? second) %))))

(defn init-new-round! [game {:keys [wait-time] :as opts} guild-id channel-id host-id]
  (go
    (let [abort-ch (chan)
          start-ch (chan)
          {:keys [budgets pots] :as game} (remove-bust-outs game)
          waiting-game {:host (if (contains? budgets host-id) host-id (reduce (partial max-key budgets) (mapcat :winners pots)))
                        :participants []
                        :opts opts
                        :start-timestamp (+ (System/currentTimeMillis) wait-time)
                        :guild-id guild-id
                        :channel-id channel-id
                        :prev-round game
                        :abort-chan abort-ch
                        :start-chan start-ch}
          {message-id :id} (<! (apply dr/create-message! rest-conn channel-id (mapcat identity (waiting-game-message waiting-game))))]
      (if message-id
        (swap! waiting-games assoc channel-id (assoc waiting-game :message-id message-id))
        (dr/create-message! rest-conn channel-id :content (i18n/loc-msg guild-id :bot/general-error)))
      (await-game-start! channel-id))))
