# Replay pipeline: status and next steps (resume point)

Written 2026-10-12. Read this first in a fresh context. It says what is built, how we work, what has NOT been
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
| #17 | WP6a deaths | `DeathAnalyzer`/`DeathAssessor` (`DeathAnalysisDomain`) - **open when this was written; check `gh pr list`** |
| this PR | docs | this file, the Discord report plan, 5 more spike programs |

Extractors (L1, need the replay): players, ability-draft, tracks, minutes, combat-log, state-changes.
Analyzers (L2, stored data only, `@Order`): activity (10), fights (20), deaths (30).

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

## 6. NOT verified yet (do before turning the feature on in production)

1. **A real download from Valve has never run.** The downloader is tested against a local HTTP server only.
2. **`zstd-jni` on the arm64 host** (spike S8) is unverified; fallback is `io.airlift:aircompressor`.
3. **The pipeline has never run end to end against PostgreSQL.** All DB tests use H2: `integer array` columns,
   `bytea`, the roster check against real stored OpenDota rows and table growth are untested on Postgres.
4. **Memory in the real app at `-Xmx512m`** (Spring + Postgres on the 2 GiB host). The parse alone passes at 512 MB in
   `replayTest`; 90-minute games are untested.
5. Thresholds (lane corridor, farm window, fight burst/gap, death rules) were tuned on **2 matches**; the plan (S10)
   wants 20. Roamers, All Pick and other patches are untested.
6. `ReplayParseRunDomain.extractorStatus` (per-extractor status) was planned but not built; failures are only in the
   `error` text.
Suggested first step toward enabling: a small end-to-end smoke (local Postgres from `docker-compose.yml`, a local
HTTP server serving a compressed sample replay through `replay.processing.url-template`, run `ReplayRunService`).

## 7. Next PRs, in order

Each is its own branch and PR with replay-tagged checks against OpenDota where possible.

1. **Review and merge #17** (deaths), then check production logs.
2. **Fight reasons (WP4c):** for each fight and player: had a teleport (scroll/boots owned and ready) with `NO_TP`,
   `TP_ON_COOLDOWN`, `HAD_TP_NOT_USED`; per ability: ready at the fight start (`HeroStateTimeline.ready`), cast in the
   fight (combat-log ABILITY), and why not (`ON_COOLDOWN`, `NO_MANA`, `DISABLED`, `DEAD_EARLY`, `HELD_BACK`), including
   "used on creeps 20 s before" (damage events by that inflictor on non-hero targets). Needs: ability mana cost
   (add `m_iManaCost` to the state recorder), ability metadata (passive, cast range) from the existing OpenDota
   constants through a small interface like `AbilityNames`, and mana % from the track. Table `FightAbilityUseDomain`
   (plan 7.2/7.3).
3. **Front-line check:** project positions at the fight start on the axis between team centroids; `frontlineRank`,
   expected rank (max health, attack range, role), `frontlineDelta` on `FightPlayerDomain`; "out of position" for
   deaths (plan 7.3, 9.2). Needs hero max health / attack range (add to the sampler or take from constants).
4. **BKB pierce table and disables (S4):** read `bkbpierce` / dispellable from the OpenDota ability constants (or a
   curated resource); split disables that pierce from those that do not in the death analysis.
5. **Buybacks (S11) and end-game (WP6b):** sample unspent gold and the buyback cooldown (`m_flBuybackCooldownTime`,
   gold counters) on the grid; `BuybackEventDomain`; `canBuyback`; `EndGameAnalysisDomain` (decisive fight, ancient
   fall, last carry death). The sample match has no BUYBACK combat-log entry, so find a match with buybacks first.
6. **Group 4 (WP5):** add `NET_WORTH` to the minute counters (`m_iNetWorth`); `TeamStateSegmenter` (lead % with
   hysteresis), `TeamMinuteStateDomain`, `GameStateSegmentDomain`, `BuildingKillContextDomain` (assists near, fight
   link, matched to OpenDota's `ObjectiveEventDomain`), `PlayerPhaseSummaryDomain`, `TeamBehaviorDomain` (plan 8).
   Add the activity classifier's fight override (ACTIVITY pass 2) here.
7. **Group 2 (WP3):** runes and wards (spike S5: entity create/delete vs counters), courier events, lane
   equilibrium (lane-creep entities), pre-game summary, `LaneAssignmentDomain`/`LaneMatchupDomain` (L2: the lane
   dominance rule is already validated against OpenDota, 10/10 on two matches) (plan 6).
8. **Group 6 (WP7):** spike detection on the net-worth difference, cause factors, outliers; needs 6 (plan 10).
9. **Group 1 extras:** All Pick capture (needs a mode-22 replay, spike S9), nightly `AbilityPairStat` /
   `AbilityPickPositionStat` aggregates, queue-position queries (plan 5).
10. **Discord reports (WP8 = R0-R7)** per the report plan: persist thread ids (`MatchReportThreadDomain`), the
    `postToThread` refactor of `MatchReportOrchestrator.processAndReportFull` (approved), `ReplayReportService`
    listening to a `ReplayAnalysisCompleted` event, one analyzer + formatter per group, the per-channel
    `replayReport` setting. R1 (draft section) only needs what is already stored, so it can come right after the
    smoke test and be the first user-visible result.
11. **Operations (WP9):** admin recompute of an `analysisVersion` (shares the parse semaphore), metrics per status,
    retention checks, a 20-replay load test at 512 MB, threshold calibration on 20 matches (S10), the
    production switch-on checklist of section 6.
12. **Housekeeping:** many merged worktrees `../dbuff-*` and local branches can be removed
    (`git -C dbuff worktree remove <path>`; delete the branches); update the local `dbuff/` checkout.

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
