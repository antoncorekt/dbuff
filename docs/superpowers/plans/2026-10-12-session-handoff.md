# Session handoff, 2026-10-12: queue, game modes, roles, review loop

For an agent with no memory of the session. Read this, then `CLAUDE.md` ("Replay pipeline"), then `2026-10-12-replay-status-and-next-steps.md` (older, with an update section at the top of its section 7). Facts here were true at the end of the session; check `git log` and `gh pr list` before relying on them.

## 1. Rules of the owner (they bind)

- Container `/Users/anton/Documents/dbuffContainer` is not a git repo; the repo is `dbuff/` (GitHub `antoncorekt/dbuff`). Never develop on `main` there: use a worktree `../dbuff-<name>` on a branch `feature|fix|chore|docs/<name>`; remove it when merged. The local `dbuff/` checkout is kept on `main`, clean (fast-forward it with a fetch).
- SOLID and DRY; if a change would break either, stop and ask. Comments only where needed. INFO logs only where each line earns its place. **Never any Claude or AI attribution** in commits, PRs, issues, code or docs. Plan first, then implement. Ask before anything expensive (the owner did approve subagents and full test runs for this work).
- **Merging to `main` deploys to production** (CI deploy job with a health gate and automatic rollback; Hibernate `ddl-auto=update` creates tables, no Flyway). Merge only on the owner's explicit word for that PR ("merge it", "merge it when ready" counts for the PR it was said about). Replay processing and the report are **off in production** (`REPLAY_PROCESSING_ENABLED`, `REPLAY_REPORT_ENABLED`); never turn them on without the owner's word; the switch-on order is in `CLAUDE.md` ("Switching it on in production"). The owner said not to check AWS after merges while replay is off; a deploy run that ends green on GitHub is enough.
- **Standing rule: after every implementation stage run the `code-review` skill on the PR, triage, fix the valid findings, then continue.** Triage honestly: fix real defects, skip old-code noise and tuning questions (record those as known hypotheses). Do not dismiss a formatting finding without checking it against CI: one slipped on an import-order finding.
- For the owner a "review loop" (match list, generated report, assistant and owner verdicts per row, rounds) is the way to improve analysis rules: `docs/replay-review-loop.md`, record per round in `docs/replay-role-loop/`.

## 2. How the session worked (reuse this rhythm)

1. Plan in a doc (`docs/superpowers/plans/`), docs PR, owner answers questions.
2. One worktree and one implementation agent per PR, with a self-contained prompt: scope, files not to touch, rules, formatter commands, how to test, how to report. Two agents in parallel only when their classes do not overlap (processing versus parsing; loop tooling versus resolver).
3. Agent opens the PR and waits for CI. The lead runs `code-review`, triages, sends the valid findings back to the same agent (SendMessage), the agent fixes, pushes, CI green.
4. Before merging: check that CI ran on the **head commit** of the PR (`gh run list --branch <b>` headSha equals `gh pr view` headRefOid), mergeable and clean; merge with `gh pr merge <n> --repo antoncorekt/dbuff --merge`; then find the deploy run of the merge (it can lag a few seconds; take its id from `gh run list --branch main`, not a stale id) and `gh run watch <id> --exit-status`.
5. Agent reports are model output: verify state with `gh` before acting; read the code when a claim matters.

## 3. Build and environment quirks

- JDK 21: `JAVA_HOME=~/.sdkman/candidates/java/21.0.0.0+35-tem`; call `$JAVA_HOME/bin/java` explicitly for the formatter (another `java` on PATH breaks it).
- `./gradlew spotlessApply` crashes locally. Format every changed or new Java file, main and test, with google-java-format 1.25.2: `java --add-exports=jdk.compiler/com.sun.tools.javac.{api,file,parser,tree,util,code}=ALL-UNNAMED` (each its own argument, before `-jar`; in zsh use an array) `-jar /private/tmp/claude-502/-Users-anton-Documents-dbuffContainer/adf443da-919a-4382-bdac-ad32f1154003/scratchpad/gjf/google-java-format-1.25.2-all-deps.jar --skip-sorting-imports --replace <files>`, then sort imports in strict lexicographic order of the full name (`a.B` before `a.B.C`), static imports last after a blank line. CI `spotlessCheck` fails on any deviation.
- SSH push is broken: `git -c credential.helper='!gh auth git-credential' push|fetch https://github.com/antoncorekt/dbuff.git <branch>`. Commit identity: `git -c user.name="Anton Kozlovskyi" -c user.email="antoncorekt@users.noreply.github.com"`.
- zsh: no word splitting of variables, an unmatched glob is an error, no `timeout` on macOS, long `sleep` is blocked (use a loop or `gh run watch`).
- Real-replay tests (`replay` tag) and the loop (`roleloop` tag) run only locally: `REPLAY_FIXTURE_DIR=/private/tmp/claude-502/-Users-anton-Documents-dbuffContainer/adf443da-919a-4382-bdac-ad32f1154003/scratchpad/replays`, a docker `postgres:16-alpine` (user and password `postgres`/`password`, db `dbuff`, port 5544), `./gradlew :server:replayTest` or `:server:roleLoop`. The fixture directory holds `.dem`, `.dem.zst` and OpenDota `.json` of four matches (Ability Draft 9038725154, 9037038717; All Pick 9040892342, 9040889566) and `constants-*.json`; it is outside git and replays expire around January 2027. Local leftovers at the end: container `roleloop-pg` on 5544 (stored round-1 data), round reports under `dbuff/server/build/role-loop/round-01` and `dbuff-replay-role-round-02/server/build/role-loop/round-02`, about 19 worktrees `../dbuff-*` (remove the merged ones), the scratchpad above.
- Production read-only checks, only if asked: `AWS_PROFILE=dbuff AWS_REGION=eu-north-1`, log group `/aws/ec2/dbuff`, stream `i-08e7015203317e18a/app`; backups in `s3://dbuff-deploy-243450839214/db-backups/` (a dump was taken before the first replay merge).

