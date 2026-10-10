# Adding DBuff to your Discord server

DBuff posts a report thread for every Dota 2 match your group plays, and answers `/stats` and
`/hero` questions about the group. Setup takes about two minutes and happens entirely in Discord.

## 1. Invite the bot

Use the invite link from the bot's owner. It requests exactly the permissions the bot needs:
View Channel, Send Messages, Send Messages in Threads, Create Public Threads, Embed Links and
Read Message History. Slash commands appear in your server straight away; the bot also posts a
short welcome message.

## 2. Register a channel

In the channel where reports should appear, run:

```
/dbuff register player:<pick from the list>
```

You need the **Manage Channel** permission (or Manage Server) for any command that changes
tracking. `/dbuff status` and `/dbuff help` work for everyone.

If the channel is not ready, `register` tells you exactly what is missing — usually a channel
permission the bot lacks. Fix it and run the command again.

**Finding a player:** type a name in the `player:` box and pick a result. If you only have an ID,
the number in a player's OpenDota or Dotabuff profile URL is their account ID.

## 3. Add the rest of the group

```
/dbuff add player:<name>            add someone to tracking
/dbuff add player:<name> user:@them also link their Discord account
/dbuff link player:<name> user:@them
/dbuff modes mode:<game mode>       add a tracked game mode (remove:true to drop one)
```

## 4. Use it

- New matches appear as a thread in the channel; the report fills in once OpenDota has parsed the
  game, usually within a few minutes.
- `/stats` and `/hero` summarise the group's games. They default to Ability Draft; pass
  `game_mode:all` for everything.
- `/match` reruns or retries a report. `/dbuff help` lists every command.

## Stopping and restarting

`/dbuff deactivate` stops tracking in the channel. `/dbuff register` brings it back. Removing the
bot from the server deactivates everything it tracked there.

## Bot owner checklist

In the Discord developer portal: **Public Bot** on, **Message Content Intent** on, and the invite
scopes `bot` and `applications.commands`. The invite URL is logged at startup.
