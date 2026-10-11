# Discord match report from replay data (plan for WP8)

Status: plan, not implemented. Written 2026-10-11. Expands WP8 of
`2026-10-11-replay-extraction-plan.md` (section 12); read that plan's sections 3a-3c first. This document covers only
how the parsed replay data reaches Discord, per match, for the channel's focus group.

---------------------------------------------------------------------------------------------------

## 1. How reports work today (verified on `origin/main` @ b2be071)

- `MatchReportOrchestrator` builds a `MatchReportContext` (match, player statistics, abilities, items, kill logs,
  instance config, focus player ids) and runs every `ReportAnalyzer` bean, sorted by `getOrder()`.
- Two stages per match, both in one Discord thread:
  1. `processAndReportPartial`: header message "waiting for parsed data...", thread `Match <id>`, analyzers that
     implement `PartialDataAvailable`.
  2. `processAndReportFull`, called by `MatchParseCheckJob` once OpenDota has parsed the match: edits the header
     (win/loss, score, duration), runs `FullDataAvailable` analyzers into the same thread.
- An analyzer returns `List<Report>`. `Report` is a Jackson-typed interface (`@JsonSubTypes`); `DiscordReportFormatter`
  turns reports into messages of at most 2000 characters with an `instanceof` chain and `splitAndAdd`.
- Per channel settings (`InstanceSettings`): report detail `MINIMAL < STANDARD < FULL` via
  `ReportAnalyzer.getMinimumDetail()` (default `FULL`), AI summary switch, quiet hours, game mode. A new analyzer
  therefore stays out of short reports until placed.
- One analyzer failing is caught and logged by `runAnalyzers`; the rest still report.
- **Gap that matters for this plan:** the thread id and header message id are carried only in Quartz job data
  (`MatchParseSchedulerService` to `MatchParseCheckJob`). They are not stored. A replay follow-up that arrives hours
  later (or after a restart) has no way to find the thread.

---------------------------------------------------------------------------------------------------

## 2. Delivery: a third stage in the same thread

```
Stage 1 partial  ->  Stage 2 full (OpenDota)  ->  Stage 3 replay findings  (new, minutes to hours later)
```

- **Trigger.** When `ReplayRunService` finishes a run with `DONE` or `PARTIAL` and the L2 analyzers have run, it
  publishes a Spring `ReplayAnalysisCompleted(matchId, analysisVersion)` event. `ReplayReportService` listens, so the
  replay pipeline knows nothing about Discord (DIP, SRP).
- **Which channels.** Every active instance that tracks at least one player of the match, has a Discord channel, and
  has replay reports enabled (section 7). The focus group is per instance, so one match can produce a different
  replay message in each channel.
- **Where to post.** The thread of that instance's earlier report. Store it when the thread is created:
  `MatchReportThreadDomain(matchId, instanceId, channelId, threadId, headerMessageId, createdAt)`, unique on
  `(matchId, instanceId)`, written by the orchestrator in both `processAndReportPartial` and
  `LastMatchesProcessorService` (the two places a thread is made), deleted with the match in `MatchDeletionService`.
  Quartz job data keeps working unchanged; the table is the durable copy.
- **No thread, no post.** If no thread row exists (history imported quietly at registration, the match was reported
  before this feature, thread deleted) nothing is posted and the reason is logged at DEBUG. Replay findings are never
  sent as a stray channel message.
- **Once only.** `ReplayReportPostedDomain(matchId, instanceId, analysisVersion, postedAt)`, unique on
  `(matchId, instanceId)`. A reprocess or an analysis recompute does not post again. An admin "repost" action may
  delete the row first; that is a later option, not part of the first version.
- **Quiet hours.** Honour `QuietHoursGuard.isQuietTime(settings)`: a replay that finishes during a channel's quiet
  hours is posted at the next job run after the window. A small `ReplayReportRetryJob` (or the existing replay job's
  tail) retries pending rows, so nothing is lost.
- **Header.** Edit the header message once more to append a short marker (for example "replay analysed"), using
  `discordMessageService.editMessage`. Optional, behind the same switch.
- **On demand.** `/match <id>` (`MatchCommand`) already posts a partial report for a chosen match; it additionally
  posts the replay sections if the match has a finished replay run. It uses the same analyzers (section 3), only
  without the once-only guard.

---------------------------------------------------------------------------------------------------

## 3. Code structure (SOLID, no change to existing behaviour)

