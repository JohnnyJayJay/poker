(ns poker.discord.display
  "The display namespace contains functions to create chat messages for poker games in Discord.
  The emotes used for the cards come from the Playing Card Emojis Discord server:
  https://top.gg/servers/623564336052568065"
  (:require [poker.logic.pots :as pots]
            [poker.logic.game :as poker]
            [clojure.string :as strings]))

(def ^:private black-ranks
  {:ace   623575870375985162
   :two   623564440574623774
   :three 623564440545263626
   :four  623564440624824320
   :five  623564440851316760
   :six   623564440679350319
   :seven 623564440754978843
   :eight 623564440826150912
   :nine  623564440868225025
   :ten   623564440620630057
   :jack  623564440951980084
   :queen 623564440851185679
   :king  623564440880807956})

(def ^:private red-ranks
  {:ace   623575868672835584
   :two   623564440989859851
   :three 623564440880545798
   :four  623564441103106058
   :five  623564440868225035
   :six   623564440759173121
   :seven 623564440964694036
   :eight 623564440901779496
   :nine  623564440897454081
   :ten   623564440863899663
   :jack  623564440582881282
   :queen 623564440880807936
   :king  623564441073614848})

(defn- with-suit [rank-map suit]
  (reduce-kv (fn [acc rank id]
               (assoc acc (conj [suit] rank) id))
             {}
             rank-map))

(def upper-halves
  (merge (with-suit black-ranks :clubs)
         (with-suit black-ranks :spades)
         (with-suit red-ranks :hearts)
         (with-suit red-ranks :diamonds)
         {[nil nil] 714565166070759454}))

(def lower-halves
  {:clubs    623564441224740866
   :spades   623564441094586378
   :hearts   623564441065226267
   :diamonds 623564440926683148
   nil       714565093798576455})

(defn- emote-mention [id]
  (str "<:_:" id ">"))

(defn- halves-str [cards upper]
  (let [keyfn (if upper (juxt :suit :rank) :suit)
        halves-map (if upper upper-halves lower-halves)]
    (strings/join
      " "
      (->> cards
           (map keyfn)
           (map halves-map)
           (map emote-mention)))))

(defn cards->str
  ([cards fill-to]
   (let [cards (concat cards (repeat (- fill-to (count cards)) nil))]
     (str
       (halves-str cards true)
       "\n"
       (halves-str cards false))))
  ([cards] (cards->str cards 0)))

(defn user-mention [id]
  (str "<@" id ">"))

(defn- pots->str [pots]
  (strings/join
    "\n"
    (map (fn [{:keys [name money]}]
           (str "**" name ":** `" money "` chips"))
         pots)))

(defn game-state-message
  [{:keys [community-cards] :as game}]
  (str
    "**Community Cards:**\n"
    (cards->str community-cards 5) "\n"
    (pots->str (:pots (pots/flush-bets game)))))

(defn- move->str [{:keys [action cost]}]
  (str (strings/capitalize (name action)) " - `" cost "` chips"))

(defn turn-message
  [{[player-id] :cycle :keys [budgets] :as game}]
  (str
    "It's your turn, " (user-mention player-id) "!\n"
    "What would you like to do? You still have `" (budgets player-id) "` chips.\n"
    (strings/join "\n" (map move->str (poker/possible-moves game)))))

(defn instant-win-message
  [{[{[winner] :winners :keys [money]}] :pots}]
  (str
    "Everybody except " (user-mention winner) " has folded!\n"
    "They win the main pot of `" money "` chips."))

(defn- hands->str [hands]
  (strings/join
    "\n"
    (map (fn [[player-id {:keys [name cards]}]]
           (str (user-mention player-id) " - " name "\n" (cards->str cards)))
         hands)))

(defn- pot-win->str
  [{[winner & more :as winners] :winners :keys [prize name]}]
  (if more
    (str (strings/join ", " (map user-mention winners)) " split the " name " for `" prize "` chips each!")
    (str (user-mention winner) " wins the " name " and gets `" prize "` chips!")))

(defn- wins->str [pots]
  (strings/join "\n" (map pot-win->str pots)))

(defn showdown-message
  [{:keys [hands pots]}]
  (str
    "**Showdown!** Let's have a look at the hands...\n\n"
    (hands->str hands)
    "\n\nThis means that:\n"
    (wins->str pots)))

(defn player-notification-message
  [{:keys [order player-cards budgets]} player-id]
  (str
    "Hi " (user-mention player-id) ", here are your cards for this game:\n"
    (cards->str (player-cards player-id)) "\n"
    "You have a budget of `" (budgets player-id) "` chips.\n"
    "We're playing no-limit Texas hold'em. You can read up on the rules here:\n"
    "<https://en.wikipedia.org/wiki/Texas_hold_%27em>\n\n"
    "Those are the participants, in order: " (strings/join ", " (map user-mention order)) "\n"
    "**Have fun!** :black_joker:"))

(def handshake-emoji "\uD83E\uDD1D")

(defn new-game-message [player-id timeout buy-in]
  (str
    (user-mention player-id) " wants to play Poker!\n"
    "You have " (quot timeout 1000) " seconds to join by reacting with :handshake:!\n"
    "Everybody will start with `" buy-in "` chips."))

(defn blinds-message [{:keys [big-blind small-blind big-blind-value]}]
  (str
    (user-mention small-blind) " places the small blind of `" (quot big-blind-value 2) "` chips.\n"
    (user-mention big-blind) " places the big blind of `" big-blind-value "` chips."))


(defn- budgets->str [budgets]
  (strings/join
    "\n"
    (map (fn [[player-id budget]]
           (str (user-mention player-id) " - `" budget "` chips"))
         budgets)))

(defn restart-game-message [{:keys [budgets]} timeout buy-in]
  (str
    "This round of the game is over, but you can keep playing!\n"
    "Players of the last round, you now have:\n"
    (budgets->str budgets) "\n\n"
    "You will enter the next round with this if you continue playing.\n"
    "New players can also join! They will start with `" buy-in "` chips.\n"
    "If you want to play, react with :handshake: within the next " (quot timeout 1000) " seconds."))

(defn already-ingame-message [user-id]
  (str "You are already in a game, " (user-mention user-id) "!"))

(defn channel-mention [id]
  (str "<#" id ">"))

(defn channel-occupied-message [channel-id user-id]
  (str "There already is an active poker session in " (channel-mention channel-id) ", " (user-mention user-id)))

(defn channel-waiting-message [channel-id user-id]
  (str "There already is a game waiting for players to join in " (channel-mention channel-id)
       ". Maybe you want to join there, " (user-mention user-id) "?"))

(defn invalid-raise-message [game]
  (let [minimum (poker/minimum-raise game)]
    (str "You must raise to an amount between `" minimum "` and `"
         (poker/possible-bet game) "` chips. E.g.: `raise " minimum "`")))

(defn info-message [user-id]
  (str
    "Hi, " (user-mention user-id) "!\n"
    "I am a Discord bot that allows you to play Poker (No limit Texas hold' em) against up to 19 other people in chat. "
    "To start a new game, simply type `holdem! <buy-in amount>`. The (optional) buy-in is the amount of chips everyone will start with.\n"
    "Grab a bunch of friends and try it out!\n\n"
    "I am open source and written in the Clojure programming language. "
    "You can find my code here: https://github.com/JohnnyJayJay/poker/"))

