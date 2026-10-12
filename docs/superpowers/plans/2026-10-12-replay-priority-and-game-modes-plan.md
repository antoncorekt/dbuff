# Replay: queue priority, flow, and game modes

Status: plan, nothing built. Replay processing stays off in production until the owner says otherwise. Written after the replay pipeline was merged (PR #37); read `2026-10-12-replay-status-and-next-steps.md` for what exists.

## 1. Where the code stands

- `ReplayQueue.next()` takes due retries first, then new matches ordered `startTime desc`. A retry of an old match beats a brand-new one, and a backlog fills the rest of a batch.
- The replay job runs on its own cron (`replay.processing.cron`, every 10 minutes). Nothing tells it that a match is new. `MatchParseCheckJob` and the replay job are decoupled.
- `ReplayBatchService` fetches the whole batch before it starts, so a match that arrives mid-batch waits behind it.
- Analyses already run inside `ReplayRunService.conclude()` straight after the parse and extract. The Discord report is delivered once per job run, after the whole batch (`ReplayProcessingJob`).
- `replay.processing.supported-modes` exists (default `[18, 22]`) and is enforced twice: in the queue query and in `ReplayEligibility` (`SKIPPED_NOT_SUPPORTED`). Mode 22 is `game_mode_all_draft`, which the client calls All Pick (see `GameModeResolver`). Nothing downstream knows the mode.
- Ability Draft hardcodes: `AbilityDraftExtractor` (pool and pick counts, runs for every mode), `ClarityReplayParser` (reads `m_AbilityDraftAbilities` for every replay), `DraftFacts`, `DraftSection`, the `supportedModes` comment.
- No role concept exists in the replay code. Fights, deaths and outliers use activity, zones and front-line rank only.

## 2. The flow

new match → OpenDota parse requested → quest polls → parsed, details mapped (replay location stored) → match enters the fresh lane → replay job downloads, parses, extracts and analyses it → run status `DONE` or `PARTIAL` → report is posted into the match thread at once.

Analyses are not a separate wait: the signal is the run status. The replay stays dependent on `detailsMappedAt`; it does not start before OpenDota has parsed the match.

## 3. PR 1: queue priority

Order the queue takes matches in:

1. **Fresh matches**, details mapped within `replay.processing.fresh-window-hours` (6), newest first.
2. **Retries**, but only a retry that is younger than 24 hours and has had no more than 2 attempts. Such a retry goes before older new matches but after fresh ones.
3. **New matches that are not fresh**, newest first.
4. **Old retries** (older than 24 hours or more than 2 attempts) and the **backlog**, only when `replay.processing.backlog.enabled` is true (default **false**, stays false until the owner says), limited by `backlog.batch-size`, and only when lanes 1 to 3 are empty.

Mechanics:

- The job asks for the next match after each one finishes, not for a whole batch. A fresh match waits at most for the parse in flight (one parse at a time is a standing decision, so a running parse is not pre-empted).
- After the details mapper stores the replay location it publishes `ReplayRequested(matchId)`; a listener triggers the replay job through Quartz (`@DisallowConcurrentExecution` prevents overlap).
- Priority is per match, not per instance: a match tracked by several instances has one run.
- Properties: `fresh-window-hours`, `retry-priority-hours` (24), `retry-priority-attempts` (2), `backlog.enabled`, `backlog.batch-size`.
- Tests: queue ordering with fresh, young retry, old retry and backlog rows; a fresh match arriving mid-batch is taken next.

## 4. PR 2: flow and report timing

- Deliver a match's report right after its run ends instead of calling `deliverPending()` after the batch.
- A replay Valve does not have yet gives `FAILED_DOWNLOAD` with the existing backoff retry, and stays in the fresh lane while it is fresh.
- Update CLAUDE.md "Replay pipeline" and the status document.

## 5. PR 3: game-mode profiles (no behaviour change for Ability Draft)

- A `GameModeProfile` per mode (Ability Draft 18, All Pick 22), registered as beans. `supported-modes` stays the switch: a mode is used only if configured and registered, so a mode can be added later with one profile.
- `ReplayExtractor`, `ReplayAnalyzer` and `ReplayReportSection` get a default `supports(mode)` that returns true. Common ones stay untouched; `AbilityDraftExtractor` and `DraftSection` apply to Ability Draft only.
- The parser receives the mode as context and reads the Ability Draft pool through a `DraftReader` only for that mode. `ReplayFacts.draft` becomes a typed Ability Draft facts object. Table names stay (Hibernate `ddl-auto=update`).
- All Pick draft data comes from OpenDota's stored `DraftEventDomain`; the replay has no pick stream for it (S9).
- Tests: both modes through the real-replay suite (fixtures: two Ability Draft and two All Pick matches).

## 6. PR 4: roles

- New `player_role` table (match, player slot, role 1 to 5, `ASSIGNED` or `DERIVED`, confidence, evidence) written by `PlayerRoleAnalyzer`, which runs before the fight, death and outlier analyzers. Role resolution is a strategy per mode (`RoleResolver`).
- **All Pick resolver:** the hero is picked for a role. Use OpenDota lane role, the team's net worth and farm ranking, and our activity data (jungle, lane, roaming time). Consumers: carry in the jungle, out of position, phase summaries, outliers.
- **Ability Draft resolver (PR 5, own design first):** any hero can play any role depending on chosen abilities. Score the hero and abilities against each role and against what the team lacks. Needs hero and ability metadata from OpenDota constants and thresholds calibrated on real Ability Draft matches.

## 7. PR 6: mode-specific reasons

- The swing to fight to player chain exists in the data: the spike's FIGHT factor points to the fight, and fight rows hold the reasons. A joined read model is a query.
- Common reasons stay in the shared assessors (no teleport, item or ability on cooldown, no mana, bad position, carry in the jungle). A `ReasonContributor` per mode adds mode-specific ones (Ability Draft: weak ability combination; All Pick: hero and role mismatch).
- Reasons are stored as strings already, so no schema change.
- "No vision" needs wards, which are not built; "carry in the jungle" needs PR 4.

## 8. Order and rules

1, 2, 3, then 4; 5 and 6 afterwards. PRs 1 and 2 fix the production risk first and do not depend on the mode work. One worktree and one PR each, reviewed before the next; nothing is merged without the owner's word; no switch in production changes without it.
