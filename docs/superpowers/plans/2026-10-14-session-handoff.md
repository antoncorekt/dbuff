# Replay: session handoff, 2026-10-14

For a fresh agent. Read this, then `2026-10-14-replay-switch-on-checklist.md`, then `2026-10-13-session-handoff.md` (environment and build quirks; its PR list stops at #77 and is superseded by the table below; its section 8 holds the shared agent rules block, whose plan and worktree paths are stale: the plans are now on `main`) and `2026-10-12-session-handoff.md` (the owner's rules).

## State

Everything planned is built, reviewed and merged to `main` (last code PR #102; confirm with `gh run list --branch main` that its deploy run is green). This handoff (PRs #103 and #104) may itself still be open when you read it: check `gh pr list`. Replay processing (`REPLAY_PROCESSING_ENABLED`) and the report (`REPLAY_REPORT_ENABLED`) are still OFF in production. Do not turn them on without the owner's word, in the order of `CLAUDE.md` and the checklist. Do not check AWS while replay is off.

The owner's standing instruction (2026-10-14): merge each PR once it is reviewed (`code-review`, valid findings fixed) and CI is green on its head; continue until all PRs of the plan are done. That instruction covered the A6/A7 plan PRs (#78 to #102); a new body of work needs a new word.

## What was merged since the 10-13 handoff

| PRs | What |
|---|---|
| #78, #79, #86 | docs: handoff, team behaviour plan (A6), A7 plan |
| #80, #81 | rotations (B1), mid-game verdict and reasons (B1b) |
| #82, #83, #85 | group or split and lane swap (B2), smoke (B3), objectives after a won fight (B4) |
| #84 | lane for supports who shadow a carry (A4 fix) |
| #87 | `GET /api/v1/replay/{matchId}/analysis` (sections: run, roles, fights, vision, runes, lanes, items, outliers, behaviour, endgame) |
| #88 | per-section switch for the Discord report (`replay.report.sections.<name>.enabled`, `defaultEnabled()` false for new sections) |
| #89 important-fights, #91 lanes, #92 rotations and smoke, #93 objectives, #94 vision, #95 opening (the runes and opening section, branch `replay-runes-section`), #96 items | Discord sections, all default off |
| #90 | one ability wording across the report (display names) |
| #97 | vision and rotation fight reasons (second pass `late-fight-reasons`, order 95) |
| #98 | key item behind a swing (factor type `ITEM`) |
| #99 | outlier explanations and the `outliers` section (Discord, default off, order 78) |
| #100 | metrics (`dbuff.replay.*`, `/actuator/metrics` exposed in prod behind the API key) |
| #101 | end game and throw detection (`end_game_summary`, `endgame` section, Discord default off, order 79) |
| #102 | 20-replay load test, arm64 zstd check, switch-on checklist, `scripts/replay-host.sh` |

## Findings worth keeping

- Load test, 20 real replays at 512 MB: 20 of 20 DONE, 87 s, live heap 96 to 100 MB (no growth), old-generation peak 114 to 161 MB (76-minute game highest). Valve downloads 17 of 17 (zstd body behind a `.bz2` URL). zstd-jni works on linux/arm64 (Docker on an arm64 Mac, not the host).
- Most important open risk before switch-on: `/tmp` on the host is probably RAM-backed; set `REPLAY_PROCESSING_TEMP_DIR=/var/tmp/dbuff-replays` after creating and write-testing it (checklist step 1).
- A match may lack replay location in OpenDota for 1 to 2 hours (`SKIPPED_NO_REPLAY_INFO`, retried by the queue).
- Still unverified: OpenDota series present when the replay is analysed (PARTIAL behaviour), the report text in a real Discord channel, a metrics read on the host, Valve reachable from AWS, a 90-minute game.
- Rule quality on two Ability Draft matches only: vision is lopsided even with the contrast rule; the rotation fight reason never fired; no throw and no feeder in these matches (rules proven by unit tests only). All thresholds are first guesses in properties.
- Environment variable form of a section switch: `REPLAY_REPORT_SECTIONS_IMPORTANTFIGHTS_ENABLED` (no underscore inside the section name); a wrong name stops start-up when the report is enabled.

## Open for the owner

1. Which Discord sections to enable first (all new ones are default off: important-fights, lanes, rotations, smoke, objectives, vision, opening, items, outliers, endgame), after reading the rendered texts (files `section-<name>-<matchid>.txt` in `~/dbuff-fixtures/send/`, which exists only on the owner's machine and is not in the repo; the smoke test writes them).
2. The production switch-on (checklist) and when.
3. Whether to extend the review rounds to All Pick and more matches (the rules are tuned on two Ability Draft matches).
4. Whether to start the plan below, step by step (step 1 is the owner's own reading).

## Cleanup (2026-10-14, done at the owner's request)

At the time of cleanup (2026-10-14) removed: the 66 merged worktrees `../dbuff-*` (each checked clean and an ancestor of `main`; one had only `.DS_Store` changes), their local branches, the 102 merged remote branches on GitHub (each checked with the compare API as behind or identical to `main`; every PR stays readable; only `main` was left on GitHub, branches opened after that are new), the stale remote-tracking refs, and the stopped docker container `fightloop-pg`. Not touched: dangling anonymous docker volumes (possibly from other projects), `smoke.log` in the container directory, the fixtures in `~/dbuff-fixtures`.

Still to do (code and docs hygiene, none urgent): `TeamSide` helper duplication: `SpikeAnalyzer` still has its own private `teamOf(long slot)`; check `RunePickupAnalyzer`, `OpeningSummaries` and the report sections for inline slot-to-team code (`TeamSide.ofSlot`, `opponent()`, `commonSide` and the package-private `Prettify.teamOf(stored, yours)` exist now; the older list of TeamNetWorth, OutlierAnalyzer and BuildingKillAnalyzer was not confirmed), remaining `/private/tmp` paths in older plan docs, three copies of the load-test figures (CLAUDE.md, checklist, this file), the `RowOrder`/`BehaviourLines` helper style, a timing-based test in `ReplayRunServiceTest` (permit wait: sleeps 500 ms, asserts under 400 ms; make it deterministic if it ever flakes).

## Plan for next (proposal, in order; each step needs the owner's word to start, and inside step 2 every rule or threshold change is shown to the owner as a new round before the next one; never change rules or bump analyzer versions unprompted)

1. **Owner reads the rendered sections** (`~/dbuff-fixtures/send/section-*.txt`) and says which to enable first and which wording to change. Recommended first set: `important-fights`, `lanes`, `endgame`; hold `vision` (lopsided) and `outliers` (no real data) until more matches are reviewed.
2. **Review rounds on more matches.** 20 fixtures exist (`server/src/test/resources/replay/load-test/matches.txt`; 9 Ability Draft, 7 All Pick among the new 16): run the loops (`:server:reviewLoop -Ploop.topic=<fights|lanes|vision|runes|items|behaviour|roles>`, `docs/replay-review-loop.md`) over them, send the readable reports to the owner, tune the properties (first candidates: vision contrast rule, `rotation-window-seconds` 60 (the rotation reason never fired), the group or split 4-of-5 cluster rule (most time is MIXED), throw thresholds) with a new round per change. All Pick has had no review round at all.
3. **Production switch-on** only on the owner's word, following `2026-10-14-replay-switch-on-checklist.md` (step 1: backup, `df -h /tmp`, temp dir `/var/tmp/dbuff-replays`; step 2: processing on with batch size 1; step 3: batch up; step 4: report on, then sections one by one). Items still unverified there: OpenDota data present when a replay is analysed (PARTIAL path), the report text in a real Discord channel, a metrics read on the host, Valve reachable from AWS, a game of about 90 minutes (the longest tested is 76).
4. **Code hygiene** from the list above, as small PRs.
5. **Possible new analyses** (nothing planned): feeding explanations, buyback readiness from gold (needs unspent gold, not stored), courier, a stricter vision rule (observer-count difference), per-section Discord wording polish after the owner's feedback.

## How the work was done (rhythm, unchanged)

One worktree and one background agent per PR with a self-contained prompt plus `~/dbuff-fixtures/agent-rules.txt`; the agent opens the PR and waits for CI; the lead verifies with `gh` (head SHA equals the PR head, mergeable), runs `code-review` for that PR (name the PR in the skill args), triages, sends valid findings to the same agent, checks CI on the new head, merges (`gh pr merge <n> --repo antoncorekt/dbuff --merge`) only under the owner's word or standing instruction, and watches the deploy run. Parallel PRs conflict in `CLAUDE.md`, `ReplayReportProperties`, `SectionSwitchContextTest`, `ReplayPostgresSmokeTest`: tell the agent to merge `origin/main` (no rebase), keeping both sides. Agent reports are model output: verify. Build quirks: see the 10-13 handoff (JDK 21, google-java-format jar, https push, commit identity, no AI attribution anywhere).
