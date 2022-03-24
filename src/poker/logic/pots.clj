(ns poker.logic.pots
  "The poker.pots namespace contains a function to calculate poker pots at the end of a round.
  The last pot is considered to be the current pot."
  (:require [poker.logic.util :refer [all-in? highest-bet]]
            [clojure.set :as sets]))

(defn- update-bets
  "Updates all round bets in the game using the given function."
  [game f]
  (update game :round-bets #(zipmap (keys %) (map f (vals %)))))

(defn- remove-zero-bets
  "Removes all round bets that are zero."
  [game]
  (update game :round-bets #(into {} (remove (comp zero? second) %))))

(defn- place-in-pot
  "Places the given amount in the current pot."
  [{:keys [pots] :as game} amount]
  (update-in game [:pots (dec (count pots)) :money] + amount))

(defn- update-pot-participants
  "Updates all pots, removing all participants who are no longer part of the game."
  [{:keys [players] :as game}]
  (letfn [(remove-folds [pot]
            (update pot :participants sets/intersection players))]
    (update game :pots #(mapv remove-folds %))))

(defn- open-side-pot
  "Opens a new pot with 0 money in it and the players in the round bets."
  [{:keys [round-bets pots] :as game}]
  (update game :pots conj {:money        0
                           :participants (set (keys round-bets))
                           :name         (count pots)}))

(defn flush-bets
  "Flushes all round bets by distributing among old and new pots as required by the poker rules.
  This means that at the end, players who no longer participate will not be part of the round bets
  map anymore and all other bets will be reset to 0.
  This function should only be used to update the game at the end of a betting round,
  but it can also be used to get interim calculations of the pots, in which case only
  the :pots of the returned value should be considered."
  [game]
  (loop [{:keys [round-bets] :as game} game]
    (let [bet-values (vals round-bets)
          all-ins (map round-bets (filter #(all-in? game %) (keys round-bets)))
          pot-margin (or (and (seq all-ins) (reduce min all-ins)) (highest-bet game))
          game (-> game
                   (place-in-pot (reduce + (map #(min % pot-margin) bet-values)))
                   (update-bets #(- % (min % pot-margin))))]
      (if (some #(> % pot-margin) bet-values)
        (recur (open-side-pot (remove-zero-bets game)))
        game))))
