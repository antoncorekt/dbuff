# Replay pipeline: status and next steps (resume point)

Written 2026-10-12, updated the same day after the stack of PRs #17 to #34 was built (none of it merged yet). Read this first in a fresh context. It says what is built, how we work, what has NOT been
verified, and what to build next, in order. The design reasoning lives in
`2026-10-11-replay-extraction-plan.md` (section numbers below refer to it); how the results reach Discord is in
`2026-10-11-replay-discord-report-plan.md`. The architecture of the code is summarised in the "Replay pipeline"
section of the repo `CLAUDE.md`.

## 1. The goal in one paragraph

Download the Valve replay of a Dota 2 match (Ability Draft first, All Pick later), parse it once with Clarity, store
the facts that OpenDota does not have (draft order, positions every 5 s, cooldowns, combat log, ...), compute
conclusions from those stored facts (fights, deaths, activity), and finally report them in the Discord thread of the
match for the focus players. Replays vanish after about 3 months, so facts are stored for all 10 players and
conclusions can be recomputed from the facts without the replay. Everything is **off by default**
(`replay.processing.enabled=false`) and a replay problem must never break match ingestion or reports.

## 2. What is done (all on `main` unless marked)

| PR | What | Notes |
|---|---|---|
| #6 | OpenDota detail tables (teamfights, deaths, timelines, objectives, purchases, draft events) | before the replay work; replay tables extend these, never copy them (plan 3a) |
| #7 | Plan, research notes, spike notes, first spike programs | docs only |
| #8 | WP0 foundation | download (Valve URL from cluster+salt), zstd/bzip2 by magic bytes, Clarity parser, roster check, run table + statuses, queue, job, semaphore, deletion hook |
| #9 | WP1 Ability Draft | 40 picks + 48-skill pool (`AbilityDraftPickDomain`, `AbilityDraftPoolDomain`) |
| #10 | WP2a sampler | whole-replay parse (~1.4 s); 5 s tracks (`ReplayPlayerTrackDomain`, `int[]` columns); 32 per-minute counters (`ReplayPlayerMinuteDomain`) |
| #11 | WP2b combat log | hero-relevant entries as one zstd blob (`ReplayCombatLogDomain`, `CombatLogCodec`, `CombatLogReader`) |
| #12 | review fixes | shared `LimitedCopier`, codec range checks, partial runs retried once, keep stored replay info |
| #13 | WP2c map model | `MapLayout`/`MapLayouts`, frozen `replay/map-layout.json`, `mapVersion` fingerprint |
| #14 | WP2d analysis framework + activity | `ReplayAnalyzer`/`ReplayAnalysisService`, `ActivityAnalyzer` (`PlayerActivitySegmentDomain`) |
| #15 | WP4a fights | `FightDetector`/`FightAssessor`/`FightAnalyzer` (`FightDomain`, `FightPlayerDomain`) |
| #16 | WP4b hero state | items gained/lost, cooldowns, skill levels (`ReplayStateChangeDomain`) |
| #18 | docs | this file, the Discord report plan, 5 more spike programs |

**Open, stacked PRs, not merged** (each is based on the one before; merge them in this order, oldest first; merging a PR to `main` deploys, and the ones marked T add tables, so take a `pg_dump` first):

| PR | What |
|---|---|
| #17 | deaths: `DeathAnalyzer`/`DeathAssessor`/`HeroStateTimeline` (T: `death_analysis`) |
| #19 | fight reasons: `NO_TP` / `TP_ON_COOLDOWN` / `HAD_TP_NOT_USED` for a hero away from a fight |
| #20 | mana cost changes and absolute mana in the state changes and tracks |
| #21 | `FightAbilityAnalyzer`: per fight and hero, abilities and items cast or not and why (T: `fight_ability_use`) |
| #22 | front-line rank (`FrontlineRanker`) and the attack range in the tracks; `PlayerTraces` loader |
| #23 | `OUT_OF_POSITION` death reason |
| #24 | disables counted until removed, and `BKB_PIERCED` with the modifier that went through BKB |
| #25 | buybacks (`BuybackAnalyzer`, T: `buyback_event`), Aegis-versus-buyback-versus-fountain respawn, and **`GameClock`: sampling in game time through pauses** (a real bug found on a match with 218 s of pauses) |
| #26 | `TeamStateAnalyzer`/`TeamStateSegmenter` (T: `game_state_segment`) from OpenDota net worth |
| #27 | `BuildingKillAnalyzer` (T: `building_kill_context`), `MatchMapLayout` |
| #28 | `PhaseSummaryAnalyzer` (T: `player_phase_summary`) |
| #29 | `SpikeAnalyzer`/`SpikeDetector` swings and causes (T: `game_spike`, `game_spike_factor`); the All Pick spike S9 result |
| #30 | `ReplayPostgresSmokeTest`: the whole pipeline against a real PostgreSQL |
| #31 | Discord report R0: per-channel `replay_report` switch, thread storage (T: `match_report_thread`, `replay_report_posted`), `ReplayReportService`, `SwingSection` |
| #32 | `DeathSection`, `FightSection` |
| #33 | `DraftSection`, `MovementSection` |
| #34 | `/api/v1/replay` run status and re-analysis endpoints; the switch-on steps |