| Piece | Responsibility | Notes |
|---|---|---|
| `ReplayDataAvailable` (marker, like `FullDataAvailable`) | `isApplicable(context)`: the match has a replay run `DONE` or `PARTIAL` and the rows this analyzer needs | analyzers stay out of stages 1 and 2 because they do not implement `PartialDataAvailable`/`FullDataAvailable` |
| `ReplayReportData` on `MatchReportContext` | read-only view of the L2 rows for this match and the focus players | loaded through the read interfaces of plan 3a.4 (`PlayerMinuteSeries`, `MatchDeaths`, ...), `null` when there is no replay; no analyzer queries repositories on its own |
| One analyzer per group: `DraftReplayAnalyzer`, `EarlyGameReplayAnalyzer`, `FightReplayAnalyzer`, `MovementReplayAnalyzer`, `EndGameReplayAnalyzer`, `SwingReplayAnalyzer` | SRP: select and rank the findings of one group, return reports | implement `ReportAnalyzer` and `ReplayDataAvailable`; `getOrder()` 20-25 (after the existing OpenDota ones); `getMinimumDetail()` per section 5 |
| `Finding` (value object) | what happened, for whom, when, why, how sure | `code` (the enums of the plan: `HELD_BACK`, `NO_TP`, `BKB_ON_COOLDOWN` ...), `playerId`, `gameSecond`, `severity`, `confidence`, `evidence` map |
| Report types: `DraftReport`, `EarlyGameReport`, `FightFindingsReport`, `MovementReport`, `EndGameReport`, `SwingReport` | data carried to the formatter; no text | register each in `Report`'s `@JsonSubTypes` (REST `analyzeMatch` serialises them) |
| `ReportSectionFormatter<T extends Report>` (new interface: `supports(Report)`, `format(T, names)`) | OCP: a new report brings its own formatter | the new formatters implement it; `DiscordReportFormatter` gets a `List<ReportSectionFormatter<?>>` and tries them before its existing `instanceof` chain. The existing branches are **not** rewritten in this work (migration is optional later) |
| `ReplayReportService` | event listener: find instances, load thread, build context, run replay analyzers, format, post, record "posted" | one responsibility: delivery of stage 3 |
| `MatchReportOrchestrator.processAndReportReplay(match, config, threadId)` | the same shape as `processAndReportFull` | the tail of `processAndReportFull` (filter, run, format, post) moves into one private `postToThread(...)` used by both: this edits an existing class to avoid copying it (DRY). It is a pure refactor and needs your approval before it is made |

Reuse, not rewrite: `runAnalyzers` isolates failures; `splitAndAdd` enforces the 2000-character limit;
`InstanceSettings.detail()` and `isEnabledFor` decide who sees what; `DiscordMessageService` posts; `MatchReportContext`
gets one nullable field. `ReportAnalyzerDetailTest` is extended to pin the placement of the new analyzers.

---------------------------------------------------------------------------------------------------

## 4. What each section says (deterministic templates; no prose stored, no AI needed)

All text is built from L2 rows. Every statement carries the game time (`23:10`) so a person can open the replay at
that moment and check it. Only the channel's focus players are named; the other nine are context. Wording states the
fact and its evidence, not blame ("held back" is shown as "had Ravage ready and enough mana, did not cast").

| Section | Source (plan group) | Example line |
|---|---|---|
| Draft | G1 `AbilityDraftPickDomain`, pool, pair stats | "Pick 4 overall (2nd on the team). Still available: Chronosphere, Ravage (won 61%)." |
| Early game | G2 lane, runes, wards | "Lane: offlane 1v2, lost (-14% net worth at 10:00). First tower fell 11:20. Enemy took both 3:00 bounty runes." |
| Fights | G3 `FightPlayerDomain`, ability use | "23:10 teamfight (lost 4-1): you were 3000 units away farming the jungle, no TP scroll (last bought 6:40)." / "Ravage ready, mana enough, not cast in 3 of 5 fights." |
| Movement and game state | G4 phases, state behaviour | "12-25 min: 62% jungle farming, joined 3 of 7 fights. While ahead the team took 1 tower in 9 min." |
| End game | G5 deaths, BKB, buyback | "41:10 carry death: no BKB owned; Sleep + Chain Frost chained 4.2 s; teammates 2600 units away." |
| Swings | G6 spikes, outliers | "28:00-31:00 +7.4k for Dire: a won fight (62%) and Roshan/aegis (21%); the Dire core had bought Blink 90 s earlier." |

Findings below `minConfidence` (default 0.6) or whose extractor failed are left out, never shown as guesses. A
section with nothing to say is omitted, and a match with no findings at all posts nothing.

---------------------------------------------------------------------------------------------------

## 5. Detail levels and size budget

Discord messages are 2000 characters and a thread is read by people, so selection matters more than completeness.
**Decided:** the message caps stay. They protect two things: Discord's per-message limit and rate limits when a thread
gets many messages, and readability, because a thread that grows by a dozen messages per match stops being read.

| Level | Replay content |
|---|---|
| `MINIMAL` | nothing from the replay (stage 3 is not posted) |
| `STANDARD` | Draft, Early game, Swings (top 2) and End game (focus deaths only): about 2 messages |
| `FULL` | all six sections, fights listed per focus player: up to `maxMessages` (default 4) |

Rules to keep it short, all configurable under `replay.report.*`:
- `maxFindingsPerSection` (default 3), ranked by `severity x confidence`, ties by time.
- Fights: only fights with importance above a threshold (towers taken, Roshan, deaths >= 3, or a focus player's
  `presence` anomaly), at most `maxFightsPerPlayer` (default 3) plus one aggregate line ("3 of 5 fights: ...").
- If the result exceeds `maxMessages`, the lowest-ranked findings are dropped first and one footer line says how many
  were left out (as `MatchTraceReporter` does with its cap), so truncation is stated, not hidden.
