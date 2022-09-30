(ns poker.logic.hands
  "The poker.hands namespace contains functions to evaluate and compare
  poker hands (collections of 5 cards). The hand-matching functions generally
  return the given hand in an appropriate order if it matches and nil otherwise."
  (:require [clojure.math.combinatorics :as combs]
            [clojure.string :as string]
            [poker.logic.util :refer [highest compare-desc]]))

(defn- occurrence-fn
  "Returns a function that takes a hand of cards and evaluates the rank-based occurrence requirement.
  n is the number of times a rank must occur in the hand, amount is the number of matching n-groups
  that is required.
  E.g., if n is 2 and amount is 1, the returned function will return an appropriately sorted version
  of the given hand if it has *one* *pair* of the same ranks and else nil."
  [n amount]
  (fn [hand]
    (let [rank-freqs (frequencies (map :rank hand))
          matching-ranks (set (filter (comp #{n} rank-freqs) (keys rank-freqs)))]
      (if (= (count matching-ranks) amount)
        (sort-by (juxt (comp boolean matching-ranks :rank) :index) compare-desc hand)))))

(defn highcard
  "Hand-matching function for high-card hands.
  Never returns nil, because every hand is a valid high-card hand.
  Just sorts the cards in descending order."
  [hand]
  (sort-by :index compare-desc hand))

(def pair
  "Hand-matching function for one pair-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not contain exactly one pair."
  (occurrence-fn 2 1))

(def two-pairs
  "Hand-matching function for two pair-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not contain exactly two pairs."
  (occurrence-fn 2 2))

(def three-of-a-kind
  "Hand-matching function for three of a kind-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not contain three of a kind."
  (occurrence-fn 3 1))

(def four-of-a-kind
  "Hand-matching function for four of a kind-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not contain four of a kind."
  (occurrence-fn 4 1))

(defn full-house
  "Hand-matching function for full house-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not contain a full house (one pair and three of a kind)."
  [hand]
  (and (pair hand) (three-of-a-kind hand)))

(defn flush
  "Hand-matching function for flush-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not represent a flush (all cards having the same suit)."
  [[{suit :suit} :as hand]]
  (if (every? #{suit} (map :suit hand))
    (highcard hand)))

(defn- all-neighbours
  "Returns the given cards if they are in descending order, nil if not."
  [cards]
  (if (reduce
        (fn [{one :index} {two :index :as next}]
          (if (= (dec one) two)
            next
            (reduced false)))
        cards)
    cards))

(defn straight
  "Hand-matching function for straight-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not represent a straight (all cards being adjacent).
  Uses high rules, i.e. if the hand contains an ace, it will be treated
  as the highest card first and then the lowest card if no match could be found."
  [hand]
  (let [hand (highcard hand)]
    (or (all-neighbours hand)
        (if-let [ace (first (filter #(= (:rank %) :ace) hand))]
          (let [hand (concat (drop 1 hand) (list (assoc ace :index -1)))]
            (all-neighbours hand))))))

(defn straight-flush
  "Hand-matching function for straight flush-hands.
  Returns an appropriately sorted version of the given cards or nil
  if the cards do not represent a straight flush, i.e. match the criteria
  of both a flush and a straight."
  [hand]
  (and (flush hand) (straight hand)))

(def hands
  "A sequence of all possible hands.
  A hand here consists of a (displayable) name, an id, an index to represent
  the natural order (like with cards) and a match-fn to test if a hand of 5
  cards matches the requirements for this hand type.
  The sequence is in reverse natural order, i.e. the best hand is first."
  (reverse
    (map-indexed
      (fn [index match-sym]
        {:name     (keyword "hand" (name match-sym))
         :id       (keyword (name match-sym))
         :index    index
         :match-fn (resolve match-sym)})
      '[highcard
        pair
        two-pairs
        three-of-a-kind
        straight
        flush
        full-house
        four-of-a-kind
        straight-flush])))

(defn cards-value
  "Returns a value that can be used to compare two vectors of cards."
  [cards]
  (mapv :index cards))

(defn hand-value
  "Returns a value that can be used to compare two hands with associated cards."
  [{:keys [index cards]}]
  [index (cards-value cards)])

(defn best-hand
  "Returns the best possible hand for a set of 7 cards.
  The returned result will be a map that has the properties of a hand as defined in hands
  and a :cards key associated with the cards of the concrete hand."
  [cards]
  (let [card-combs (combs/combinations cards 5)]
    (loop [[{match :match-fn :as hand} & remaining-hands] hands]
      (if-let [matching-combs (seq (keep match card-combs))]
        (assoc hand :cards (first (highest cards-value (map vec matching-combs))))
        (recur remaining-hands)))))
