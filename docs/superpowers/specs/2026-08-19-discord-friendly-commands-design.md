# Design: User-Friendly Discord Commands

**Date:** 2026-08-19
**Status:** Approved for planning

## Goal

Replace DBuff's prefix-based Discord commands with discoverable slash commands
that autocomplete their arguments, and add player statistics commands covering
overall performance, item builds, ability builds, and hero performance.

Text commands (`!vs`, `!dbuf …`, `!rerun`, `!retry`) keep working as aliases.

## Current State

Four listeners exist; three are registered in `BotConfiguration`.

| Trigger | Location | Behavior |
|---|---|---|
| `!dbuf register/status/add-players/remove-players/add-modes/remove-modes/deactivate/help` | `RegistrationDiscordListener` | Instance config, hand-rolled `--modes`/`--name` flag parsing |
| `!vs <player_name>` | `PlayerScoutDiscordListener` | Opponent stats by name/regex, replies in a new thread |
| *(bare image attachment)* | `PlayerScoutDiscordListener` | OCR scoreboard, scouts every detected opponent |
| `!rerun`, `!retry` | `PingPongListener` | Re-report / reprocess a match; thread-only, match ID parsed from thread name |

`MatchSummaryDiscordListener` is dead code — never registered as a bean, and its
`!ping` handler only responds to bots. `DiscordMessageService` handles outbound
sends/edits/threads and is kept as-is.

### Problems being fixed

1. **No discoverability.** Nothing appears in Discord's `/` picker; users must
   already know `!dbuf help` exists.
2. **Typos vanish silently.** Unknown subcommands fall through to help, so
   `!dbuf add-player 123` looks like it partially worked.
3. **Raw numeric Dota account IDs typed by hand**, with no validation until the
   service throws.
4. **Four inconsistent prefixes** sharing one help text.
5. **Errors are posted publicly**, cluttering the channel.
6. **`deactivate` is destructive with no confirmation.**
7. **Image posts silently trigger OpenAI Vision calls** — memes and clips cost
   money.
8. **Dead code** (`MatchSummaryDiscordListener`) demonstrating that parallel
   listener code drifts.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Migration scope | Slash commands **plus** text aliases | Preserves muscle memory; `MESSAGE_CONTENT` intent is retained anyway for the image-scout trigger |
| Registration scope | **Guild**-scoped | Single server; guild commands propagate instantly instead of taking up to an hour |
| Namespace | `/scout`, `/stats`, `/match`, `/dbuff` | Frequent actions stay short; rare config nests under `/dbuff` |
| Autocomplete | Local + OpenDota | Dictionaries are already in-memory; OpenDota `SearchApi` covers new-player lookup |
| Image scouting | Button prompt + explicit command | Stops silent Vision spend on non-scoreboard images |
| `/match` target | Thread-only, ID from thread name | Explicit user decision; no schema change |
| Discord ↔ Dota link | `discordUserId` on `PlayerDomain` | One player is one person; indexable, unlike a JSON blob |
| Stats shape | Subcommands per dimension | Each has a fixed output shape and a self-documenting picker entry |
| Time scoping | Preset `period:` choices | Avoids date-parsing friction; bounds query cost |
| Async model | Sync ack → thread → async fill | Satisfies Discord's 3s window without `defer()`; results stream in progressively |

## Command Surface

### `/scout` — opponent scouting (replaces `!vs`)

| Command | Options |
|---|---|
| `/scout player` | `name:` (single) — autocompleted from opponents in `ExternalPlayerStatisticRepository`; free text still accepted for unseen names, and the existing case-insensitive regex behavior of `!vs` is preserved, so one invocation may still match several players |
| `/scout scoreboard` | `image:` — attachment option |

`/scout player` takes a single `name:`, not a list — its regex support already
covers matching multiple players in one call.

