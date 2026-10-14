# Testing the Discord match thread stages on your Mac

For the owner. It runs the app locally against a real Discord test channel and your local Postgres, with the staged match header turned on, and lets you walk one match through every title with `!rerun`. Nothing here changes production: the switches are set only in the shell you start the app from.

Property names, environment variable names, commands and endpoints below were checked against the code (`application.properties`, the `@ConfigurationProperties` classes, `.env.example`, `build.gradle`, the command classes). Nothing was run against a real Discord channel or a real Valve download: those are the checks in step 9 and what is marked "Not verified".

## 1. What you need

1. Docker running, JDK 21 (`export JAVA_HOME=~/.sdkman/candidates/java/21.0.0.0+35-tem`), this branch checked out.
2. A Discord bot token (`DISCORD_BOT_TOKEN`) for a bot that is a member of a test server. The bot needs: view channel, send messages, create public threads, send messages in threads. Use a test channel that is not the one you use for real matches.
3. A match that already has a Discord thread named `Match <id>` in that test channel, or step 6 to make one.
4. For the replay part: the match must be an Ability Draft (mode 18) or All Pick (mode 22) match with OpenDota replay info (`cluster` and `replay_salt`) and a replay Valve still serves (a few days old at most).

## 2. Start the database

1. `docker compose up -d postgres` in the repository root. It listens on `localhost:5432`, user `postgres`, password `password`, database `dbuff`; these are the defaults of `spring.datasource.*`, so no variables are needed. Your data is in the `postgres-data` volume, so existing matches and the channel registration are kept.
2. Check: `docker compose ps` shows `dbuff-postgres` healthy.

## 3. Start the app

In one terminal, from the repository root, paste your own token into the second line and run the lines one after another:

```
export JAVA_HOME=~/.sdkman/candidates/java/21.0.0.0+35-tem
export DISCORD_BOT_TOKEN='paste-the-bot-token-here'
export DISCORD_REPORT_STAGES_ENABLED=true
export REPLAY_PROCESSING_ENABLED=true
export REPLAY_REPORT_ENABLED=true
export REPLAY_REPORT_FORMAT=components
export REPLAY_REPORT_SECTIONS_IMPORTANTFIGHTS_ENABLED=true
export REPLAY_REPORT_SECTIONS_LANES_ENABLED=true
export REPLAY_REPORT_SECTIONS_VISION_ENABLED=true
export REPLAY_REPORT_SECTIONS_ITEMS_ENABLED=true
export REPLAY_PROCESSING_TEMP_DIR=$HOME/dbuff-replay-tmp
export SCHEDULER_QUIET_HOURS_ENABLED=false
mkdir -p $REPLAY_PROCESSING_TEMP_DIR
./gradlew :server:bootJar
$JAVA_HOME/bin/java --enable-preview -jar server/build/libs/server-0.0.1-SNAPSHOT.jar
```

Start it with `$JAVA_HOME/bin/java`, not plain `java`: the `java` on your PATH may be a newer JDK (for example 25), which fails with `UnsupportedClassVersionError ... preview features ... only recognizes preview features for class file version 69` because the jar is compiled with JDK 21. Check with `java -version`.

Do not use `./gradlew :server:bootRun` for this: the build passes `--enable-preview` to the compiler and to the test tasks only, `bootRun` has no such argument, and the class files of `MatchProcessorService`, `MatchCompletenessService` and `ProcessContext` are marked as preview classes (checked with their class file version), so the JVM refuses to load them without the flag. Production starts the jar the same way (`java --enable-preview -jar`, see `infrastructure/cloudformation/template.yaml`). The jar name was checked by building it.

Instead of `export`, the same `NAME=value` lines can go into a `.env` file in the repository root (it is git-ignored and the build includes `spring-dotenv`); I did not run that route.

What each one does:

| Variable | Property | Why |
|---|---|---|
| `DISCORD_BOT_TOKEN` | `discord.api-key` | the bot login |
| `DISCORD_REPORT_STAGES_ENABLED` | `discord.report.stages.enabled` | the staged header, the basic card, the stage titles, and the header refresh and report repost on `!rerun`. Default false |
| `REPLAY_PROCESSING_ENABLED` | `replay.processing.enabled` | fetch and analyse replays. Default false |
| `REPLAY_REPORT_ENABLED` | `replay.report.enabled` | post the replay report in the thread. Default false |
| `REPLAY_REPORT_FORMAT` | `replay.report.format` | `components` posts the report as Components V2 cards, `plain` (default) as text |
| `REPLAY_REPORT_SECTIONS_<NAME>_ENABLED` | `replay.report.sections.<name>.enabled` | six sections are on by default (swing, deaths, fights, movement, composition, draft); the others are off. The name has no dash or underscore: `IMPORTANTFIGHTS` is the section `important-fights`. Other names: `LANES`, `ROTATIONS`, `SMOKE`, `OBJECTIVES`, `OPENING`, `VISION`, `ITEMS`, `OUTLIERS`, `ENDGAME`. **A name that is not a section stops the app from starting while the report is on** |
| `REPLAY_PROCESSING_TEMP_DIR` | `replay.processing.temp-dir` | where the replay is downloaded and unpacked (up to about 250 MB per match). Default is `<java.io.tmpdir>/dbuff-replays`, so setting it is optional on macOS |
| `SCHEDULER_QUIET_HOURS_ENABLED` | `scheduler.quiet-hours.enabled` | by default the replay job and the match jobs sleep from 01:00 to 09:00 server time. Only needed if you test at night |

