# How to host poker

Because poker is open source, you have a [license](../LICENSE.md) to use its source code. One use is to copy it and run it yourself. In the following sections, I will describe how to get this bot up and running.\
I assume that you are familiar with how Discord bots work and how to create a Discord app in the developer portal. If you aren't, consider using the [public instance](https://discord.com/api/oauth2/authorize?client_id=461791942779338762&permissions=277025671168&scope=applications.commands%20bot) of this bot instead.

## Prerequisites
- Git
- Docker (or, if you don't want to use docker, JDK 11+ and Leiningen)
- A Discord bot user created through the [developer portal](https://discord.dev), with **api token** at hand
- The same bot user must be a member of a server that contains the card emojis. I won't link to it directly from here for now, shoot me a message in the [support Discord](https://discord.gg/npEXyQt) to learn how to get in.

## Setup

1. Clone this repository `git clone https://github.com/JohnnyJayJay/poker`
2. In the directory root of the project, create a file called `config.clj` and enter the following:

```clj
{:token "YOUR BOT TOKEN HERE"
 :buffer-size 1000 ;; this is a scaling option, it should be fine like this now but may have to be increased if the bot gets on a lot of servers
 ;; this will allow people to invite it via /poker info if your bot is public
 :invite-url "https://discord.com/api/oauth2/authorize?client_id=YOUR_BOTS_CLIENT_ID&permissions=277025671168&scope=applications.commands%20bot"
 ;; you can change these URLs to whatever you want, I'd appreciate it if you keep my source URL for credit though!
 :server-url "https://discord.gg/npEXyQt"
 :source-url "http://github.com/JohnnyJayJay/poker" 
 :support-url "https://ko-fi.com/johnnyjayjay"
 ;; the /holdem command defaults
 :defaults {:wait-time 25
            :timeout 180
            :buy-in 1000
            :max-players 10}}
```
3. In the project directory, run `docker-compose run poker lein run update`, or, if you don't use docker, `lein run update`. This command registers the slash commands of this bot. It should be run whenever you update to a version of poker that changes the set of commands.
4. You can now run the bot using `docker-compose up -d` or `lein run`.

## Updating
To update poker to a newer version published to GitHub, do the following:

1. `git pull`
2. `docker-compose down`
3. Update slash commands if applicable (see [Setup](#Setup))
4. `docker-compose up -d --build`