Posting a bare image in a registered channel no longer triggers OCR. Instead the
bot attaches a 🔍 **Scout this scoreboard** button to the message. One click runs
the scout; ignoring it costs nothing.

### `/stats` — new

All four subcommands take `player:` (required, list) and `period:` (optional).

| Command | Extra options | Output when filter absent | Output when filter present |
|---|---|---|---|
| `/stats overall` | `hero:` | Full stat block across all heroes | Same block, narrowed to that hero |
| `/stats heroes` | `limit:` | Top-N hero table: games, win %, avg KDA | *n/a* |
| `/stats items` | `items:`, `hero:`, `limit:` | Top-N item ranking | Combo mode: games containing **all** listed items |
| `/stats skills` | `skills:`, `hero:`, `limit:` | Top-N ability ranking | Combo mode: games containing **all** listed abilities |

`limit:` is an optional integer controlling the size of the top-N ranking,
defaulting to **10** and capped at **25** — it is ignored in combo mode, which
returns exactly one result block per player. The underlying repositories already
accept a `limit` parameter and already default to 10.

`/stats heroes` deliberately has **no** `hero:` option. Its "filter present" case
would be a one-row ranking table, which `/stats overall hero:X` already serves
better as a full stat block. This is the one intentional break from the
absent/present symmetry of the other subcommands.

**Stat block contents** (`/stats overall`), all already produced by
`PlayerStatisticService.getPlayerStatistics`: wins, losses, avg win rate,
avg/max/min KDA, avg GPM (+max/min), avg XPM, avg last hits (+max/min), avg
denies, avg observer/sentry wards, avg camps stacked, avg rune pickups, avg
tower/roshan kills, avg lane efficiency.

**Combo mode contents:** number of games containing the full set, win rate over
those games, avg KDA over those games, and per-member avg purchase time (items
only) and avg use count.

`period:` is a fixed choice list — Last 7 days, Last 30 days, Current patch, All
time — defaulting to **Last 30 days** to bound query cost. "Current patch"
resolves to the start date of the newest entry in `PatchConstantService` through
today; if that lookup yields nothing, the handler falls back to Last 30 days and
says so in the thread rather than silently returning all-time data.

When `hero:` is set, the `skills:` autocomplete narrows to that hero's abilities
via `HeroAbilitiesConstantService`. When `hero:` is set on `/stats overall`, the
`popularHeroes` section is omitted, since it degenerates to a single row.

### `/match` — thread-only (replaces `!rerun` / `!retry`)

`/match rerun` and `/match retry`. The match ID is parsed from the thread name.
Invoked outside a thread, they reply ephemerally explaining the constraint.

### `/dbuff` — configuration

| Command | Options / notes |
|---|---|
| `/dbuff register` | `player:` (single) + optional `name:`, `mode:` |
| `/dbuff status` | Current configuration embed |
| `/dbuff players add` | `player:` autocompleted via OpenDota `SearchApi`; optional `user:` links at the same time |
| `/dbuff players remove` | `player:` autocompleted from currently tracked players only |
| `/dbuff link` | `player:` + `user:` — sets the Discord mapping |
| `/dbuff modes add` / `remove` | `mode:` autocompleted from `getMatchTypeConstantMap()`, showing names rather than the numeric IDs the domain stores |
| `/dbuff deactivate` | Confirmation button before committing |
| `/dbuff help` | Generated from the registry, so it cannot drift |

`/dbuff register` takes a **single** player, not a list: Discord options are not
variadic, and a comma-separated string with synthetic autocomplete would be worse
than adding the rest via `/dbuff players add`. This is the one place list support
is deliberately omitted.

## Architecture

Handlers are written once against a normalized context; two thin adapters feed
them from the slash and text surfaces.