Extractors (L1, need the replay): players, ability-draft, tracks, minutes, combat-log, state-changes.
Analyzers (L2, stored data only, `@Order`): activity (10), fights (20), fight abilities (25), deaths (30), buybacks (35), team states (40), buildings (45), phases (50), spikes (60). Report sections: `ReplayReportSection` beans, see the Discord part of `CLAUDE.md`.

## 3. How we work (rules that bind)

- **Worktree per change**, never edit `dbuff/` on `main` (container `CLAUDE.md`). Create from a fresh `origin/main`:
  `git -C dbuff -c credential.helper='!gh auth git-credential' fetch https://github.com/antoncorekt/dbuff.git main:refs/remotes/origin/main`
  then `git -C dbuff worktree add ../dbuff-<name> -b feature/<name> origin/main`. The local `dbuff/` checkout is stale
  (742f579); SSH push/fetch is broken, always use the https + `gh auth git-credential` form.
- Commit identity: `git -c user.name="Anton Kozlovskyi" -c user.email="antoncorekt@users.noreply.github.com"`.
  **No AI attribution anywhere** (commits, PRs, code).
- Global rules: SOLID and DRY (ask before duplicating or violating), comments only where needed, INFO logs only
  when they earn their place, ask before expensive things (subagents, full sweeps, long loops).
- JDK 21 for Gradle: `JAVA_HOME=~/.sdkman/candidates/java/21.0.0.0+35-tem`. `spotlessApply` crashes locally: format
  with the standalone google-java-format 1.25.2 all-deps jar (download from Maven Central) run with
  `--skip-sorting-imports --replace` and these `--add-exports=jdk.compiler/com.sun.tools.javac.{api,file,parser,tree,util,code}=ALL-UNNAMED`,
  then sort imports lexicographically with static imports last after a blank line (spotless `importOrder '', 'static '`).
  Re-run the formatter after the last edit: CI's `spotlessCheck` also removes unused imports.
- Tests: `./gradlew :server:test` (CI; H2 in PostgreSQL mode) and, locally only,
  `REPLAY_FIXTURE_DIR=<dir with the .dem files> ./gradlew :server:replayTest` (tag `replay`, runs at -Xmx512m).
  CI never needs a replay; replays are never committed.
- **PR rhythm the user expects:** implement, add tests (unit + DB + a replay-tagged check against OpenDota numbers),
  commit, push, open the PR, and report. The user then says "run review", "merge" and "continue": run the
  `code-review` skill on the PR number, triage the findings (the reviewer sometimes diffs against old history and
  lists already-merged code, so separate real findings in our code from noise), fix the valid ones on the same
  branch, wait for CI (`gh pr checks <n> --watch`), merge with `gh pr merge <n> --merge`, check the production deploy
  and logs (`AWS_PROFILE=dbuff AWS_REGION=eu-north-1`, log group `/aws/ec2/dbuff`, stream `<instance>/app`), then start
  the next item. **Merging to `main` deploys to production** and creates new tables via Hibernate `ddl-auto=update`
  (no Flyway); never merge without the user's word.
- Shell gotchas: zsh errors on unmatched globs; `sleep` is blocked in the Bash tool (use `gh run watch`); `timeout`
  does not exist on macOS.

## 4. Test fixtures and spike tooling (these live outside git)

