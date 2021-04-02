# poker

A Clojure implementation of a poker game ([Texas Hold 'em](https://en.wikipedia.org/wiki/Texas_hold_%27em)) and a corresponding Discord bot to play poker in chat.

This is (roughly) how the logic part is used:

```clojure
(require '[poker.logic.game :as poker]
         '[poker.logic.core :as cards])

(def player-budgets {"Alice" 100 "Bob" 85})
(def game (poker/start-game 10 (keys player-budgets) (shuffle cards/deck) player-budgets))

; Available functions
(poker/possible-moves game)
(poker/call game)
(poker/raise game 20)
(poker/fold game)
(poker/all-in game)
```

## Bot

[![Discord Bots](https://top.gg/api/widget/461791942779338762.svg)](https://top.gg/bot/461791942779338762)

Here are a few impressions of what it looks like:

![](https://i.imgur.com/zGCQoHN.png)
![](https://i.imgur.com/FeJTYD5.png)
![](https://i.imgur.com/yAWQtXn.png)

The bot is public, you can invite it [here](https://discord.com/api/oauth2/authorize?client_id=461791942779338762&permissions=329792&scope=bot).

It is currently not guaranteed to be online 24/7 since some things may still change, but it should be online 99% of the time now.

## Running

You are also free to host it yourself (either manually or using Docker):

1. Get [Leiningen](https://leiningen.org/) (not needed for Docker)
2. Clone this repo
3. Put a `cofig.clj` file in the repo that looks like this:
   
   ```clj
   {:token           "TOKEN"
   :default-wait-time       25000   ; The default number of milliseconds to wait for players when starting a game
   :default-buy-in  1000    ; The default buy-in if none is specified when running "holdem!"
   :default-timeout         180000} ; The default number of milliseconds after which the current player, if they have not made a move, will fold automatically
   ```
4. Run `lein run` or `docker-compose up`



## License

Copyright © 2020 JohnnyJayJay

This program and the accompanying materials are made available under the
terms of the Hippocratic License 2.1 which is available at
https://firstdonoharm.dev/version/2/1/license.html
