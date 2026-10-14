# Replay: session handoff, 2026-10-14

For a fresh agent. Read this, then `2026-10-14-replay-switch-on-checklist.md`, then `2026-10-13-session-handoff.md` (environment, rules and rhythm; its PR list stops at #77 and is superseded by the table below) and `2026-10-12-session-handoff.md` (the owner's rules, shared agent rules block in section 8 of the 10-13 file).

## State

Everything planned is built, reviewed, merged to `main` and deployed (last PR #102). Nothing is open. Replay processing (`REPLAY_PROCESSING_ENABLED`) and the report (`REPLAY_REPORT_ENABLED`) are still OFF in production. Do not turn them on without the owner's word, in the order of `CLAUDE.md` and the checklist. Do not check AWS while replay is off.

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
| #89, #91, #92, #93, #94, #95, #96 | Discord sections, all default off: important-fights, lanes, rotations, smoke, objectives, vision, opening, items |
| #90 | one ability wording across the report (display names) |
| #97 | vision and rotation fight reasons (second pass `late-fight-reasons`, order 95) |
| #98 | key item behind a swing (factor type `ITEM`) |
| #99 | outlier explanations and the `outliers` section |
| #100 | metrics (`dbuff.replay.*`, `/actuator/metrics` exposed in prod behind the API key) |
| #101 | end game and throw detection (`end_game_summary`, `endgame` section) |
| #102 | 20-replay load test, arm64 zstd check, switch-on checklist, `scripts/replay-host.sh` |

## Findings worth keeping

- Load test, 20 real replays at 512 MB: 20 of 20 DONE, 87 s, live heap 96 to 100 MB (no growth), old-generation peak 114 to 161 MB (76-minute game highest). Valve downloads 17 of 17 (zstd body behind a `.bz2` URL). zstd-jni works on linux/arm64 (Docker on an arm64 Mac, not the host).
- Most important open risk before switch-on: `/tmp` on the host is probably RAM-backed; set `REPLAY_PROCESSING_TEMP_DIR=/var/tmp/dbuff-replays` after creating and write-testing it (checklist step 1).
- A match may lack replay location in OpenDota for 1 to 2 hours (`SKIPPED_NO_REPLAY_INFO`, retried by the queue).
- Still unverified: OpenDota series present when the replay is analysed (PARTIAL behaviour), the report text in a real Discord channel, a metrics read on the host, Valve reachable from AWS, a 90-minute game.
- Rule quality on two Ability Draft matches only: vision is lopsided even with the contrast rule; the rotation fight reason never fired; no throw and no feeder in these matches (rules proven by unit tests only). All thresholds are first guesses in properties.
- Environment variable form of a section switch: `REPLAY_REPORT_SECTIONS_IMPORTANTFIGHTS_ENABLED` (no underscore inside the section name); a wrong name stops start-up when the report is enabled.

## Open for the owner

1. Which Discord sections to enable first (all new ones are default off), after reading the rendered texts in `~/dbuff-fixtures/send/section-*.txt`.
2. The production switch-on (checklist) and when.
3. Whether to extend the review rounds to All Pick and more matches (the rules are tuned on two Ability Draft matches).
4. Cleanup (below).

## Cleanup not done (owner said later)

About 60 merged worktrees `../dbuff-*` and their branches, the docker containers of the loops (`fightloop-pg` if it still exists), `TeamSide` helper duplication in older analyzers (SpikeAnalyzer, TeamNetWorth, OutlierAnalyzer, BuildingKillAnalyzer), remaining `/private/tmp` paths in older plan docs, the `MinuteSeries`/`RowOrder` helper style choices.

## How the work was done (rhythm, unchanged)

One worktree and one background agent per PR with a self-contained prompt plus `~/dbuff-fixtures/agent-rules.txt`; the agent opens the PR and waits for CI; the lead verifies with `gh` (head SHA equals the PR head, mergeable), runs `code-review` for that PR (name the PR in the skill args), triages, sends valid findings to the same agent, checks CI on the new head, merges (`gh pr merge <n> --repo antoncorekt/dbuff --merge`) only under the owner's word or standing instruction, and watches the deploy run. Parallel PRs conflict in `CLAUDE.md`, `ReplayReportProperties`, `SectionSwitchContextTest`, `ReplayPostgresSmokeTest`: tell the agent to merge `origin/main` (no rebase), keeping both sides. Agent reports are model output: verify. Build quirks: see the 10-13 handoff (JDK 21, google-java-format jar, https push, commit identity, no AI attribution anywhere).