- Two Ability Draft replays: matches **9038725154** (2026-10-10, 45 min) and **9037038717** (2026-10-09). They were
  in a session scratchpad (`/private/tmp/claude-502/.../scratchpad/replays`, likely gone). To recreate: `GET
  https://api.opendota.com/api/matches/<id>` gives `cluster` and `replay_salt`; download
  `http://replay<cluster>.valve.net/570/<id>_<salt>.dem.bz2` (the file is **Zstandard** despite the name; `zstd -d`).
  Valve keeps replays about 3 months, so these two expire around **2027-01**; after that the replay tests need new
  matches and new expected numbers (every assertion cites OpenDota's data for match 9038725154: copy the match
  JSON from the API when choosing a new one).
- Spike programs in `scripts/replay-spikes/` (Clarity probes: `Dump`, `Fields`, `Timeline`, `Sample`, `MapProbe`,
  `Cooldown`, `Deaths`, `Totals`, ...). They ran from a scratch Gradle project with `src/main/java/spike`; the
  committed `build.gradle` points sources at the folder but has not been run from there.
- Clarity 5.0.1 quirks learned: `Entity.getProperty` is generic, so inside string concatenation or `String.valueOf` it
  infers `char[]` and throws `ClassCastException` (assign to `Object` first); vectors are `skadistats.clarity.model.Vector`;
  entity names come from the `EntityNames` string table (`@UsesStringTable("EntityNames")`); entities are gone at the
  end of the replay, so read them while it streams; array fields have empty slots past their end (check for null).

## 5. Facts found about the replay data (do not re-derive)

- Clock: 30 ticks/s; game state 5 starts 0:00; the pre-game phase (state 4) starts 90 s earlier; states 8 and 10
  occur before 4. Combat-log time minus `m_flGameStartTime` is game time. The replay's `m_unMatchID64` is a lobby id,
  not the match id: identity is checked through the roster (`ReplayRosterVerifier`).
- Replay player id = 2 x player index (0-4 Radiant, 5-9 Dire); OpenDota slots are 0-4 and 128-132 (`PlayerSlots`).
  Team counter arrays (`m_vecDataTeam`) are indexed by position within the team.
- World position = `(cell - 128) * 128 + vec` from `CBodyComponent`. Lane = tower names via `EntityNames`
  (`dota_goodguys_tower1_bot` ...); 28 jungle camps in `m_NeutralSpawnBoxes` (12 of 40 slots empty); no Roshan spawner
  entity at game start.
- Combat log (S2): attacker = caster/damager, target = victim, inflictor = ability/item/modifier. Summed hero
  damage, ability and item use counts equal OpenDota's. 95 hero deaths against OpenDota's 94: the extra is an Aegis
  use. The Ancient's fall is logged after the state change, so the parse reads 10 s past the end. `TEAM_BUILDING_KILL`
  has no usable killer.
- Cooldown (S3): `m_fCooldown` is the time **remaining**, counting down in real time; `m_flCooldownLength` is the
  full length and is 0 for some abilities.
- OpenDota lane numbering is geometric: 1 bottom, 2 mid, 3 top for both teams.

## 6. Verification state (do the rest before turning the feature on in production)

