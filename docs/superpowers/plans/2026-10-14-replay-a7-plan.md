# Replay: A7 and what comes next, detailed plan (2026-10-14)

Status: plan only, no code. Detail of section 13 of `2026-10-13-replay-next-analyses-plan.md`, written after A1 to A6 (B1 to B4) were merged. Every number is a first guess (a number in the text is a guess unless it is an external fact such as the replay expiry). The questions for the owner are in section 7, each with its default.

## 1. The main gap: nothing the analyses find reaches a user

The analyzers fill their tables, but only the older sections reach Discord (swing line with fight reasons, deaths, fights, movement, draft, composition). Not shown anywhere outside the local review loop: important fights as a list, vision, runes and the opening, lanes, item efficiency, rotations, group or split, lane swaps, smoke, objectives after fights. Replay processing is off in production, so the tables are also empty there.

So A7 has two halves: **surface** what exists (C1 to C3) and **finish** the open analyses (C4 to C6), then **prove it** on real load (C7).

## 2. C1: the replay analysis endpoint

- `GET /api/v1/replay/{matchId}/analysis` (behind the API key, next to the run status endpoint in `ReplayResources`): one JSON document read only from the stored tables: run status (read through the existing `GET /api/v1/replay/{matchId}` status reader, not a second one), roles and composition, important fights with reasons, vision per important fight, runes and opening, lanes with results, item efficiency, behaviour (rotations, stretches, swaps, smoke, objectives).
- One reader class per topic (the `FightExplanationReader` pattern) and a thin assembler, so a topic can be added without touching the others. Response records are separate from entities.
- Words stay out of the JSON: it carries codes, times in seconds and slots; the readable wording belongs to the report (C2). The loop's wording classes are the first source to share.
- Data need: none new. Risk: response size on a 90-minute game; sections can be requested with a `sections` parameter (default all).

## 3. C2: the report in Discord, in steps

- **C2.0 (first): a per-section switch.** `ReplayReportSection` has no switch today and a new bean posts as soon as `REPLAY_REPORT_ENABLED` is on. Add `replay.report.sections.<name>.enabled` (default false for every new section) read by `ReplayReportService`, with a test that a disabled section posts nothing. "Accepted" means the owner said so for that table in chat or in the loop round file; the switch is turned on in a later config change, not by the section's PR.
- Each analysis gets its own `ReplayReportSection` (the existing extension point), small, at `STANDARD` or `FULL` detail, so a short report stays short. Wording moves from the test-only loop classes (`LoopWording`, `GenericWording`, `FarmingWording`) into main code once and extends what main already has (`ReasonWording`, `Prettify`, `GameTime`, `ReportHeroNames`) instead of adding a second layer; the loop and the report share it and the loop keeps only the table layout.
- Order by value to a reader, each its own PR and its own review: (1) important fights list with the top reasons (already partly in the swing line); (2) lanes (who won each lane and why in one line); (3) rotations and smoke worth reading (only the GOOD, WASTED and DIED rows, capped); (4) objectives missed after a won fight; (5) item efficiency lines; (6) vision lines for important fights; (7) runes and opening.
- Every section is off by default until the owner has accepted that table in a loop round (already true for all the tables above on the two Ability Draft matches), and respects the existing caps (`max-messages`, per-line caps).
- The report stays off in production (`REPLAY_REPORT_ENABLED`), as before.

## 4. C3: reason rows for the other analyses