Other keys (OpenAI, Google Vision, `DOTA_API_KEY`) are not needed for this test; the app starts without them. The REST API is open locally because `app.api.required` is false and `DBUFF_API_KEY` is empty.

The last command keeps this terminal busy; leave it open and use another terminal for the later commands. Check: the log shows no stack trace at the end of the start, and the bot is online in the test server.

## 4. Register the test channel

1. In the test channel run `/dbuff register player:<a player>`. The player may be picked from the autocomplete list, or typed as a 32-bit Dota account ID, a Steam64 ID, or a profile link from OpenDota, Dotabuff, Stratz or Steam (`PlayerIdParser`); vanity Steam URLs and persona names are rejected. Use a player who played the match you will test. The text alias of the command is `!dbuf`. Which players the autocomplete lists was not checked.
2. Run `/dbuff settings replay_report:true detail:full`. The channel must have the replay report on and a detail level above `minimal`, or the report is not posted. `detail:standard` posts fewer sections (and at most 2 messages instead of 4).
3. `/dbuff status` shows the result. Use `quiet_hours:off` in `/dbuff settings` if the channel has its own quiet hours; quiet hours never delay a title edit or a `!rerun`, but they do hold back the normal report.

## 5. Pick the match thread

1. Open an existing thread named `Match <id>` in the test channel (the name is what `!rerun` reads the match from). The channel must be the one registered in step 4: the thread's parent channel is looked up.
2. If the thread was created before the switch was on, its header is plain text: `!rerun` then does nothing to the header (that is the legacy rule), and you need a new thread. To get a new staged thread for an old match use `/match retry` in the old thread: it deletes the match's stored data and builds the match again, with a new header. **This also throws away the stored replay analysis of that match.**

## 6. Make a new staged thread, and walk the stages

A match found by the app after the switch was on gets a staged header. In the test channel it appears as follows (this is the live flow; it needs the tracked player to play, or an old match to be found as new):

| Stage | Header text | Set by |
|---|---|---|
| 1 | `Match <id> · <mode> · Win/Loss · <n> min · OpenDota parse requested`, a card with one line per tracked player | the first sight of the match |
| 2 | `... OpenDota data ready`, or `... replay processing` when a replay is expected | OpenDota parsed the match |
| 2 (timeout) | `... OpenDota did not parse this match` | parse timeout |
| 3 | `... replay analysis ready`, `... replay analysis ready (partial)`, or `... replay analysis not available` | the replay run reached a final state |

The shortest path for an existing match: `/match retry` in its thread (stage 1 and 2), then let the replay run (step 7), then `!rerun` (step 8).

## 7. Make the match go through replay processing

The replay job takes a match when it has no run yet for the current `replay.processing.parser-version` (1), is mapped, and is an active mode (18 and 22). It runs every 10 minutes (`0 3/10 * * * ?`: minutes 3, 13, 23, ...) and, for a match found fresh, at once.

Wherever a command below says `<matchId>`, type the match number instead (no angle brackets).

1. Check where the match stands: `curl localhost:8080/api/v1/replay/<matchId>` (404 means no run yet).
2. A match that already has a run is not taken again. To process it again, delete its run in Postgres: `docker exec -i dbuff-postgres psql -U postgres dbuff -c "delete from replay_parse_run_domain where match_id = <matchId>"`. Its stored analysis is replaced by the next run.
3. A match older than the fresh windows (mapped more than 24 hours ago) is a "backlog" match and is only taken when you also run `export REPLAY_PROCESSING_BACKLOG_ENABLED=true` before starting the jar (stop it with Ctrl+C and start it again). Valve deletes replays after a while, and a match older than 90 days is skipped (`EXPIRED`).
4. Wait for the next cron minute. In the log: `Replay run for match <id>: DONE (attempt 1, ...)` then `Replay batch: 1 of 1 matches stored`. At that moment the header title changes to `replay analysis ready` (stage 3), and, if the channel allows it and it is not quiet hours, the report is posted.
5. `POST localhost:8080/api/v1/replay/<matchId>/reanalyze` only reruns the analyzers on stored data (it needs a DONE run); it does not fetch the replay and does not touch Discord. Use it after changing analyzer settings, then `!rerun` to see the result.
6. No replay from Valve (offline test, or the replay is gone): serve a stored one yourself. The download URL is `replay.processing.url-template` (env `REPLAY_PROCESSING_URL_TEMPLATE`), filled by `String.format` with the arguments cluster, match id, salt (`HttpReplayDownloader`). Put `<id>.dem.zst` of a match in a folder `570/` as `<id>_<salt>.dem.bz2`, run `python3 -m http.server 8099` in the parent folder, and start the app (in the same terminal as step 3, after `export REPLAY_PROCESSING_URL_TEMPLATE='http://localhost:8099/570/%2$d_%3$d.dem.bz2'`) (`%2$d` and `%3$d` skip the cluster). The decompressor is chosen from the file's first bytes, not its name (`docs/replay-fixtures.md`). The match must still have `cluster` and `replay_salt` stored from OpenDota. The format string and the server were not run end to end.

