(ns poker.core
  "The poker.core namespace contains definitions for
  the basic building blocks of a poker game: the cards."
  (:require [clojure.string :as string]))

(def ranks
  "All available ranks (no jokers/wildcards) in ascending order of value."
  [:two :three :four :five :six :seven :eight :nine :ten
   :jack :queen :king :ace])

(def suits
  "All available suits."
  [:clubs :diamonds :hearts :spades])

(def deck
  "The 52-card deck used to play poker.
  Cards have a displayable :name, a :rank, a :suit and
  an :index defining their value/order."
  (for [[index rank] (map-indexed vector ranks)
        suit suits]
    {:name (str (string/capitalize (name suit)) " " (name rank))
     :rank  rank
     :index index
     :suit  suit}))