```
service/discord/command/
  DbuffCommand.java          interface: definition() -> CommandData,
                             execute(CommandContext)
  CommandContext.java        resolved args, channel/guild/instance, invoker,
                             acknowledge(), replyEphemeral()
  AsyncReply.java            post / postEmbed / fail — into the thread
  CommandRegistry.java       collects DbuffCommand beans, registers guild
                             commands on ReadyEvent, generates help
  autocomplete/
    AutocompleteProvider.java        choices(String partial, CommandContext)
    HeroAutocomplete, ItemAutocomplete, AbilityAutocomplete,
    TrackedPlayerAutocomplete, OpponentAutocomplete,
    OpenDotaPlayerAutocomplete, GameModeAutocomplete
  adapter/
    SlashCommandAdapter      onSlashCommandInteraction, onCommandAutoComplete,
                             onButtonInteraction
    InteractionResponder     CommandContext over an interaction
    TextCommandAdapter       onMessageReceived, `!` prefix parsing
    ChannelResponder         CommandContext over a MessageChannel
  impl/
    ScoutCommand, MatchCommand, StatsCommand,
    DbuffInstanceCommand, DbuffPlayersCommand
```

One handler class per top-level command rather than per subcommand — five
classes, not twenty near-empty ones. `/dbuff` splits in two because nine
subcommands in one file would stop being readable.

### Async model

Every command follows: **sync ack → create thread → fill asynchronously.**

```java
interface CommandContext {
  // ... resolved args, instanceId, invoker ...
  AsyncReply acknowledge(String summary, String threadName);  // sync, must be <3s
  void replyEphemeral(String message);                        // pre-ack errors only
}

interface AsyncReply {              // used from a virtual thread
  void post(String message);
  void postEmbed(MessageEmbed embed);
  void fail(String message);
}
```

The adapter replies synchronously, retrieves the reply message, creates a thread
on it, then invokes the handler on a virtual thread with an `AsyncReply` bound to
that thread. No handler ever touches the JDA gateway thread, and `defer()` is not
needed — the sync ack satisfies the 3-second window.

Four JDA constraints this respects:

1. **Ephemeral replies cannot have threads.** The ack is therefore public.
   Validation errors (unknown player, no registered instance, list over cap) fire
   *before* the ack and stay ephemeral; execution errors go into the thread via
   `fail()`.
2. **Threads cannot nest.** Invoked inside a thread, `acknowledge()` returns an
   in-place sink that posts to the current thread. Handlers are unaware.
3. **`/match` is thread-only**, so it always takes the in-place path — same
   interface, no special-casing.
4. **Thread clutter.** Stats threads get `AutoArchiveDuration.TIME_1_HOUR`. Match
   threads keep whatever `MatchReportOrchestrator` already uses.

Text commands take the identical path; `PlayerScoutDiscordListener` already
creates a thread on the triggering message, so `ChannelResponder` formalizes
existing behavior. Both surfaces converge on one code path.

### Autocomplete

Autocomplete has its own 3-second budget and fires on every keystroke.

Hero, item, ability, mode, tracked-player, and opponent providers read
`ConstantsManagers`' in-memory Caffeine maps or local tables, so they cost
effectively nothing. `OpenDotaPlayerAutocomplete` is the only provider making a
network call; it gets its own Caffeine cache keyed on query prefix and its own
`RateLimiter`, kept **separate** from the 60/min match-fetch limiter so typing in
a search box cannot starve match ingestion.

Autocomplete handlers must never throw. On failure they return an empty list and
log at debug — a throwing handler leaves the user staring at a broken picker with
no explanation.

### List options

Three dimensions, three different meanings:

| Option | Cardinality | Semantics |
|---|---|---|
| `players:` | list | **Fan-out** — one result block per player |
| `items:` | list | **Conjunctive** — games where the player had *all* of them |
| `skills:` | list | **Conjunctive** — same, within one game |
| `hero:` | single | Filter. Correctly single: a game has exactly one hero, so a list could only mean OR, clashing with the others |