## 8. Use `!rerun`

1. In the match thread send `!rerun` (text alias of `/match rerun`; `!retry` is the alias of `/match retry`, which you do not want here). The command reads the match id from the thread name, which must be `Match <id>`: the app names every thread it creates that way (`MatchCommand` reads the prefix `Match `), and the thread's parent channel must be a registered channel. The bot answers "Rerunning analysis for match <id>" and rebuilds the OpenDota report from stored data.
2. With the switch on, it then also:
   1. sets the header title again from what is stored now: `replay analysis ready` (or `(partial)`, or `not available`) when the match has a final run; `replay processing` when a replay is still expected; `OpenDota data ready` when the match is stored and no replay is expected. This is the one place where a title may go back to an earlier stage, for example after you deleted the run in step 7.2. It only touches a staged header; a plain header is left alone.
   2. posts the replay report sections again, if the match has a DONE or PARTIAL run, even if the report was already posted for it. It follows the section switches, the channel's `replay_report` setting and detail level; it ignores quiet hours because you asked.
   3. says what happened in one extra message in the thread: `Header refreshed.` when the title was set, then `Replay report posted again.` or `Replay report not posted: <reason>.` (no stored replay analysis, report off for the channel, minimal detail, no match or channel row, no thread on record, no section applies, Discord cannot find the thread). No `Header refreshed.` means the header was not changed (plain header, no thread row, or the Discord edit failed: see the log line `Header refresh of match ... failed` or `Could not edit header message`).
3. `/match retry` is unchanged and does not refresh.

## 9. What to look at in Discord

These are the checks nobody has done in a real channel. Write what you see in the table.

| # | Check | What good looks like | Result | Notes |
|---|---|---|---|---|
| 1 | Header is a Components V2 container with an accent colour (green win, red loss) and a Dotabuff link line | one container, title in bold | | |
| 2 | Creating a thread from a V2 header message works | thread `Match <id>` is attached to the header | | |
| 3 | Editing the V2 header works at each stage (stage 2, stage 3) | the title changes in place, no new message | | |
| 4 | The basic player card under the header | one line per tracked player: name, hero, K/D/A, GPM/XPM, Win or Loss | | |
| 5 | The stage titles keep result, score, mode and duration | e.g. `Match 77 · ability draft · Win (40-25) · 35 min · replay analysis ready` | | |
| 6 | `(partial)` and `not available` titles | wording as above | | |
| 7 | `!rerun` goes back and forth: delete the run, `!rerun` shows `replay processing` or `OpenDota data ready`; run again, title returns to `ready` | title follows the stored state | | |
| 8 | A plain (old) header is not edited as V2 and stays plain, also when a replay run ends | no error, no change | | |
| 9 | V2 limits: the report cards are accepted at full detail | no `rejected, posting plain text` WARN in the log; if there is one, the text fallback is posted | | |
| 10 | How packed cards look (several sections in one message) | readable, not cut off | | |
| 11 | Whether the note after a card is accepted | no WARN `Components of the replay report ... rejected` | | |
| 12 | The report posted again by `!rerun` | the same sections as before, no duplicates inside one run, quiet hours ignored | | |
| 13 | Standard detail posts at most 2 messages, full at most 4 | counts as stated | | |
| 14 | Switch off (restart without `DISCORD_REPORT_STAGES_ENABLED`) | `!rerun` replies as before, no refresh, no repost | | |

Log lines to look at (the terminal that runs the jar): `Stage 1 sent for match`, `Replay run for match`, `Replay report of match <id> posted in <n> messages`, and the WARN lines for failures (`Stage 1 of match`, `Replay title of match`, `Header refresh of match`, `Could not edit header message`, `Header components ... rejected`).

## 10. Switch it off again

Stop the app, unset the variables (or open a new terminal) and start it as usual: with `DISCORD_REPORT_STAGES_ENABLED` false, new matches get the old plain header and no title edits or reposts run. A thread whose header is staged keeps being editable only through the staged code, which is off then.
