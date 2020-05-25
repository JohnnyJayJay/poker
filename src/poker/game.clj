(ns poker.game
  "The poker.game namespace contains functions to create, restart and advance poker games."
  (:require [clojure.set :as sets]
            [poker.state :as state]
            [poker.util :refer [highest-bet compare-desc]]))

(defn required-bet
  "Calculates the minimum bet the current player needs to make in their turn.
  This considers the bet margin (i.e. what others have placed) as well as the
  player's bet so far and their budget, i.e. this will never be any higher than
  the player can afford."
  [{:keys [round-bets budgets] [player-id] :cycle :as game}]
  (let [margin (- (highest-bet game) (round-bets player-id 0))
        budget (budgets player-id)]
    (min margin budget)))

(defn possible-bet
  "Calculates the maximum bet the current player can possibly make in their turn.
  This considers budgets of other players as well as the highest bet in the current
  betting round, meaning that this will never be higher than the highest other player
  budget or the highest bet this round."
  [{:keys [budgets live-order round-bets] [player-id] :cycle}]
  (let [own-budget (budgets player-id)
        highest-bet (reduce max (vals round-bets))
        highest-other-budget (reduce max highest-bet (map budgets (remove #{player-id} live-order)))]
    (min own-budget highest-other-budget)))

(defn minimum-raise
  "Returns the minimum bet required to raise.
  required bet + previous raise (or the big blind value if none happened)"
  [{:keys [big-blind-value last-raise] :as game}]
  (+ (required-bet game) (or last-raise big-blind-value)))

(defn- remove-participant
  "Removes the current player from the active participants, i.e.
  they will not be able to make a turn again in this game.
  This is used for both players who go all in and players who fold."
  [{[player-id] :cycle :as game}]
  (letfn [(remove-id [coll]
            (remove #{player-id} coll))]
    (-> game
        (update :cycle #(cons player-id (remove-id %)))
        (update :live-order remove-id)
        (update :turns dec))))

(defn- bet
  "Places a bet of the specified amount for the current player.
  The amount will be coerced to the minimum/maximum bet defined in required-bet/possible-bet.
  If the coerced bet is an all-in, i.e. equal to the player's budget, the player will
  be removed from the active participants.
  If it is a raise, i.e. more than the minimum, the round's turn counter will be reset
  (everyone now has to call/fold again).
  The bet amount will be added to the round bet of the player and withdrawn from their
  budget. The game will move to the next turn and transition to the next state if applicable."
  [{[player-id] :cycle :as game} amount]
  (let [budget (get-in game [:budgets player-id])
        maximum (possible-bet game)
        minimum (required-bet game)
        amount (-> amount (max minimum) (min maximum))]
    (-> (cond-> game
                (> amount minimum) (assoc :turns 0)
                (= amount budget) remove-participant)
        (update-in [:budgets player-id] #(- % amount))
        (update-in [:round-bets player-id] #(+ % amount))
        (update :cycle rest)
        (update :turns inc)
        state/transition)))

(defn call
  "Places a bet of the minimum required bet for the current player.
  No distinction is made between calling and checking, since a check is just a call
  with the minimum amount of 0."
  [game]
  (bet game (required-bet game)))

(defn raise
  "Places a bet of at least the minimum raise for the current player.
  Coerces to minimum-raise if necessary."
  [game amount]
  (let [amount (max amount (minimum-raise game))]
    (-> game
        (bet amount)
        (assoc :last-raise amount))))

(defn all-in
  "Places a bet of the maximum possible bet for the current player."
  [game]
  (bet game (possible-bet game)))

(defn fold
  "Removes the current player from the active participants and removes them from the
  set of players in general. Also removes their cards."
  [{[player-id] :cycle :as game}]
  (-> game
      remove-participant
      (update :players #(disj % player-id))
      (update :player-cards #(dissoc % player-id))
      (update :cycle rest)
      (update :turns inc)
      state/transition))

(defn possible-moves
  "Returns a map of the possible moves the current player can make associated with their corresponding (minimum) cost.
  fold and all-in are always possible."
  [game]
  (let [maximum (possible-bet game)
        minimum (required-bet game)]
    (cond-> {:fold 0 :all-in maximum}
            (zero? minimum) (assoc :check 0)
            (and (pos? minimum) (> maximum minimum)) (assoc :call minimum)
            (> maximum minimum) (assoc :raise (minimum-raise game)))))

(defn- new-order
  "Calculates the order of the next game (with possibly new players joining).
  Preserves the order of players who participated previously, rotating by one (to move the blinds)
  and adds all new players afterwards."
  [players previous-order]
  (let [old-players (sets/intersection players (set previous-order))
        new-players (sets/difference players old-players)]
    (concat (->> (cycle previous-order)
                 (filter old-players)
                 (drop 1)
                 (take (count old-players)))
            (filter new-players players))))


(defn start-game
  "Starts a new game or restarts an existing game (preserving previous order and budgets) if one is given.
  The budgets are a map of player id -> number, where the number indicates the
  budget they start with or add to their current budget.
  The big-blind-value is an integer denoting the big blind bet amount. The small blind will be half of that.
  The function will calculate the correct order of players using previous order if applicable and the order of player-ids.
  The given cards will be used in their given order.

  A game is a map with the following associations:
  - :order a sequence of the players that participated at the start of the game in the correct order
  - :big-blind-value the value of the big blind bet
  - :small-blind the player who is small blind this game
  - :big-blind the player who is big blind this game
  - :live-order a sequence of the active participants in this game in the correct oder
  - :cycle an infinite sequence that cycles :live-order (is updated with every turn)
  - :remaining-cards the cards that have not been dealt yet
  - :community-cards the community cards of the game, i.e. those that belong to all players
  - :pots a vector of the pots of this game. The last pot is the current one. They're updated when transitioning to a new state.
  - :players a set of players that are still part of the game, regardless if active or not. I.e. this contains players who are all-in but not players who have folded
  - :player-cards a map of player id -> seq of two cards that represents the private cards of each individual player
  - :round-bets a map of player id -> bet (number) representing the bet the player has placed in this round so far
  - :budgets a map of player id -> budget (number)
  - :turns a counter to keep track of the amount of turns made in a betting round. It is reset when someone raises
  - :state the current state of the game. Possible states: :pre-flop, :flop, :turn, :river, :instant-win, :showdown"
  ([{:keys [order big-blind-value] :as game} player-ids cards new-budgets]
   (let [players (set player-ids)
         [small-blind big-blind :as new-order] (new-order players order)
         [player-cards remaining-cards] (split-at (* 2 (count players)) cards)]
     (-> game
         (merge {:order           new-order
                 :big-blind-value big-blind-value
                 :small-blind     small-blind
                 :big-blind       big-blind
                 :live-order      new-order
                 :cycle           (cycle new-order)
                 :remaining-cards remaining-cards
                 :community-cards []
                 :pots            [{:participants players
                                    :money        0}]
                 :players         players
                 :player-cards    (zipmap players (partition 2 player-cards))
                 :round-bets      (zipmap players (repeat 0))
                 :turns           0
                 :state           :pre-flop})
         (update :budgets #(merge-with + % new-budgets))
         (dissoc :winner :prize :hands)
         (bet (quot big-blind-value 2))
         (bet big-blind-value)
         (update :turns dec))))
  ([big-blind-value player-ids cards initial-budgets]
   (start-game {:big-blind-value big-blind-value
                :order ()
                :budgets {}}
               player-ids
               cards
               initial-budgets)))