- Existing `splitAndAdd` handles line wrapping; sections are never split mid-finding.

---------------------------------------------------------------------------------------------------

## 6. Failure and degradation

- No replay, expired, failed run, or `SKIPPED_*`: stage 3 is simply not posted. The two earlier stages are unaffected
  and already contain everything OpenDota provides. The status stays queryable (plan 3c.1).
- `PARTIAL` run: sections whose extractor failed are omitted; the others post. No "error" text is sent to Discord.
- An analyzer throws: caught by `runAnalyzers`; the remaining sections still post.
- Discord failure (thread deleted, bot lost permission): log at WARN, do not record "posted" so a later run can retry
  at most `maxPostAttempts` times (default 3); then give up for that instance.
- The replay pipeline and the report pipeline are decoupled by the event: a report problem can never fail or retry a
  replay parse, and a replay problem can never block a report.

---------------------------------------------------------------------------------------------------

## 7. Settings

- Server kill switch: `replay.report.enabled` (default `false`, so the feature ships dark and is turned on once the
  first group is verified in production), and the limits from section 5. When it is off nothing is posted for any
  channel.
- Per channel: one new nullable `replayReport` value in `InstanceSettings`, set through `InstanceSettingsApplier`
  and `/dbuff settings replay_report:on|off`. **Decided:**
  - **Existing channels get replay reports by default.** Their value is null and null resolves to **on** (server
    property `replay.report.default-enabled=true`). This is a deliberate exception to the "null means behave as
    before" rule of `InstanceSettings`, so the class comment and `CLAUDE.md` must say so.
  - **Newly onboarded channels carry it as an explicit setting:** `/dbuff register` stores the value (on by default)
    and shows it in the `/dbuff status` and `/dbuff settings` output and in the welcome message, so the person
    registering sees it and can turn it off. It is not hidden behind the server default.
  - `STANDARD` and `MINIMAL` channels follow the detail level of section 5; the switch turns replay reports off
    regardless of detail.
- Hibernate `ddl-auto=update` creates the two new tables and the column; there is no migration script (project rule),
  and a `pg_dump` is taken before merging.

---------------------------------------------------------------------------------------------------

## 8. Work packages (each its own branch and PR; reports ship group by group)

The report side follows the extraction work packages, so the first user-visible result needs only WP0 and WP1.

| WP | Scope | Depends on | Done when |
|---|---|---|---|
| R0 | `MatchReportThreadDomain` written at thread creation; deletion hook; `ReplayAnalysisCompleted` event; `ReplayReportService` skeleton with the once-only table, quiet-hours retry, settings; `ReplayReportData` on the context; `ReportSectionFormatter` and the formatter delegation; the approved `postToThread` refactor | extraction WP0 | a fake analyzer posts one message into the stored thread once, and a second event does not repost |
| R1 | Draft section | R0, extraction WP1 | a Discord thread shows the draft finding for a focus player after a replay finishes |
| R2 | Early game section | R0, WP3 | section 4 line for lane and runes on two sample matches |
| R3 | Fights section | R0, WP4 | absence and unused-ability lines match the hand-checked examples of plan 7.4 |
| R4 | End game section | R0, WP6 | each focus death in the window has exactly one reason line |
| R5 | Movement and game-state section | R0, WP5 | phase and state lines for the focus group |
| R6 | Swings section | R0, WP7 | top swings with causes for two sample matches |
| R7 | `/match` replay detail, optional header marker, admin repost | R1+ | `/match <id>` shows the same sections without double posting |

---------------------------------------------------------------------------------------------------

## 9. Tests

- Analyzers: input rows from builders, assert the `Finding` list and ranking (no Discord, no database).
- Formatters: assert text for fixed `Report` objects; assert every message is at most 2000 characters and that
  truncation produces the footer line.
- `ReplayReportService`: with a fake `DiscordMessageService` and in-memory repositories: posts once; skips with no
  thread row; skips for `MINIMAL`; retries after quiet hours; survives a Discord error without recording "posted".
- Regression: `ReportAnalyzerDetailTest` extended; the existing stage 1 and 2 outputs are unchanged when
  `replay.report.enabled=false` (snapshot of one existing report).
- No replay file is needed for any of these (plan section 12): they run in CI.

---------------------------------------------------------------------------------------------------

## 10. Decisions for the user

| Decision | Default chosen |
|---|---|
| Post replay findings as a follow-up in the existing thread (not a new thread or channel message) | yes |
| Replay reports on by default | **on** for existing channels (null resolves to on); newly onboarded channels store an explicit setting, on by default, shown at registration and in `/dbuff settings`; server kill switch `replay.report.enabled` starts off |
| `MINIMAL` channels get no replay content | yes |
| Language and tone | English, factual with game times, no blame wording |
| Refactor `processAndReportFull` into a shared `postToThread` | **approved**; done in R0 as a behaviour-preserving change, covered by a test of the existing stage 2 output before and after |
| Persist thread ids in a new table | yes (needed; today they are not stored) |
| Message cap | **kept**: 4 messages at `FULL`, 2 at `STANDARD`, truncation stated in a footer line |