Lists are typed as **one comma-separated string option** whose autocomplete
completes the *last* token and returns the whole accumulated string as the choice
value. Typing `Blink, Black K` offers `Blink Dagger, Black King Bar`.

Two hard Discord limits, not tuning knobs:

- A choice **value** caps at 100 characters — roughly 3–5 items.
- The picker shows at most **25** suggestions.

`players:` is capped at **5**, with an ephemeral error above that. Each player's
embed is posted into the thread **as it completes**, so a slow fifth player does
not hide the first four. This progressive fill is a concrete advantage of the
thread model over `defer()`, where nothing appears until everything finishes.

### Resolving names back to IDs

Because `hero:`, `items:`, and `skills:` are string options, autocomplete offers
*display* names while the repositories need numeric IDs. Each handler resolves
submitted names through the constant maps (`getItemConstantMap()`,
`getHeroConstantMap()`, `getAllAbilityConstants()`) before querying.

Resolution can fail — autocomplete is a suggestion, not a constraint, so a user
can submit freehand text or edit a previously-completed value. On failure the
handler replies **ephemerally, before the ack**, naming every token that did not
resolve and suggesting the closest match by edit distance. It never silently
drops an unresolved token: dropping one member of a combo query would quietly
answer a different question than the one asked, reporting a two-item combo's
statistics as though they were a three-item combo's.

## Data Layer Changes

### 1. Hero filter on three existing query methods

`ItemRankingRepository.findItemRankingsByPlayer`,
`AbilityRankingRepository.findAbilityRankingsByPlayer`, and
`PlayerStatisticRepository.findPlayerStatistics` each gain an optional
`Set<Long> heroIds`, applied as a predicate on
`PlayerMatchStatisticDomain.heroId`.

> **Correctness trap.** Both ranking repos compute pick rate against a private
> `getTotalMatchCount()` helper. That helper **must also** receive the hero
> filter. Missing it makes `/stats items player:X hero:Invoker` divide
> Invoker-game item picks by the player's total games across *all* heroes,
> silently reporting pick rates several times too low. The same trap exists in
> the ability repo.

### 2. Aggregate average use count

Add `cb.avg(root.get(useCount))` to both ranking aggregations, and `avgUseCount`
to `ItemRankingResponse` and `AbilityRankingResponse`. Items keep
`avgPurchaseTime`; abilities have no time analogue, so skills report pick rate,
win rate, and avg uses.

`useCount` is null for unparsed matches. SQL `AVG` skips nulls so the arithmetic
is safe, but a null result means "no parsed data", not "zero uses" — the
formatter renders it as `—`, never `0.00`.

### 3. Conjunctive combo queries (new)

