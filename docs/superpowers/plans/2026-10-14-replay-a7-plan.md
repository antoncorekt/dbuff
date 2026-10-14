# Replay: A7 and what comes next, detailed plan (2026-10-14)

Status: plan only, no code. Detail of section 13 of `2026-10-13-replay-next-analyses-plan.md`, written after A1 to A6 (B1 to B4) were merged. Every number is a first guess. The questions for the owner are in section 7, each with its default.

## 1. The main gap: nothing the analyses find reaches a user

The analyzers fill their tables, but only the older sections reach Discord (swing line with fight reasons, deaths, fights, movement, draft, composition). Not shown anywhere outside the local review loop: important fights as a list, vision, runes and the opening, lanes, item efficiency, rotations, group or split, lane swaps, smoke, objectives after fights. Replay processing is off in production, so the tables are also empty there.

So A7 has two halves: **surface** what exists (C1 to C3) and **finish** the open analyses (C4 to C6), then **prove it** on real load (C7).

## 2. C1: the `/match` replay detail endpoint

- `GET /api/v1/replay/{matchId}/analysis` (behind the API key, next to the run status endpoint in `ReplayResources`): one JSON document read only from the stored tables: run status, roles and composition, important fights with reasons, vision per important fight, runes and opening, lanes with results, item efficiency, behaviour (rotations, stretches, swaps, smoke, objectives).
- One reader class per topic (the `FightExplanationReader` pattern) and a thin assembler, so a topic can be added without touching the others. Response records are separate from entities.
- Words stay out of the JSON: it carries codes, times in seconds and slots; the readable wording belongs to the report (C2). The loop's wording classes are the first source to share.
- Data need: none new. Risk: response size on a 90-minute game; sections can be requested with a `sections` parameter (default all).

## 3. C2: the report in Discord, in steps

- Each analysis gets its own `ReplayReportSection` (the existing extension point), small, at `STANDARD` or `FULL` detail, so a short report stays short. Wording moves from the test-only loop classes into main code once, shared by the loop and the report (DRY; the loop keeps only the table layout).
- Order by value to a reader, each its own PR and its own review: (1) important fights list with the top reasons (already partly in the swing line); (2) lanes (who won each lane and why in one line); (3) rotations and smoke worth reading (only the GOOD, WASTED and DIED rows, capped); (4) objectives missed after a won fight; (5) item efficiency lines; (6) vision lines for important fights; (7) runes and opening.
- Every section is off by default until the owner has accepted that table in a loop round (already true for all the tables above on the two Ability Draft matches), and respects the existing caps (`max-messages`, per-line caps).
- The report stays off in production (`REPLAY_REPORT_ENABLED`), as before.

## 4. C3: reason rows for the other analyses

Today only fight reasons (`fight_reason`) feed the swing line. Vision (`NO_VISION`, `LATE_WARD`), runes (`RUNE_ADVANTAGE`), and rotations (`WASTED`) can be extra `ReasonContributor`s for the fight they touch (a missed ward before a lost fight, a wasted rotation of the missing support). Each is a contributor bean with a weight in `ReasonProperties`; the analyzer does not change. Do one contributor per PR after its table is accepted, and compare the fights loop before and after (only important fights count).

## 5. The open analyses

### C4: the key item behind a swing
- Goal: for each swing (`game_spike`), name the item purchase or completion that moved the team's income or fight strength in the window just before it ("Radiant finished a Black King Bar at 27:40, three minutes before the swing").
- Data: `ItemPurchaseEventDomain` and the state changes of items held (exist), item constants with cost and the whitelist from A5. Rule: the most expensive completed item of the favoured team in the window before the swing start (a property), counted only when its owner took part in the swing's fights; plain gold alone does not decide.
- Output: one `GameSpikeFactorDomain` row (kind key item) per swing and a line in the swing text. First loop round on the two matches.

### C5: outlier explanations
- Goal: say why a `NW_OUTLIER` or `FEEDER` is what it is, in a line: the income change point (the minute his gold per minute broke from the team's), the gold source that moved (lane farm, jungle, kills), the share of fights joined, and the deaths in the window.
- Data: per-minute gold and `ReplayPlayerMinuteDomain`, `PlayerActivitySegmentDomain`, deaths, rotations (a hero who rotated a lot lost farm). No new extraction.
- Output: an `explanation` text and the structured facts on `PlayerOutlierDomain` (version bump); a section only after the owner accepts a loop round.

### C6: end game and the rest
- **End game:** buyback readiness at the last fights (buyback events exist; the gold needed from net worth) and a "throw" flag when a team lost a lead of at least 10% of net worth after a won fight with no objective taken (uses the B4 conversions). Table `end_game_summary`; loop topic `endgame`.
- **Courier:** courier gold per minute exists; an analysis only if the owner wants it (question 5), otherwise dropped.
- **Metrics per status** (counts of runs by `ReplayStatus`, durations, memory) as Micrometer meters on the existing actuator, so the staged production switch-on can be watched. No new tables.

## 6. C7: proving it before it is switched on

- **20-replay load test** (local, tag `replay`, never in CI): process 20 stored replays one after another at `-Xmx512m` with the real Postgres smoke setup, record heap peak, time per replay and the table row counts; fail on any `FAILED_*` status. Needs 20 replays: the four fixtures plus replays downloaded for recent matches (they expire in about January 2027, so this should be done before then).
- **The unverified list** (each is a small separate check, recorded in `CLAUDE.md` "Switching it on" before it counts as done): real download from Valve, zstd on Linux arm64, memory on a 90-minute game, the report text in a real Discord channel, OpenDota series being present when the replay is analysed (and what happens when they are not: PARTIAL).
- The production switch-on stays exactly as written in `CLAUDE.md` and only on the owner's word.

## 7. Questions for the owner (default in brackets)

1. Start with the `/match` replay analysis endpoint (C1) before any new Discord section? [Yes, it lets you read production data without Discord.]
2. Discord sections: the order in section 3, one PR each, all off until you accept the table? [Yes.]
3. Wording shared between the loop and the report moves into main code in C2 (the loop keeps only layout)? [Yes.]
4. Key item behind a swing (C4) and outlier explanations (C5): build both? [Yes, C4 first.]
5. Courier analysis: drop it? [Drop it, no clear question to answer.]
6. End game and throw (C6): build it? [Yes, small, after C4 and C5.]
7. Load test: run it when 16 more replays are downloaded, before January 2027? [Yes, as C7 after the surfacing steps.]
8. Order overall: C1, C2 (steps 1 to 7), C3 per contributor, C4, C5, C6, C7? [Yes.]

## 8. PR order and rules

C1, C2.1 to C2.7, C3.1 to C3.3, C4, C5, C6, C7. One worktree and one agent per PR; each PR gets `code-review`, valid findings fixed, CI green on the new head, then merged under the owner's standing instruction; processing and the report stay off in production; no AI attribution.
