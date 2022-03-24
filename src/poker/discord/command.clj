(ns poker.discord.command
  (:require [slash.command.structure :as cmd]))

(def holdem-command
  (cmd/command
   "holdem"
   "Play a game of poker!"
   :options
   [(cmd/option "buy-in" "How many chips do the players enter with?" :integer :min-value 100 :max-value 1000000)
    (cmd/option "small-blind" "The value of the small blind" :integer :min-value 1 :max-value 1000000)
    (cmd/option "big-blind" "The value of the big blind" :integer :min-value 2 :max-value 1000000)
    (cmd/option "wait-time" "How many seconds do players have to join?" :integer :min-value 1 :max-value 600)
    (cmd/option "timeout" "How many seconds do players have to make a move?" :integer :min-value 1 :max-value 600)
    (cmd/option "max-players" "The maximum number of players that can participate" :integer :min-value 2 :max-value 10)
    (cmd/option "closed" "If set to true, no new players can join after the first round" :boolean)]))

(def poker-command
  (cmd/command
   "poker"
   "Poker commands"
   :options
   [(cmd/sub-command
     "info"
     "Displays information about the bot")
    (cmd/sub-command
     "language"
     "Set the bot language for your server")]))
