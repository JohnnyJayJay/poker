# poker

A Clojure implementation of a poker game ([Texas Hold 'em](https://en.wikipedia.org/wiki/Texas_hold_%27em))

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

An implementation of a Discord bot that allows you to play this in chat is WIP and can be found in the poker.discord namespaces.

## License

Copyright © 2020 JohnnyJayJay

This program and the accompanying materials are made available under the
terms of the Hippocratic License 2.1 which is available at
https://firstdonoharm.dev/version/2/1/license.html
