# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DBuff is a Dota 2 match analytics backend built with Spring Boot 3.5, Java 21, and PostgreSQL 16. It fetches match data from the OpenDota API, stores player statistics, provides item/ability rankings, and integrates with Discord and OpenAI for match summaries.

## Build & Development Commands

```bash
# Build entire project
./gradlew build

# Run the server (requires PostgreSQL via docker-compose up -d)
./gradlew :server:bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew :server:test --tests "com.ako.dbuff.SomeTestClass"

# Code formatting (Google Java Format via Spotless)
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Apply formatting

# Regenerate OpenDota API client from OpenAPI spec
./gradlew cleanupodtaClient generateDotapiClient
```

## Architecture

### Multi-Module Gradle Project

- **`server/`** — Main Spring Boot application (`com.ako.dbuff.DbuffApplication`)
- **`clients/dotapi/`** — Auto-generated OpenDota API client (OpenAPI Generator). Do not edit generated code directly; modify `clients/dota_api_formatted.json` and regenerate.

### Server Module Package Layout (`com.ako.dbuff`)

- `config/` — Spring configuration beans (caching, API clients, concurrency, Discord, OpenAI)
- `dao/model/` — JPA entities, `dao/repo/` — Spring Data repositories
- `resources/` — REST controllers and response models
- `service/` — Business logic:
  - `ai/` — OpenAI-powered match analysis and summarization
  - `constant/` — Hero/item/ability constants (cached from OpenDota)
  - `discord/` — Discord bot (JDA). `discord/command/` holds the command layer; see below.
  - `list/` — Match history scraping (Playwright + JSoup) and parsing
  - `match/` — Match processing, analysis, and detail mapping
  - `ranking/` — Item/ability ranking calculations
  - `instance/` — Instance configuration management

### Key Architectural Patterns

- **Virtual Threads (Java 21)**: Used throughout for high concurrency. HikariCP pool is 50 for local dev; the `prod` profile lowers it to 10 because Postgres is co-located on the same 2 GiB instance.
- **Semaphore-based concurrency control**: `ConcurrencyConfig` limits parallel match fetches (20) and page scrapes (5) to prevent resource exhaustion.
- **Rate limiting**: Guava `RateLimiter` on OpenDota API (60 req/min) and ScraperAPI calls.
- **Caching**: Caffeine + Spring Cache for hero/item/ability constants.
- **Schema management**: Hibernate `ddl-auto=update` owns the schema. There are SQL scripts in `server/src/main/resources/db/migration/`, but **Flyway is not on the classpath** — it was never added as a dependency, so those scripts have never run. Treat a `pg_dump` as the only authoritative schema, and do not assume a migration has been applied because a file exists. Indexes are declared with `@Index` on the entities so Hibernate owns them too; `V2__item_ranking_indexes.sql` is retained only as documentation of the `INCLUDE`/partial variants JPA cannot express.
- **Preview features are enabled**: `--enable-preview` is passed at compile time, to the test JVM, and in production (`template.yaml`). All three must agree — a class that actually uses a preview feature is marked in its class file and will not load on a JVM without the flag.

### Discord Command Layer

Commands do not touch JDA. Each implements `DbuffCommand` (name, JDA definition, `execute`) and talks to Discord only through `CommandContext`, which is why they are all testable against `FakeCommandContext` with no Discord connection.

