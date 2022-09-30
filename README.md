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

It is currently not published as a library, only used as the backbone for the associated Discord bot.

## Bot

[![Discord Bots](https://top.gg/api/widget/461791942779338762.svg)](https://top.gg/bot/461791942779338762)

Here are a few impressions of what it looks like:

![](https://i.imgur.com/zGCQoHN.png)
![](https://i.imgur.com/FeJTYD5.png)
![](https://i.imgur.com/yAWQtXn.png)

Active work is also being done on translating this bot. More about that at the link below.

Some relevant links:
- [Invite link](https://discord.com/api/oauth2/authorize?client_id=461791942779338762&permissions=329792&scope=bot) to add the public version hosted by myself to your Discord server
- [Support server](https://discord.gg/npEXyQt) for chatter, bug reports and suggestions
- [How to host this bot yourself](./doc/host.md)
- [How to help with translation](./doc/i18n.md)


## License

Copyright © 2020-2022 JohnnyJayJay

This program and the accompanying materials are made available under the
terms of the Hippocratic License 2.1 which is available at
https://firstdonoharm.dev/version/2/1/license.html
