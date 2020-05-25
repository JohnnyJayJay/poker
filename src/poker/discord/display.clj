(ns poker.discord.display
  (:require [clojure.string :as strings]))

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
         (with-suit red-ranks :diamonds)))

(def lower-halves
  {:clubs    623564441224740866
   :spades   623564441094586378
   :hearts   623564441065226267
   :diamonds 623564440926683148})

(def lower-back 714561447602028624)

(def upper-back 714561543173570661)

(defn- emote-mention [id]
  (str "<:_:" id ">"))

(defn- halves-str [cards upper fill-to]
  (let [keyfn (if upper (juxt :suit :rank) :suit)
        halves-map (if upper upper-halves lower-halves)
        back (if upper upper-back lower-back)]
    (strings/join
      " "
      (as-> cards cards
            (map keyfn cards)
            (map halves-map cards)
            (concat cards (repeat (- fill-to (count cards)) back))
            (map emote-mention cards)))))

(defn cards->str
  ([cards fill-to]
   (str
     (halves-str cards true fill-to)
     \n
     (halves-str cards false fill-to)))
  ([cards] (cards->str cards 0)))