- **`CommandRegistry`** is the single source of truth: both adapters dispatch through it, guild registration reads its definitions, and `/dbuff help` is generated from it, so help cannot drift from the implemented command set.
- **Two adapters, one handler per command.** `SlashCommandAdapter` serves `/` commands and autocomplete; `TextCommandAdapter` keeps the legacy `!` aliases (`!dbuf`, `!vs`, `!rerun`, `!retry`) working by mapping them onto the same option names. Never register a second listener for an input an adapter already handles — both surfaces see `!` messages and the command would be answered twice.
- **Validate, then acknowledge, then work.** Discord discards an interaction not acknowledged within 3 s, and an ephemeral message cannot carry a thread. So every handler does its cheap validation first with `replyEphemeral`, calls `acknowledge` second (which creates the thread), and does the slow work third, writing into the returned `AsyncReply`. Getting this order wrong means a typo produces a thread full of an error instead of a private correction.
- **Configuration changes are gated by Discord permissions, not a role list.** `DbuffConfigCommand` refuses `register`, `add`, `remove`, `link`, `modes` and `deactivate` unless `CommandContext.invokerCanManageChannel()` — Manage Server, or Manage Channel on the *parent* channel for a thread — and `status`/`help` stay open. The check is in `DiscordPermissions`, shared by all three contexts, and a handler's `FakeCommandContext` defaults to a manager so tests opt *in* to the refusal.
- **Servers are onboarded by `GuildLifecycleListener`, not only at startup.** `onReady` registers commands for guilds already joined; `onGuildJoin` registers them for a new one (via the shared `GuildCommandRegistrar`) and posts a welcome; `onGuildLeave` deactivates that guild's instances so their jobs stop failing against unreachable channels. `BotPermissions.REQUIRED` is the single list behind both the invite URL (logged at startup) and `ChannelReadinessService`, which `/dbuff register` uses to refuse a channel the bot cannot post or create threads in. `ChannelReadinessService` takes JDA `@Lazy` because JDA is built from the listeners that reach the command beans.
- **Re-registering a deactivated channel reactivates its instance.** `DbufInstanceConfigService.register` reuses the inactive row (replacing players and modes) and reschedules its jobs; only an *active* instance is rejected as a duplicate.
- **Registration imports history quietly.** `DbufInstanceConfigDomain.trackingSince` is set on register/reactivate; a player added later gets their own entry in `playerTrackingSince` (never a move of the instance-wide value, which would swallow the existing players' recent games), read through `trackingSinceFor`. In `LastMatchesProcessorService`, an unseen match whose *end* (start + duration, not start alone — a game in progress at registration is live news) is before the *earliest* cutoff of the tracked players who have it — so it is live if it is news to any of them — goes through `MatchProcessorService.process` with no parse request, thread or report, then one `sendBackfillNotice`. A null `trackingSince` (instances that predate the field) reports everything, as before. `RecentMatchesJob` is `@DisallowConcurrentExecution` because the first import can outlast the 5-minute trigger. New `MatchDomain` rows take `gameModeId` from the recent-matches response so the mode filter works on first sight; before, the null passed the filter and every mode was reported.
- **`player:` accepts more than the autocomplete ID.** `PlayerIdParser` reads an account ID, a Steam64 ID (offset `76561197960265728`) or an opendota/dotabuff/stratz/steam profile link, and `/dbuff register|add` reject the whole request naming any entry it cannot read rather than tracking the rest.
- **Players are keyed by name, so a clash never repoints an account.** `getOrCreatePlayer` looks up by account ID first; a name already used by a *different* account stores the newcomer as `"name (accountId)"`. Overwriting the existing row's ID would silently move another group's tracked player onto this account.
- **Per-channel settings are nullable, and null means "behave as before".** `InstanceSettings` is an `@Embeddable` on `DbufInstanceConfigDomain`. Hibernate loads an embedded object whose columns are all null as `null`, so code reads it through `effectiveSettings()`, never the field. Unset means AI on, `FULL` detail, the server-wide quiet window in the server's zone, and Ability Draft for `/stats` — so a channel that never touched `/dbuff settings` is unchanged. `InstanceSettingsApplier` is the one place values are parsed and validated; every rejection is an `IllegalArgumentException` written for the person who typed it, and the command shows it as is. The same applier serves REST via `UpdateInstanceRequest`.
- **Report detail is declared by each analyzer, not decided by the orchestrator.** `ReportAnalyzer.getMinimumDetail()` defaults to `FULL` so a new analyzer stays out of shorter reports until placed, and `isEnabledFor(settings)` lets one opt out of that rule — `AiSummaryReportHandler` answers to its own switch because it is the costly report. `MatchReportOrchestrator.enabledAnalyzers` is the single filter point for the partial, full and legacy paths; `analyzeMatch` (REST) is deliberately unfiltered. `ReportAnalyzerDetailTest` pins where each current analyzer sits.
- **Quiet hours are per channel.** `QuietHoursGuard.isQuietTime(InstanceSettings)` follows the server window when the channel chose nothing, never skips when it chose `off`, and reads its own window in the channel's timezone otherwise. Both `RecentMatchesJob` and `MatchParseCheckJob` ask with the instance's settings. A timezone alone also shifts the server window into that zone.
- **A channel's default game mode is a fallback, not a filter.** `GameModeResolver.resolveOrDefault(typed, channelDefault)` uses what the user typed, else the channel default, else Ability Draft. Registering with `mode:` seeds it so tracking and `/stats` agree. Stored as canonical constant names, which the resolver accepts back.
- **The statistics commands share `StatsRequestResolver`.** `/stats` and `/hero` both route their `player`/`hero`/`period`/`game_mode` validation through it, in that order, and get back a `StatsRequest` carrying the resolved date range, filters and footer label. It exists because two copies of the sequence above would drift, and the drift is silent: a handler that acknowledges before validating still works for every correct input. Option *names* are defined once in `StatsOptions` for the same reason — the resolver reads them by name, so a command spelling `game_mode` differently would compile, register, and then ignore what the user selected.
- **`player:` is optional and defaults to the channel's whole focus group.** The cap of `MAX_PLAYERS` (5) applies differently to each case, deliberately: explicitly named players over the cap are *rejected*, because the user chose them and dropping one would discard a choice without saying which; a focus group over the cap is *trimmed* so a bare `/stats` still answers, with `StatsRequest.omissionNotice()` stating "showing 5 of 8 tracked" in the acknowledgement. `PlayerReferenceResolver.focusGroup` sorts by name because the config stores players in a `Set` — without that, embed order and which player gets trimmed would both vary per invocation. Nothing caps group size at `/dbuff add` time, so a large group is possible; raise `MAX_PLAYERS` rather than removing the trim, since each player is another pass over the match history on a 2 GiB box.
- **Autocomplete providers** are keyed `command:subcommand:option`, most-specific-first — needed because e.g. `/dbuff add player:` searches all of OpenDota while `remove player:` must offer only tracked players. A provider serving the same option on several commands overrides `getCommandNames()` (e.g. `hero:` and `player:` mean the same thing on `/stats` and `/hero`); forgetting to gives the second command a silently empty picker, which `DiscordCommandWiringTest` now fails on. The OpenDota search provider has its **own** rate limiter and cache, deliberately separate from the 60 req/min match-fetch limiter, so typing in a picker cannot starve match ingestion.
- **Unknown names are reported, never dropped.** `ConstantNameResolver` and `GameModeResolver` return unresolved names and the services throw `UnknownConstantNameException`; silently discarding one turns a filtered query into an unfiltered top-N and answers a different question than the one asked.
- **Game mode defaults to Ability Draft.** `GameModeResolver` resolves the `game_mode:` filter against the OpenDota match-type dictionary — the same one `/dbuff` validates tracked modes against — with aliases `ability_draft`, `all_pick` and `all`, and accepts a comma-separated list. Two things to know: `all_pick` maps to **`game_mode_all_draft`** (22), because that is what the client labels All Pick, while `game_mode_all_pick` (1) is the unranked legacy mode; and the default lives in `resolveOrDefault`, used only by the command layer, so the REST API keeps treating "no mode given" as every mode rather than quietly narrowing.
- **A mode filter excludes matches with no recorded mode.** `MatchDomain.gameModeId` is null when the scraper could not map a mode name, and an `IN` predicate drops those rows. That is deliberate — an unknown mode is not Ability Draft — but it means the default filter hides such matches from `/stats` and `/hero`.
- **`trace_matches:` lists the games behind the numbers.** `MatchTraceReporter` posts `matchId - date` per player as a plain message after that player's embed, capped at `MAX_TRACED_MATCHES` with the overflow stated rather than elided. It goes through `PlayerStatisticRepository.findPlayerMatches`, which shares `buildMatchPredicates` with the aggregations on purpose — a trace built from a different predicate set would list games the numbers did not come from, which is worse than no trace. The combo subcommands pass their own `matchIds` instead, since those queries already know which games satisfied the conjunction. A trace failure is posted into the thread but never rethrown: the statistics are already out, and losing the trace must not lose them too.

### Replay pipeline (`service/replay/`)

Optional enrichment from Valve replays, off by default (`replay.processing.enabled`). Nothing in it may fail match ingestion or reports: every problem becomes a `ReplayStatus` on `ReplayParseRunDomain`.

- **One entry point, one replay at a time.** `ReplayProcessingJob` (Quartz, global) takes `replay.processing.batch-size` matches through `ReplayBatchService`, sequentially; `replayParseSemaphore` (`replay.processing.max-concurrent`, default 1) bounds parses on the 2 GiB host. `ReplayQueue` picks due retries first, then new matches whose OpenDota details are stored (`detailsMappedAt`).
- **The game mode decides what runs** (`service/replay/mode/`). A `GameModeProfile` bean per mode (`AbilityDraftProfile` 18, with the 48-skill pool and 40 picks; `AllPickProfile` 22, `game_mode_all_draft`) and `GameModeRegistry`: a mode is processed only when it has a profile *and* is in `replay.processing.supported-modes`, which stays the switch (a configured mode with no profile logs a warning and is skipped). `ReplayEligibility` uses the registry (`SKIPPED_NOT_SUPPORTED`), `ReplayRunService` passes the profile to the parser, runs only the extractors whose `supports(mode)` is true and hands it to `ReplayAnalysisService`, and `ReplayReportService` posts only the sections that support it. `ReplayExtractor`, `ReplayAnalyzer` and `ReplayReportSection` default `supports(mode)` to true; `AbilityDraftExtractor` and `DraftSection` apply to Ability Draft only. The parser reads `m_AbilityDraftAbilities` through a `DraftReader` (`AbilityDraftReader`; `NoDraftReader` for other modes), so `ReplayFacts.draft` (`AbilityDraftFacts`) is null for All Pick, which writes no draft rows (its draft comes from OpenDota's `DraftEventDomain`; the replay has no pick stream for it). `ReplayAnalysisService.analyze(matchId, failed)` without a mode (the re-analysis endpoints) takes the mode from the stored match and does nothing for an inactive one. The queue still filters by `supported-modes` alone. A new mode is a new profile bean plus its configuration entry.
- **Only `service/replay/clarity/` imports Clarity.** Extractors read `ReplayFacts`; a new table slice is a new `ReplayExtractor` bean, a new compression format a new `ReplayDecompressor` bean.
- **Identity is the roster, not the match id.** The replay's `m_unMatchID64` is a lobby id. `ReplayRosterVerifier` compares accounts per slot (hero for anonymous players); a mismatch is `FAILED_IDENTITY` and writes nothing.
- **Extractors store facts, not conclusions.** `AbilityDraftExtractor` writes the 40 picks and the 48-skill pool and stores nothing for a draft of another shape, rather than a partial one. The ability names come through the small `AbilityNames` interface, so the extractor does not depend on the constants cache.
- **Time is game time, not ticks.** A paused game keeps ticking, so `GameClock` takes the paused stretches (`m_bGamePaused`) out of every tick before it is used as a time; the combat log is stamped in game time already. Without it the samples of a match with pauses (one sample match has 218 s of them) drift away from the combat log by the length of each pause.
- **The parse reads the whole replay once** (about 1.4 s for a 45-minute game, at the 512 MB heap `replayTest` uses). `SampleRecorder` samples every player each 5 game seconds from the pre-game phase (-90 s) and their cumulative counters each minute; `ReplayTrackExtractor` and `ReplayMinuteExtractor` store them. Tracks are `int[]` columns (Hibernate maps them to SQL arrays natively; the hypersistence array types clash with Hibernate 6.6). Game states 8 and 10 occur before the pre-game phase, so "state at least 6" does not mean the game is over.
- **The hero-relevant combat log is stored as one zstd blob** (`ReplayCombatLogDomain`, laid out by `CombatLogCodec`, versioned) so that fights, deaths and swings can be recomputed after Valve deletes the replay. `CombatLogRecorder` keeps damage and deaths that involve a hero, hero ability and item uses, gold and experience, whitelisted modifiers on heroes, building kills and buybacks; analyzers read it through `CombatLogReader`. A combat-log death is not always a real death: an Aegis use is logged as one. The parse reads 10 s past the end of the game because the Ancient's fall is logged after the state changes.
- **Map positions are classified by `MapLayout`**, built from the buildings and camps the replay lists (`MapFacts`) and frozen as `replay/map-layout.json`, because the replay will be gone when an analysis runs. `MapLayouts.forVersion(run.mapVersion)` returns the layout only for the frozen map version, so a patch that moves a tower shows up as a missing layout instead of a wrong classification. A zone is a base, a lane (bottom, mid, top), the river or the jungle (with the camp when inside a camp box); its half says whose side of the river it is. The lane corridor, river and base sizes are in `MapLayout.Tuning`; they were checked against OpenDota's `lane` on two matches.
- **Analyzers work from stored data only.** A `ReplayAnalyzer` bean reads the extractors' tables, the combat-log blob and the frozen map, never the replay, and `ReplayAnalysisService` runs them after the extractors once the replay's facts are out of scope; a failing analyzer makes the run `PARTIAL`. Rows carry `analysisVersion`, so rules can change and be run again. Thresholds are in `ReplayAnalysisProperties` (`replay.analysis.*`). `ActivityAnalyzer` stores what each hero was doing (dead, teleporting, in base, jungle or lane farming, pushing, travelling) as `PlayerActivitySegmentDomain`.
- **Fights are found from the stored combat log** (`FightAnalyzer`, after `ActivityAnalyzer` by `@Order`). `FightDetector` clusters hero-to-hero damage bursts and hero deaths; `FightAssessor` places the fight, picks a winner by the levels of the heroes killed (then by damage) and says for each of the ten players whether they were `ACTIVE`, `PRESENT_PASSIVE` or absent and why. A living hero who is away gets an `AbsenceReason` from his `HeroStateTimeline`: `NO_TP`, `TP_ON_COOLDOWN` (with the seconds left) or `HAD_TP_NOT_USED`, counting a TP scroll or Travel Boots. Heroes taking part are also ranked by how far toward the enemy they stood at the start (`FrontlineRanker`, rank 1 in front) and compared with the order their attack ranges suggest, short range in front: `frontlineDelta` is negative for a hero further forward than his range suggests. The attack range comes from the track (`attackRange`, as the game reports it; whether item and talent bonuses are in it is not verified), loaded with the rest by `PlayerTraces`; tracks stored before it existed get a rank but no delta. The three types are TEAMFIGHT (3 or more deaths, as OpenDota counts), SKIRMISH (1-2) and ENGAGEMENT (none, both sides hit hard). Tuning is in `ReplayAnalysisProperties` (`replay.analysis.fight-*`).
- **What each fighter did with his abilities** (`FightAbilityAnalyzer`, after `FightAnalyzer`): for each hero who took part, every learned ability and held item that is meant to be used (seen on cooldown, with a mana cost, or used at some point; passives are left out) gets a `FightAbilityUseDomain` row with its casts, damage to heroes and, when not cast, a `NonCastReason` (`ON_COOLDOWN`, `NO_MANA`, `DEAD_EARLY`, `READY_NOT_CAST`; range and target are not checked) and whether the last cast before the fight hit only creeps. Mana at the start comes from the track's absolute `mana`, so replays stored before it existed get no `NO_MANA`.
- **What heroes hold and can cast is stored as changes** (`ReplayStateChangeDomain`, by `StateRecorder` on the same 5-second grid): items gained and lost (counted by name), cooldowns starting, skill level-ups and mana costs (`MANA_COST`, which follows the skill level; the track also stores absolute `mana`). A hero item or ability's `m_fCooldown` is the time *remaining*, so a cooldown change carries the exact second it ends; its `m_flCooldownLength` may be 0 for some abilities. Together with the combat log's ability and item uses this answers "was it ready".
- **Every hero death gets an explanation** (`DeathAnalyzer`, after `FightAnalyzer`): `DeathAssessor` combines the track (where, who was near), the combat log (killer, damage and disables in the last 8 s, spell-immunity windows) and `HeroStateTimeline` (items held and cooldowns, rebuilt from the stored state changes) into a `DeathReason`. The combat log records an Aegis use as a death; the hero being alive again within 5 s marks it `REINCARNATED`, so the other deaths equal OpenDota's; a hero back within a walk of where he died, with no buyback after that death, is an Aegis, and one back at his fountain is not. `BuybackAnalyzer` (after the deaths) stores each buyback with the death it followed, how long he was dead and whether his team won that fight; the combat log's BUYBACK `value` is the player index 0-9. A disable counts only until its modifier was removed (a dispel or BKB ends it early), and one that took hold while spell immunity was on is recorded in `immunityPiercedBy` and makes the death `BKB_PIERCED` instead of `DIED_IMMUNE`; the modifier is named, not the ability that cast it. A death inside a fight carries that fight's `frontlineDelta` for the hero, and standing `replay.analysis.death-out-of-position-ranks` (2) or more places further forward than his attack range suggests, with no sharper cause, is `OUT_OF_POSITION`. `DeathAnalysisDomain` is matched to OpenDota's `DeathEventDomain` by player and time, not by index.
- **Team states** (`TeamStateAnalyzer`, after the buybacks): from the per-minute net worth OpenDota stores for every player (it needs no replay data, but like the other analyzers runs after a replay was read), `TeamStateSegmenter` splits the game for each team into `AHEAD`, `EVEN` and `BEHIND` stretches (`GameStateSegmentDomain`). A lead of `replay.analysis.team-ahead-percent` (5) of both nets worth together must hold for 120 s to enter a state and a state is left only after the lead has been below half of that for 60 s, so a wobbling lead does not flap. A match without a complete net worth timeline gets no segments.
- **Buildings** (`BuildingKillAnalyzer`, after the fights): each fallen tower, barracks and ancient gets a `BuildingKillContextDomain` row (kind, tier, lane, owner, killer and whether it was a deny from OpenDota's objectives, the attacking heroes within 1500 units in the ten seconds before from the tracks and the frozen map, and the fight). The combat log has no killer, and its barracks and fourth-tower names differ from the map's (`MapLayout.buildingPosition` translates them). `MatchMapLayout` gives an analyzer the frozen layout of a match's map version.
- **Phases** (`PhaseSummaryAnalyzer`, last): for each player and phase (laning to minute 12, mid game to 25, then late game; `replay.analysis.phase-*`) a `PlayerPhaseSummaryDomain` row with the seconds in each activity (and the seconds with none recorded, so the columns add up to the phase), kills, deaths and assists from the per-minute counters, gold and experience per minute from OpenDota's series, and the fights that began in the phase while he was alive and those he took part in.
- **Swings** (`SpikeAnalyzer`, after the phases): `SpikeDetector` finds the stretches in which the net worth difference between the teams moved by at least 1500 and 3% of both teams' together within 3 minutes and goes on while it keeps moving; one that turns a lead of 1000 or more into the other team's is a change of direction (`GameSpikeDomain`). Each swing gets evidence rows (`GameSpikeFactorDomain`): the gold sources that moved (hero kills, farm, buildings, Roshan, bounty, comeback gold, what the other team spent on buybacks) from the per-minute counters, and the fights, buildings and buybacks inside it. The primary cause is the source with the biggest share of the swing, at least 10%, named `FIGHT_WIN` (hero kills with a fight the favoured team won in the window), `PICKOFF`, `OBJECTIVE`, `ROSHAN_AEGIS`, `BUYBACK_SWING`, `CARRY_FARM`, `COMEBACK_GOLD` or `OTHER`. Net worth comes from OpenDota (`TeamNetWorth`); without the replay's counters a swing is `OTHER`. Not done: key item purchases, feeding, and the outliers of the plan.
- **Outliers** (`OutlierAnalyzer`, after the swings): from the per-minute net worth, a player holding more than `replay.analysis.outlier-share-percent` (35) of his team's for 3 minutes while joining fewer fights than the team median is a `NW_OUTLIER`, and one holding less than 10% for 5 minutes with at least 5 deaths and more than twice the team's median is a `FEEDER` (`PlayerOutlierDomain`, with his place by fights joined). Not done: why the share is what it is (the income change point, the gold source).
- **The replay analysis reaches Discord as a follow-up in the match thread** (`ReplayReportService`, off until `replay.report.enabled`): the orchestrator stores each channel's thread (`MatchReportThreadDomain`, deleted with the match) when it creates it, and after each replay batch `ReplayProcessingJob` calls `deliverPending`, which looks at runs that finished in the last `lookback-hours` and, for each active channel tracking a player of the match, posts once (`ReplayReportPostedDomain`). It skips a channel that turned `replay_report` off, asked for `MINIMAL` detail, is in its quiet hours (it is tried again on the next run) or has no stored thread (a match reported before this existed is never followed up). A `ReplayReportSection` bean writes one part (`SwingSection`, `DeathSection` for the deaths worth explaining of the tracked players, `FightSection` at FULL detail for big fights they could not join and abilities left ready in several fights, `MovementSection` at FULL for where each phase's time went and the fights joined, and `DraftSection` for what they drafted, Ability Draft only) from the stored tables; the text goes out through the existing `TextReport` formatting, capped at `max-messages` (4 at FULL, `standard-max-messages` 2 otherwise) with the left-out count stated.
- **Tests that parse a real replay are tagged `replay`** and run only through `./gradlew :server:replayTest` with `REPLAY_FIXTURE_DIR` set; `:server:test` and CI skip them. Replays are never committed.
- **`ReplayPostgresSmokeTest` runs the whole pipeline against a real PostgreSQL** (`docker run -d -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5544:5432 postgres:16-alpine`, then `SMOKE_PG_URL=jdbc:postgresql://localhost:5544/dbuff REPLAY_FIXTURE_DIR=... ./gradlew :server:replayTest --tests '*ReplayPostgresSmokeTest*'`): a local HTTP server stands in for Valve, so the downloader, zstd, parser, extractors and analyzers run for real and Hibernate (`ddl-auto=update`) creates the tables. Run it before switching replay processing on after any schema change.
- **Operating it** (`/api/v1/replay`, behind the API key): `GET /{matchId}` shows how a match's run ended, `POST /{matchId}/reanalyze` runs every analyzer again from the stored data (no replay needed, so use it after a rule or version changed) and `POST /reanalyze?limit=N&after=ID` (1 to 500, continue from the `lastMatchId` it returns, one re-analysis at a time) does so for the `DONE` runs, oldest match first; a `PARTIAL` run is left out because which extractors failed is not kept.
- **Switching it on in production**, in this order, one step per deploy: (1) take a `pg_dump` (`deploy.sh backup-now`) and merge, which only creates the tables; (2) set `REPLAY_PROCESSING_ENABLED=true` with `REPLAY_PROCESSING_BATCH_SIZE=1`, watch the log for `Replay` lines and `GET /api/v1/replay/{id}` for the first runs, and check memory with `free -m` and the JVM heap during a parse (the JVM runs at `-Xmx512m`); (3) raise the batch size; (4) only then set `REPLAY_REPORT_ENABLED=true`, which posts the analysis into the match threads of channels that did not turn it off. Turning either switch off again stops the work; nothing already stored is lost.
- **To resume the replay work, read `docs/superpowers/plans/2026-10-12-replay-status-and-next-steps.md` first** (what is done, how to build and test, what is unverified, the next PRs). The design is in `docs/superpowers/plans/2026-10-11-replay-extraction-plan.md` and the Discord reports in `2026-10-11-replay-discord-report-plan.md`.

### REST API access

`ApiKeyFilter` requires `X-Api-Key` on everything except `/actuator/health` (exempt because the CI health gate and rollback poll it). The exemption is judged on the container-normalised servlet path, not `getRequestURI()`, which still carries `/../`. `app.api.required` is false locally, so an empty `DBUFF_API_KEY` runs open; the `prod` profile sets it true, so a missing key **closes** the API (every request 401) rather than opening it. The bot never calls the REST API, so closing it does not affect Discord features.

The key reaches the running host through `scripts/set-api-key.sh`, which stores it as an SSM SecureString and has the instance write a root-only systemd drop-in (`/etc/systemd/system/dbuff.service.d/api-key.conf`) — it survives JAR deploys. It is deliberately **not** added to the instance `UserData` in `template.yaml`: `UserData` only runs on first boot, so it would not reach the live server, and editing it makes the next `deploy.sh deploy` stop/start the instance. After a rebuild, run the script again. `scripts/lib/ssm.sh` holds `run_ssm`, shared by it and `deploy-release.sh`.

Port 8080 can be narrowed with `ALLOWED_API_CIDR` (`deploy.sh deploy`, an in-place security-group change); the bot needs no inbound access.

### Environment Variables

Configured via `.env` file (loaded by spring-dotenv). See `.env.example` for required keys:
- `DOTA_API_KEY`, `SCRAPPER_API_KEY`, `OPENAI_API_KEY`, `DISCORD_BOT_TOKEN`
- `DBUFF_API_KEY`: REST API key (empty is open locally, closed in production)

### Dependencies & Infrastructure

- Locally, PostgreSQL 16 runs via `docker-compose.yml` on `localhost:5432`
- Lombok is used extensively — ensure annotation processing is enabled in your IDE

### Production Topology

A single `t4g.small` (Graviton/arm64) EC2 instance runs both the Spring Boot app
and PostgreSQL 16 on `localhost`, provisioned by
`infrastructure/cloudformation/template.yaml` in `eu-north-1`. There is no RDS
instance — it was removed to cut the bill roughly in half, since a dedicated
database and its second public IPv4 address together cost more than the compute.

Consequences worth knowing before changing anything here:

- **The instance is arm64.** Any native dependency must have an aarch64 build.
- **Backups are a nightly `pg_dump`** to `s3://dbuff-deploy-<account>/db-backups/`
  via the `dbuff-backup.timer` systemd timer (02:30 UTC). Trigger one on demand
  with `./infrastructure/cloudformation/deploy.sh backup-now`; restore with
  `... restore <key>`. Losing the instance without a backup loses the database —
  there is no longer a managed snapshot to fall back on.
- **RAM is the binding constraint** (~2 GiB shared between the JVM at `-Xmx512m`,
  Postgres at `shared_buffers=256MB`, and a 2 GiB swapfile as a safety net).

### Deploying

**Application code ships through CI.** A push to `main` runs
`.github/workflows/ci-cd.yml`: the `verify` job builds and tests, and the
`deploy` job installs the JAR that those tests passed, then gates on
`/actuator/health` and rolls back to the previous release if it does not come
up. Do not deploy application changes by hand — merge to `main` instead. CI
authenticates via GitHub OIDC (role from `infrastructure/cloudformation/cicd.yaml`,
a stack separate from `dbuff`), so there is no long-lived AWS key.

**Infrastructure stays manual, deliberately.** The `dbuff` stack owns the EC2
instance that hosts the database, so CI has no permission to touch it. Apply
template changes yourself:

```bash
./infrastructure/cloudformation/deploy.sh deploy   # apply template.yaml
./infrastructure/cloudformation/deploy.sh cicd     # apply cicd.yaml (CI identity)
./infrastructure/cloudformation/deploy.sh all      # build + upload + deploy, the pre-CI manual path
```

`build`/`upload`/`all` remain for emergencies and for bootstrapping a rebuilt
instance. Note that a stack update replacing the instance destroys the local
database — take a backup first.