Verified on 2026-10-12 with `ReplayPostgresSmokeTest` (#30): the whole pipeline on two All Pick matches against a real
PostgreSQL 16 (Hibernate creates 41 tables, `integer array` columns, `bytea`, a re-run replaces the rows, deletion
works), the real downloader against a local HTTP server, zstd decompression and the parse (about 1 s for 35 MB), the
roster check against stored player rows, and the five report sections run on the stored rows.
Per-player deaths, buybacks (8 of 8 against OpenDota's logs), building counts and the tracks' alignment with the combat
log (including a match with pauses) were checked against OpenDota on three matches.

Still NOT verified:
1. **A real download from Valve** has never run (only a local HTTP server).
2. **zstd-jni on the Linux arm64 host** (S8): it ran on arm64 macOS; the production platform is a different native
   library. Fallback is `io.airlift:aircompressor`.
3. **Memory in the real app at `-Xmx512m`** next to Spring and Postgres, and 90-minute games. The parse alone passes at
   512 MB in `replayTest`.
4. Thresholds (lane corridor, farm window, fight burst/gap, death rules, swing and team-state thresholds,
   out-of-position, the 3000-unit Aegis radius) were tuned on **2 to 3 matches**; the plan (S10) wants 20. Roamers,
   other patches and Turbo are untested.
5. The report text was read on two matches printed from PostgreSQL, but never shown in a real Discord channel.
6. `ReplayParseRunDomain.extractorStatus` was not built; so a partial run cannot be re-analysed (the re-analysis
   endpoint leaves PARTIAL runs out).
7. `m_iAttackRange`: whether item and talent bonuses are in it. The instant fountain respawn seen once in a match
   (not an Aegis, not a buyback) has no known cause.

## 7. What is left, in order

Done since the first version of this file: deaths (#17), fight reasons and ability use (#19-#21), front-line and
out-of-position (#22, #23), BKB piercing (#24), buybacks (#25, with a match that has them), team states (#26),
buildings (#27), phase summaries (#28), swings (#29), the PostgreSQL smoke test (#30), the Discord reports for swings,
deaths, fights, movement and draft (#31-#33), re-analysis endpoints and the switch-on steps (#34).

1. **Review and merge the stack** in the order of section 2, on the user's word (backup first), then check production
   logs. Then follow the switch-on steps in `CLAUDE.md` ("Switching it on in production").
2. **Group 2 (plan 6), not started:** runes and wards (spike S5: entity create/delete versus counters), courier,
   lane equilibrium, pre-game summary, `LaneAssignmentDomain`/`LaneMatchupDomain`, and the early-game report section
   R2. Needs new extraction for runes and wards.
3. **Team behaviour (plan 8.3), not started:** `TeamBehaviorDomain` for AHEAD, BEHIND and EVEN stretches, and the
   activity classifier's fight override.
4. **Group 6 rest (plan 10), not started:** key-item purchases as a swing cause, `FEEDER`, the net worth outliers,
   `PlayerOutlierDomain`; the casting ability behind a piercing modifier (`BKB_PIERCED` names the modifier only).
5. **End game (plan 9.5):** `EndGameAnalysisDomain`, `canBuyback` readiness at fights and deaths.
6. **Report extras:** R7 (`/match <id>` shows the replay sections, a header marker, an admin repost), the early-game
   section, ranking by severity times confidence, a message when the replay could not be read.
7. **Operations:** metrics per status, `extractorStatus`, a 20-replay load test at 512 MB, threshold calibration on 20
   matches (S10), a decision about the older review findings listed in section 9.
8. **Housekeeping:** many merged worktrees `../dbuff-*` and local branches can be removed
   (`git -C dbuff worktree remove <path>`; delete the branches); update the local `dbuff/` checkout. A throwaway
   PostgreSQL container `dbuff-smoke` may still be running (`docker rm -f dbuff-smoke`).

## 8. Decisions already made by the user (do not re-ask)

- Replay parses run **one at a time**, the number is configurable (`replay.processing.max-concurrent`, default 1).
- Replay-parsing tests run **locally only**; CI skips them.
- Discord replay reports: **on by default for existing channels**; a newly onboarded channel gets an explicit
  setting (on by default, shown at registration and in `/dbuff settings`); a server-wide kill switch
  `replay.report.enabled` starts **off**; message caps stay (4 messages at FULL, 2 at STANDARD, none at MINIMAL);
  the `postToThread` refactor is approved.
- The replay report is a follow-up message in the match thread when the replay run finishes, not a new thread.

## 9. Review findings deliberately not acted on (so they are not rediscovered)

- Plain `http://` replay URLs: Valve serves them over HTTP only; integrity rests on the roster check.
- Camp-before-lane order in `MapLayout`, lane flips near converging towers: calibration matched OpenDota 20/20.
- The starting inventory appears as items "added" at the first sample (before the horn); documented in `StateChange`.
- Older, already merged code the reviewer flagged (not ours): `LastMatchesProcessorService.importQuietly` has an
  unbounded `join()`, `InstanceSettingsApplier` does not accept `default` for the report detail, `scripts/set-api-key.sh`
  uses bash-only syntax under `AWS-RunShellScript`, `ApiKeyFilter` rejects CORS preflight, `AllowedApiCidr` defaults
  to `0.0.0.0/0`. Listed for a separate decision by the user.