Fight reasons (`fight_reason`) feed the swing line, and the rune contributor (`RuneAdvantageReasonContributor`) already exists. Missing are two: vision (a `VisionVerdict` of `NO_VISION`, `INVISIBLE_HERO_NO_DETECTION` or `LATE_WARD` of the losing team becomes a team reason with new reason codes) and rotations (a WASTED or DIED `RotationVerdict` of a hero who left the fight's lane just before it becomes a player reason). Each is a contributor bean with a weight in `ReasonProperties`; the analyzer does not change; one per PR after its table is accepted, comparing the fights loop before and after (only important fights count). The rune one only needs a check that its weights still look right.

## 5. The open analyses

### C4: the key item behind a swing
- Goal: for each swing (`game_spike`), name the item purchase or completion that moved the team's income or fight strength in the window just before it ("Radiant finished a Black King Bar at 27:40, three minutes before the swing").
- Data: `ItemPurchaseEventDomain` and the state changes of items held (exist), item constants with cost and the whitelist from A5. Rule: the most expensive completed item of the favoured team in the window before the swing start (a property), counted only when its owner took part in the swing's fights; plain gold alone does not decide.
- Output: one `GameSpikeFactorDomain` row per swing with a new `factorType` value `ITEM` (`ref` the item name, `playerSlot` the owner, `goldValue` the item cost, `sharePercent` null because the shares of other rows overlap), the doc comment of the entity updated, readers that switch on the type told to ignore unknown values, and a line in the swing text. First loop round on the two matches; the swing analyzer version is bumped so a re-analysis writes the new rows.

### C5: outlier explanations
- Goal: say why a `NW_OUTLIER` or `FEEDER` is what it is, in a line: the income change point (the minute his gold per minute broke from the team's), the gold source that moved (lane farm, jungle, kills), the share of fights joined, and the deaths in the window.
- Data: per-minute gold and `ReplayPlayerMinuteDomain`, `PlayerActivitySegmentDomain`, deaths, rotations (a hero who rotated a lot lost farm). No new extraction.
- Output: new columns on `PlayerOutlierDomain` (change-point minute, main gold source, fights-joined share, explanation text); `ddl-auto=update` adds them as null, so old rows stay empty until `reanalyze` runs (the analyzer version is bumped for that). The gold source comes from the per-minute gold counters by source that the replay minute extractor stores. A section only after the owner accepts a loop round.

### C6: end game and the rest
- **End game:** buyback readiness at the last fights (buyback events exist; the gold needed from net worth) and a "throw" flag (guess) when a team lost a lead of at least 10% of net worth after a won fight with no objective taken (uses the B4 conversions). Table `end_game_summary`; loop topic `endgame`.
- **Courier:** courier gold per minute exists; an analysis only if the owner wants it (question 5), otherwise dropped.
- **Metrics per status** (counts of runs by `ReplayStatus`, last run duration) as Micrometer gauges that read the counts from `replay_parse_run` when scraped (so a restart loses nothing), memory from the JVM meters Spring already registers. No code uses `MeterRegistry` today, so the PR adds it, exposes `/actuator/metrics` (behind the API key; only `/actuator/health` is exempt) and documents the read command in `CLAUDE.md`. No new tables. Built as C6b: `ReplayMetrics` and `ReplayRunCounts` in `service/replay/metrics`; the run counts are one grouped query cached for 30 s; the queue length is not a meter because `ReplayQueue` has no cheap count query.

## 6. C7: proving it before it is switched on

- **20-replay load test** (local, tag `replay`, never in CI; 20 is a guess, enough to see heap growth): process 20 stored replays one after another at `-Xmx512m` with the real Postgres smoke setup, record heap peak, time per replay and the table row counts; fail on any `FAILED_*` status. Needs 20 replays: the four fixtures in `REPLAY_FIXTURE_DIR` plus 16 downloaded for recent tracked matches with the app's own downloader (cluster and salt from OpenDota, as the fixtures were; this is also the first real download check, so a failure here is a finding, not a blocker for the other steps). Replays expire about January 2027 (Valve's rule, not a guess), so the downloads come first.
- **The unverified list** (each is a small separate check, recorded in `CLAUDE.md` "Switching it on" before it counts as done): real download from Valve, zstd on Linux arm64, memory on a 90-minute game, the report text in a real Discord channel, OpenDota series being present when the replay is analysed (and what happens when they are not: PARTIAL).
- The production switch-on stays exactly as written in `CLAUDE.md` and only on the owner's word.

## 7. Questions for the owner (default in brackets)

1. Start with the replay analysis endpoint (C1) before any new Discord section? [Yes, it lets you read production data without Discord.]
2. Discord sections: C2.0 per-section switch first, then the order in section 3, one PR each, every section off until you accept its table? [Yes.] Tables accepted so far (two Ability Draft matches): lanes, important fights, vision, items, rotations. Not yet explicitly accepted: group or split, lane swaps, smoke, objectives (the B2 to B4 reports); those sections wait for your word.
3. Wording shared between the loop and the report moves into main code in C2 (the loop keeps only layout)? [Yes.]
4. Key item behind a swing (C4) and outlier explanations (C5): build both? [Yes, C4 first.]
5. Courier analysis: drop it? [Drop it, no clear question to answer.]
6. End game and throw (C6): build it? [Yes, small, after C4 and C5.]
7. Load test: run it when 16 more replays are downloaded, before January 2027? [Yes, as C7 after the surfacing steps.]
8. Order overall: C1, C2 (steps 1 to 7), C3 per contributor, C4, C5, C6, C7? [Yes.]

## 8. PR order and rules

C1, C2.0, C2.1 to C2.7, C3.1 to C3.3, C4, C5, C6, C7. One worktree and one agent per PR; each PR gets `code-review`, valid findings fixed, CI green on the new head, then merged under the owner's standing instruction; processing and the report stay off in production; no AI attribution.
