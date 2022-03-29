(ns poker.discord.bot
  (:require [poker.discord.state :refer [active-games waiting-games event-ch rest-conn ws-conn config event-pool]]
            [poker.discord.waiting :as waiting]
            [poker.discord.i18n :as i18n]
            [poker.discord.util :refer [respond-interaction]]
            [poker.discord.component :refer [handle-component-interaction]]
            [poker.discord.playing :refer [handle-raise-submit]]
            [mount.core :as mount]
            [discljord.connections :as dc]
            [discljord.formatting :as dfmt]
            [discljord.messaging :as dr]
            [discljord.permissions :as dp]
            [discljord.events :as devents]
            [discljord.events.state :as ds]
            [slash.command :refer [defhandler paths group]]
            [slash.gateway :as slash-ws]
            [slash.core :as slash]
            [slash.response :as rsp]
            [slash.component.structure :as cmp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go <!]])
  (:gen-class))

(defn validate-opts [defaults opts]
  (let [{:keys [big-blind small-blind buy-in] :as opts} (-> (merge defaults opts) (update :wait-time * 1000) (update :timeout * 1000))
        {:keys [big-blind small-blind] :as updated-opts}
        (cond
          (and (nil? big-blind) (nil? small-blind))
          (assoc opts :big-blind (quot buy-in 20) :small-blind (quot buy-in 50))

          (nil? big-blind)
          (assoc opts :big-blind (* small-blind 2))

          (nil? small-blind)
          (assoc opts :small-blind (quot big-blind 2))

          :else opts)]
    (cond
      (> small-blind big-blind)
      :command.game.error/blinds-proportion

      (<= buy-in (max small-blind big-blind))
      :command.game.error/blinds-buy-in

      :else updated-opts)))

;; TODO use caching instead
(defn has-permissions? [channel-id]
  (go
    (let [{:keys [parent-id guild-id type] :as channel} (<! (dr/get-channel! rest-conn channel-id))]
      (case type
        ;; for threads, check the parent channel permissions and join the thread
        11 (do
             (dr/join-thread! rest-conn channel-id)
             (<! (has-permissions? parent-id)))

        ;; for regular text channels, check the permissions
        0 (let [{:keys [id]} (<! (dr/get-current-user! rest-conn))
              member (<! (dr/get-guild-member! rest-conn guild-id id))
              guild (-> (<! (dr/get-guild! rest-conn guild-id))
                        (assoc :members [member])
                        (assoc :channels [channel])
                        (ds/prepare-guild))]
          (dp/has-permissions?
           #{:send-messages :use-external-emojis}
           guild id channel-id))

        ;; for all other types of channels, deny
        false))))

(defhandler holdem-handler ["holdem"]
  {:keys [id token channel-id guild-id] {{user-id :id} :user} :member :as _interaction}
  opts
  (condp contains? channel-id ;; FIXME put in transaction
    ;; Is there already an active game in this channel?
    @active-games (->> (rsp/channel-message {:content (i18n/loc-msg guild-id :command.game/active-game)})
                       rsp/ephemeral
                       (respond-interaction id token))
    ;; Is there already a waiting game in this channel?
    @waiting-games (->> (rsp/channel-message {:content (i18n/loc-msg guild-id :command.game/waiting-game)})
                        rsp/ephemeral
                        (respond-interaction id token))
    ;; else
    (let [validated-opts (validate-opts (:defaults config) opts)]
      (if (keyword? validated-opts)
        (->> {:content (i18n/loc-msg guild-id validated-opts)}
             rsp/channel-message
             rsp/ephemeral
             (respond-interaction id token))
        (go
          (if (<! (has-permissions? channel-id))
            (waiting/init-game! id token validated-opts guild-id channel-id user-id)
            (->> {:content (i18n/loc-msg guild-id :bot/missing-permissions)}
                 rsp/channel-message
                 (respond-interaction id token))))))))


(defhandler info-handler ["info"]
  {:keys [id token guild-id] :as _interaction}
  _
  (let [bundle (i18n/guild-bundle guild-id)]
    (->> {:content (i18n/loc-msg bundle :command.info/message)
          :components [(cmp/action-row
                        (cmp/link-button
                         (:invite-url config)
                         :label (i18n/loc-msg bundle :command.info/add)
                         :emoji {:name "🃏"})
                        (cmp/link-button
                         (:source-url config)
                         :label (i18n/loc-msg bundle :command.info/source)
                         :emoji {:name "🛠"})
                        (cmp/link-button
                         (:server-url config)
                         :label (i18n/loc-msg bundle :command.info/server)
                         :emoji {:name "❓"})
                        (cmp/link-button
                         (:support-url config)
                         :label (i18n/loc-msg bundle :command.info/support)
                         :emoji {:name "💸"}))]}
         rsp/channel-message
         rsp/ephemeral
         (respond-interaction id token))))

(def language-select-components
  [(cmp/action-row
    (cmp/select-menu
     "language-select"
     [(cmp/select-option "English" "en" :emoji {:name "🇬🇧"})]))])

(defhandler language-handler ["language"]
  {:keys [id token guild-id] {:keys [permissions]} :member}
  _
  (->> (if (dp/has-permission-flag? :manage-guild permissions)
         {:content (i18n/loc-msg guild-id :command.language/select) :components language-select-components}
         {:content (i18n/loc-msg guild-id :command.language/no-perms)})
       rsp/channel-message
       rsp/ephemeral
       (respond-interaction id token)))

(defmethod handle-component-interaction "language-select"
  [{:keys [id token guild-id] {[lang] :values} :data}]
  ;; TODO actually update - guild settings are atom that is dumped periodically to a file
  (->> {:content (i18n/loc-msg guild-id :command.language/updated)
        :components []}
       rsp/update-message
       (respond-interaction id token)))

(defn handle-message-command
  [_ {:keys [content id channel-id guild-id]}]
  (when (str/starts-with? content "holdem!")
    (dr/create-message!
      rest-conn
      channel-id
      :content (i18n/loc-msg guild-id :command.holdem/deprecation-notice)
      :components
      [(cmp/action-row
        (cmp/link-button (:invite-url config)
                         :label (i18n/loc-msg guild-id :command.holdem/commands-button)
                         :emoji {:name "🤖"}))]
      :message-reference {:message_id id :guild_id guild-id :channel_id channel-id})))

(defn handle-ready [_ {[shard-id] :shard :as _ready-event}]
  (dc/status-update!
   ws-conn
   :activity (dc/create-activity :name "/holdem" :type :music)
   :shards #{shard-id}))

(def interaction-handlers
  (assoc slash-ws/gateway-defaults
         :application-command (paths #'holdem-handler (group ["poker"] #'info-handler #'language-handler))
         :message-component #'handle-component-interaction
         :modal-submit #'handle-raise-submit))

(def event-handlers
  {:interaction-create [#(slash/route-interaction interaction-handlers %2)]
   :message-create [#'handle-message-command]
   :ready [#'handle-ready]})

(defn -main [& _args]
  (log/info "Starting bot")
  (mount/start)
  (log/info "Logged in as" (dfmt/user-tag @(dr/get-current-user! rest-conn)))
  (try
    (devents/message-pump! event-ch #(.execute event-pool (fn [] (devents/dispatch-handlers event-handlers %1 %2))))
    (finally (mount/stop))))