## 4. What was done (all merged to `main` unless marked)

| PR | What |
|---|---|
| #37 (with #17 to #36 folded in) | the whole replay pipeline: extractors, analyzers (deaths, fights, abilities, buybacks, team states, buildings, phases, swings, outliers), Discord report sections, `/api/v1/replay` endpoints, smoke test |
| #35, #53 | status document updates |
| #39 | queue priority and flow: fresh matches first (6 h), young retries, backlog off by default, a new replay triggers the job, a report goes out right after its run (after review: after-commit event, trigger coalescing, no match twice in a run, report only for DONE or a final PARTIAL) |
| #40, #41 | game-mode profiles (Ability Draft 18, All Pick 22), `supports(mode)` on extractors, analyzers and sections, mode-aware parser and re-analysis, queue modes from the registry |
| #43 | `player_role` and the All Pick role resolver |
| #44, #45 | widened hero and ability constants, `position_est`, constants schema version with refetch; #45 fixed a production-relevant break: the generated OpenDota client had coercion off, so no constants fetch worked since 2026-05-07 (stored rows hid it) |
| #46 | role profiles with `INTENT` and `FINAL` stages (`player_role_profile`), `RoleFitter`, function profiles |
| #47 | team composition verdicts (`team_composition`, `CompositionRules`, `CompositionSection`) |
| #48 | loop tooling: `:server:roleLoop`, match list, reports, round diff, local-database guard |
| #49 | Ability Draft role resolver, `role-overrides.json`, per-mode `requires` |
| #50, #51, #52, #53 | review loop guide, round 1 and round 2 of the Ability Draft loop; round 2 accepted for now by the owner |
| **open** #38 | plan: queue priority, flow, game modes (the plan file is only on its branch, it was never merged; the implementation PRs followed it) |
| **open** #54 | plan: explaining a lost fight and why (`2026-10-12-replay-reasons-plan.md`) |

Production state: all of the above is deployed and healthy (checked after each merge until the owner said to stop checking); replay processing and the report are off, so none of it runs.

## 5. Where things are (read before changing)

- Replay code: `server/src/main/java/com/ako/dbuff/service/replay/`: `mode/` (profiles, registry), `clarity/` (the only package importing Clarity), `analysis/` (`role/`, `composition/`, `fight/`, `death/`, `spike/`, ...), `report/` (sections). Tests: `server/src/test/java/.../replay/` with `smoke/ReplayPipelineFixture`, `roleloop/`.
- Thresholds and weights are hypotheses in properties classes: `ReplayAnalysisProperties`, `RoleProfileProperties` (`replay.analysis.role-profile.*`), `CompositionProperties`, `ReplayProcessingProperties`. Overrides the constants cannot express: `server/src/main/resources/replay/role-overrides.json`.
- Loop: `docs/replay-review-loop.md` (method), `docs/replay-role-loop/README.md` (commands), `round-01.md`, `round-02.md` (the latter lists what is open).

## 6. State of the roles work and the open points

- Round 2 accepted for now. Open: the final stage still equals OpenDota's `position_est` for all 20 players (Brewmaster should be 1, Venomancer 3); seven intent rows still rejected; `CARRIES_3` at the pick stage of one team is too many; the attack-skill rule is probably too generous; All Pick not reviewed yet. The next input for the final stage is damage dealt and healing done per player from the stored combat log.
- Known limits: wards, runes, courier, early game, team behaviour, end game, `/match` replay detail are not built; `NO_VISION` needs wards (a separate extraction PR with a memory check at 512 MB).
- Unverified before switching processing on: a real download from Valve, zstd on Linux arm64 (only run on macOS arm64), memory on a 90-minute game at `-Xmx512m`, the report text in a real Discord channel.

## 7. What to do next

1. Ask the owner about the four questions in `2026-10-12-replay-reasons-plan.md` section 6, merge #54 (and decide on #38), then implement the plan's PR split: 6a loop made generic, 6b reason model, 6c role-aware reasons, 6d fight loss analyzer, 6e mode contributors, 6f report line; run the review loop on fights.
2. In parallel: roles final-stage data (combat log damage and healing) and a roles round 3.
3. Cleanups: `TeamSide` in `SpikeAnalyzer`, `TeamNetWorth`, `OutlierAnalyzer`, `BuildingKillAnalyzer` (each still has its own slot-128 rule); remove merged worktrees and local branches; the older review findings in section 9 of the status document.
4. Production switch-on only on the owner's word, in the documented order.

## 8. Memory files of the assistant (if the same user directory is used)

`dbuff-replay-project`, `dbuff-local-build-quirks`, `dbuff-replay-autonomous-loop`, `dbuff-review-after-impl-rule`, `dbuff-review-loop-method`, listed in `MEMORY.md`.
