(ns poker.state
  "The poker.state namespace contains a multimethod to transition between states of a poker game,
  e.g. moving to the next betting round or evaluating the results."
  (:require [poker.hands :as hands]
            [poker.pots :as pots]
            [poker.util :refer [highest all-in?]]))

(defn- round-end?
  "Returns whether the current betting round has ended.
  True if the turn count is equal to the amount of active participants
  (or higher, if someone went all in), i.e. every participant has made a turn."
  [{:keys [turns live-order]}]
  (>= turns (count live-order)))

(defn- next-round
  "Finishes the current betting round.
  First, the bets are flushed to the pots. Then, the cycle is updated so the first player
  will start again. The turn counter and the last raise is reset, more cards are revealed according to
  cards-to-unveil and the state is set to next-state."
  [{[first] :live-order :keys [remaining-cards] :as game} cards-to-unveil next-state]
  (-> game
      pots/flush-bets
      (update :cycle #(drop-while (complement #{first}) %))
      (assoc :turns 0)
      (dissoc :last-raise)
      (update :community-cards #(concat % (take cards-to-unveil remaining-cards)))
      (update :remaining-cards #(drop cards-to-unveil %))
      (assoc :state next-state)))

(defmulti
  transition
  "Multimethod to transition from one poker game state to another.
  If only one player is left, the dispatch value is :instant-win (all others have folded).
  If the betting round is not over, it is :default.
  If only one player still actively participates, it is :showdown.
  Else, it is the current state of the game.

  The result of this is that in \"normal\" states, the game will continue to the next state
  if appropriate and at the end it will evaluate the winners. :instant-win simply declares
  the remaining player as the winner; :showdown calculates the best hand and who wins which pot etc. etc."
  (fn [{:keys [state live-order players] :as game}]
    (cond
      (= (count players) 1) :instant-win
      (not (round-end? game)) :default
      (= (count live-order) 1) :showdown
      :else state)))

(defmethod transition :default [game] game)

(defmethod transition :pre-flop [game] (next-round game 3 :flop))

(defmethod transition :flop [game] (next-round game 1 :turn))

(defmethod transition :turn [game] (next-round game 1 :river))

(defmethod transition :river [game] (transition (assoc game :state :showdown)))

(defn- assoc-winners [{:keys [participants] :as pot} hands]
  (assoc pot :winners (mapv first (highest (comp hands/hand-value second) (filter (comp participants first) hands)))))

(defmethod transition :showdown
  [{:keys [community-cards player-cards] :as game}]
  (let [game (next-round game (- 5 (count community-cards)) :showdown)
        hands (->> (vals player-cards)
                   (map concat (repeat (:community-cards game)))
                   (map hands/best-hand)
                   (zipmap (keys player-cards)))]
    (-> game
        (assoc :hands hands)
        (update :pots (partial mapv #(assoc-winners % hands))))))

(defmethod transition :instant-win
  [{:keys [budgets] [{:keys [money]}] :pots :as game}]
  (let [[winner-id] (first budgets)]
    (-> game
        (next-round 0 :instant-win)
        (assoc :winner winner-id)
        (assoc :prize money))))