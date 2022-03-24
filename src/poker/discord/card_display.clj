(ns poker.discord.card-display
  (:require [discljord.formatting :as dfmt]
            [clojure.string :as str]
            [poker.discord.i18n :as i18n]
            [poker.logic.pots :as pots]))

(def black-ranks
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

(def red-ranks
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

(defn- halves-str [cards upper separate-at]
  (let [keyfn (if upper (juxt :suit :rank) :suit)
        halves-map (if upper upper-halves lower-halves)
        emotes (->> cards
                    (map keyfn)
                    (map halves-map)
                    (map dfmt/mention-emoji))]
    (str/join
      " "
      (if separate-at
        (let [[left right] (split-at separate-at emotes)]
          (concat left ["    "] right))
        emotes))))

(defn cards->str
  ([cards separate-at fill-to]
   (let [cards (concat cards (repeat (- fill-to (count cards)) nil))]
     (str
       (halves-str cards true separate-at)
       "\n"
       (halves-str cards false separate-at))))
  ([cards separate-at] (cards->str cards separate-at 0))
  ([cards] (cards->str cards nil)))

(defn pot-name [bundle num]
  (if (zero? num)
    (i18n/loc-msg bundle :term/main-pot)
    (i18n/loc-msg bundle :term/side-pot num)))

(defn- pots->str [bundle pots]
  (str/join
    "\n"
    (map (fn [{:keys [name money]}]
           (str "**" (pot-name bundle name) ":** " (i18n/loc-msg bundle :term/chip-amount money)))
         pots)))

(defn game-state-message
  [bundle {:keys [community-cards] :as game}]
  (str
    (dfmt/bold (i18n/loc-msg bundle :term/community-cards)) ":\n"
    (cards->str community-cards nil 5) "\n"
    (pots->str bundle (:pots (pots/flush-bets game)))))

(defn instant-win-message
  [bundle {[{[winner] :winners :keys [money]}] :pots}]
  (i18n/loc-msg bundle :playing/instant-win (dfmt/mention-user winner) money))

(defn- pot-win->str
  [bundle {[winner & more :as winners] :winners :keys [prize name]}]
  (let [pot-name (pot-name bundle name)]
    (if more
      (i18n/loc-msg bundle :playing/split (str/join ", " (map dfmt/mention-user winners)) pot-name prize)
      (i18n/loc-msg bundle :playing/single (dfmt/mention-user winner) pot-name prize))))

(defn- wins->str [guild-id pots]
  (str/join "\n" (map (partial pot-win->str guild-id) pots)))

(defn showdown-messages
  [bundle {:keys [hands pots player-cards]}]
  (concat
   [(i18n/loc-msg bundle :playing/showdown)]
   (->> hands
        (map (fn [[player-id {:keys [name cards]}]]
           (str (dfmt/mention-user player-id) " - " (i18n/loc-msg bundle name) "\n"
                (cards->str (concat cards (player-cards player-id)) 5))))
        (partition-all 4)
        (str/join "\n"))
   [(wins->str bundle pots)]))

(defn timed-out-message [bundle {[current] :cycle}]
  (i18n/loc-msg bundle :playing/auto-fold (dfmt/mention-user current)))