`findItemRankingsByPlayer` groups *by item* and cannot answer "which games
contained all of these". New methods `findItemComboStatistics(playerId, itemIds,
heroId, period)` and `findAbilityComboStatistics(...)` are needed. The match set
comes from:

```sql
SELECT match_id, player_slot FROM item_domain
WHERE player_id = ? AND item_id IN (:itemIds) AND is_neutral = false
GROUP BY match_id, player_slot
HAVING COUNT(DISTINCT item_id) = :itemCount
```

That set joins `player_match_statistic_domain` for win rate and KDA, and rejoins
`item_domain` for per-item avg purchase time and avg uses. `AbilityDomain`'s
`(matchId, playerSlot, abilityId)` primary key makes the ability version
structurally identical.

`COUNT(DISTINCT item_id)` — not `COUNT(*)` — is load-bearing. Duplicate rows for
one item within a single game would otherwise let a one-item game satisfy a
two-item query.

With a single item, combo mode degenerates cleanly to "games with this item", so
there is no special case for n=1.

### 4. `discordUserId` on `PlayerDomain`

One nullable column plus `PlayerRepo.findByDiscordUserId(String)`.

Note that `PlayerDomain`'s `@Id` is `name`, and `id` (the Dota account ID) is a
plain column. The link command must be written against the name primary key.

### 5. `StatsPeriod` enum

`LAST_7_DAYS`, `LAST_30_DAYS`, `CURRENT_PATCH`, `ALL_TIME`, each resolving to a
`(LocalDate start, LocalDate end)` pair. `CURRENT_PATCH` reads
`PatchConstantService`.

### 6. Indexes

`server/src/main/resources/db/migration/V2__item_ranking_indexes.sql` is a
careful script targeting exactly these queries, and **it has never run** —
Flyway is not on the classpath. The cross-joins in these repositories are
currently unindexed on a 2 GiB instance sharing RAM with the JVM and Postgres.
This matters whether or not `/stats` ships, because the REST endpoints already
run these queries.

**Approach:** declare the composite indexes via `@Index` inside
`@Table(indexes = …)` on `ItemDomain`, `AbilityDomain`,
`PlayerMatchStatisticDomain`, and `MatchDomain`, so `ddl-auto=update` creates
them and Hibernate remains the single schema owner.

This deliberately drops the `INCLUDE` and partial-index variants from the V2
script, which JPA cannot express. Those are a follow-up via `psql` only if
profiling shows the plain composites are insufficient.

Adding Flyway now is explicitly rejected: the deployed database has no
`flyway_schema_history`, there is no `V1`, and baselining a live
Hibernate-owned schema is high-risk for no immediate gain.

Verify with `\di` after deploying rather than assuming — `ddl-auto=update`
handles indexes less dependably than columns.

## Error Handling

Centralized in the adapter layer, which fixes several current behaviors:

- Handler exceptions → **ephemeral** reply plus `log.error`. Currently posted
  publicly.
- Unknown text subcommand → ephemeral "unknown command — did you mean
  `add-players`?" via edit distance against the registry, replacing the silent
  fall-through to help.
- No registered instance → ephemeral pointer to `/dbuff register`.
- Empty results → explicit "no matches for **X** in the last 30 days", not a
  blank embed.
- Autocomplete failure → empty list, debug log, never an exception.
- `players:` over the cap of 5 → ephemeral error before any work starts.

## Testing

Available: JUnit 5, Mockito via `spring-boot-starter-test`, H2. No Testcontainers.

| Layer | Approach |
|---|---|
| Handlers | `FakeAsyncReply` capturing posts; every handler unit-tested against mocked services with no JDA at all |
| Text parsing | `TextCommandAdapter` as pure string → args assertions |
| Autocomplete | Against the real in-memory `ConstantsManagers` maps; assert the 25-choice cap, match ordering, and comma-accumulation behavior |
| Name resolution | Assert unresolved tokens produce an error rather than being dropped from a combo query |
| Combo semantics | Assert `COUNT(DISTINCT …)` rejects a one-item game for a two-item query |
| Rate arithmetic | Unit-test `mapToItemRankingResponse` and the hero-filtered total-match-count path — where the pick-rate trap above would surface |

Repository tests are the weak spot and this is stated plainly: H2 cannot run the
`INCLUDE`/partial DDL, and `PlayerRepo.findByNameMatchingRegex` already depends
on the Postgres-only `~*` operator. Predicate assembly and rate arithmetic are
covered as unit tests; **actual SQL is verified against local Postgres from
`docker-compose`**, not against H2.

The structural payoff: the Discord layer currently has zero test coverage. After
this change every handler is testable without a Discord connection.

## Out of Scope

- Migrating away from the `MESSAGE_CONTENT` privileged intent (text aliases and
  the image-button trigger both require it).
- Global (non-guild) command registration.
- Adding Flyway.
- The `INCLUDE` / partial index variants from the V2 script.
- Per-instance Discord↔Dota mappings (the link is global per player).
- Multi-player `/dbuff register`.
- List support for `mode:` — one game mode per invocation; add more with repeated
  `/dbuff modes add`.
- Disjunctive (OR) filters on any dimension.
