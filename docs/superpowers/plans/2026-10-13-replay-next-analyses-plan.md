# Replay: the next analyses

Status: plan, nothing built. Replay processing and the Discord report stay OFF in production. Every number in this document is a first guess, kept as a property and tuned in the review loop (`docs/replay-review-loop.md`). Builds on the reasons plan (`2026-10-12-replay-reasons-plan.md`, PRs #56 to #66, merged and deployed).

## 1. Why a new plan

The owner reviewed the fights loop: it is too noisy, because every lost fight is explained and most are small. Owner decisions (2026-10-13):

1. Fights: count only the important ones, found from the team gold and experience graphs (spikes and direction changes).
2. Roles: round 3 accepted as it is. All Pick is skipped for now; cleanup comes later.
3. Wards: a table to review, only inside the important-fight windows (mostly before the fight).
4. The rest (runes, laning stage, item efficiency, team behaviour, others) starts from this plan.

## 2. What we do, in order

| # | PR | One line |
|---|---|---|
| 0 | environment (no repo change) | Restore Docker, the formatter jar and the four replays in a stable folder |
| 1 | this plan | Docs only |
| 2 | L1 loop formatter | Shared "easy to read" formatter for every loop report, existing reports moved onto it |
| 3 | E1 extraction fixes | Store the gold and XP reason in the combat log, typed creep kills, check siege names |
| 4 | E2 OpenDota logs stored | Runes, wards, sentry and ward removals, lane role |
| 5 | E3 item data | Item charges and item components |
| 6 | A1 important fights | Pick important fights from the gold and XP graphs; reasons only for them |
| 7 | A2 vision | Ward and vision table inside the important-fight windows |
| 8 | A3 runes and opening | Who takes the runes, who wins the first fight around 0:00 |
| 9 | A4 laning stage | Lane assignment, lane end, creep score, lane result and why |
| 10 | A5 item efficiency | Whitelist file, Magic Stick, Hand of Midas, farming items |
| 11 | A6 team behaviour | Rotations, split push, smoke, objectives (detailed after the first topics are reviewed) |
| 12 | A7 others | End game, `/match` detail, key item behind a swing, outliers, courier, metrics, load test |

Small docs PR in parallel: roles round 3 accepted (recorded in PR #68, owner verdicts in `round-03.md`). Step 0 is done (environment restored 2026-10-13).

## 3. Rules that bind every PR

- Plan first (docs PR), then a worktree per change (`../dbuff-<name>`), one agent per PR, SOLID and DRY, no AI attribution anywhere.
- After every PR opens, including a docs PR: run `code-review`, fix the valid findings (same agent), CI green on the new head commit, and only then ask the owner about the merge. A small PR is not skipped.
- Merge only on the owner's word for that PR. Merging to `main` deploys; replay stays off; no AWS checks while replay is off.
- Each analysis PR brings tests, a review-loop topic and a first round for the owner.
- A new extractor or field costs parse memory (`-Xmx512m`, one replay at a time). Each extraction PR measures the heap before anything depends on it.

## 4. Order and dependencies

| PR | Depends on | Size | Review loop topic |
|---|---|---|---|
| L1 loop formatter | none | S | none (moves `roles` and `fights` onto it) |
| E1 extraction fixes | none | M | none (checked on the fixtures) |
| E2 OpenDota logs stored | none | M | none |
| E3 item data | none | S | none |
| A1 important fights | L1 | M | `fights` (reworked) and `advantage` (new table) |
| A2 vision | A1, E2 | M | `vision` |
| A3 runes and opening | E1, E2 (E1 for the gold and XP balance in the opening window) | M | `runes` |
| A4 laning stage | E1, E2, L1 | L | `lanes` |
| A5 item efficiency | E1, E3 | L | `items` |
| A6 team behaviour | A1, A4 reviewed | L | `behaviour` (outline only) |
| A7 others | by item | M each | by item |

E1, E2 and E3 touch different classes and can run in parallel; L1 can run in parallel with them. An analysis PR waits for the owner's accepted round on the one before it only when it reads that PR's output (A2 reads A1, A6 reads A1 and A4).

## 5. Step 0: environment (done, 2026-10-13)

The scratchpad of the earlier session was cleaned (probably a reboot cleaned `/private/tmp`), which blocked every real-replay run. The environment was restored on 2026-10-13:

- Replays, OpenDota `.json` and `constants-*.json`: `~/dbuff-fixtures/replays` (the four matches). Formatter jar: `~/dbuff-fixtures/tools/gjf/google-java-format-1.25.2-all-deps.jar`. Both are outside git; replays expire around January 2027.
- The old paths are still written in `docs/replay-role-loop/README.md`, `docs/replay-fight-loop/README.md`, `2026-10-12-session-handoff.md`, `2026-10-12-replay-status-and-next-steps.md` and `2026-10-11-replay-extraction-plan.md` (`REPLAY_FIXTURE_DIR` and `/private/tmp` scratchpad paths). `CLAUDE.md` mentions `REPLAY_FIXTURE_DIR` but names no path. Pointing them at `~/dbuff-fixtures` is a docs change for the first PR that touches each file.
- Docker and the stored loop data (`roleloop-pg`) are checked by each PR that needs them.

## 6. What data exists, and what is missing

Checked against the repo on `main` (commit of PR #66).

### 6.1 Exists

| Data | Where |
|---|---|
| Hero track every 5 s from -90 s: x, y, hp %, mana %, mana, attack range, level, flags | `ReplayPlayerTrackDomain` |
| Zones: kind BASE, LANE, RIVER, JUNGLE; lane BOTTOM, MID, TOP; half RADIANT or DIRE; camp number | `MapLayout.zone(x, y)` returning `Zone` |
| Combat log kinds DAMAGE, DEATH, ABILITY, ITEM, GOLD, XP, MODIFIER_ADD, MODIFIER_REMOVE (whitelist), BUILDING_KILL, BUYBACK, with negative times before the horn | `CombatEvent`, `CombatEventKind`, `ReplayCombatLogDomain` |
| Inventory, cooldown, ability level and mana cost changes | `ReplayStateChangeExtractor`, `StateChangeKind` (ITEM_ADDED, ITEM_REMOVED, COOLDOWN, ABILITY_LEVEL, MANA_COST) |
| Per minute and player, from the replay: gold by source (hero kill, creep, neutral, bounty, Roshan, building, courier, comeback, income, shared, ward kill, deny, other, reliable, unreliable), observers and sentries placed, wards destroyed, creeps and camps stacked, bounty, power and water runes taken, gold lost to death, buyback gold | `ReplayPlayerMinuteDomain` (extractor `minutes`) |
| Per minute and player, from OpenDota: gold, xp, last hits, denies, net worth, hero and tower damage | `PlayerTimelineDomain` |
| Per minute team gold and xp advantage (Radiant) | `TeamTimelineDomain` (`radiantGoldAdv`, `radiantXpAdv`) |
| Activity segments: lane farm, jungle farm, teleport, traveling | `ActivitySegmenter` |
| Phases LANE, MID, LATE cut at fixed minutes (properties) | `PhaseWindows` |
| Fights with a losing team, gold lost, reason count | `FightExplanationDomain` (`fight_explanation`), written by `FightLossAnalyzer`; swings and fights inside them by `SpikeAnalyzer` |
| Item purchases (OpenDota purchase log) and an OpenDota based purchase-time efficiency report | `ItemPurchaseEventDomain`, `ItemEfficiencyAnalyzer` with `ItemEfficiencyConfig` |
| An OpenDota based lane report for the old match report | `LaneAnalyzer` (not the replay lane analysis planned here) |

Extractors implement `ReplayExtractor` and analyzers implement `ReplayAnalyzer`; both are discovered as beans, so a new slice needs no change to the run service.

### 6.2 Missing

| Gap | What is true in the code | Cheapest source |
|---|---|---|
| Rune events (who, which, when) | Not stored; only per-minute rune counts exist. OpenDota `runes_log` is in the generated client (`MatchResponsePlayersInnerRunesLogInner`) and read only by the smoke test and the loop report | Store `runes_log`; position from the hero track at the pickup time |
| Ward events (placement, position, time, killer) | Not stored. The client has `obs_log`, `sen_log`, `obs_left_log`, `sen_left_log` and `killed` as untyped lists, and only per-minute counts are stored | Store them (E2); verify on the fixtures before relying on them |
| Gold and XP reason | `CombatLogRecorder` copies `getStunDuration() * 100` into `aux` for every kind. The `CombatEvent` javadoc says "the gold or experience reason, or a stun duration", but nothing writes the reason. For a GOLD or XP entry `aux` is therefore the stun duration, in practice 0, and the reason is lost. Clarity 5.0.1 (the version in `build.gradle`) has `getGoldReason()` and `getXpReason()` on `CombatLogEntry`, verified with `javap` on the cached jar; what values they return for each reason is to verify in E1 | Read the reason for GOLD and XP kinds in `CombatLogRecorder` (E1) |
| Creep kills by type | DEATH rows carry the creep name, nothing groups them. `ActivitySegmenter` counts a lane creep only when the name starts with `npc_dota_creep_` and contains `goodguys` or `badguys`, and a neutral by its own prefix. Siege creep names may not follow that pattern: unchecked, so a possible miss | Typed creep kill record (E1); check siege names on the fixtures first |
| Item charges (Magic Stick) | Not read: the state-change kinds have no charges | Extend the state-change extractor (E3) |
| Item components | `ItemConstant` has only `id`, `dname`, `cost` and `attrib`; recipes are dropped | Add the components field and its parsing (E3) |
| Creep gold and XP values | Nowhere | Measured from the replay's own GOLD and XP events once the reason is stored (E1), kept in one JSON file |
| Lane assignment and matchup | None on the replay side. OpenDota `lane_role` is mapped into the old report (`PlayersMapper`), not into replay tables. The pre-horn phase is stored but not analysed | Hero track and zones; `lane_role` as a cross-check |
| Net worth every 30 s from the replay | Not stored | Only per-minute series exist; a finer series needs a new extractor and is not planned (see A1) |

Two parts of the first draft of this plan were wrong and are fixed here: per-minute gold by source and rune counts do exist (`ReplayPlayerMinuteDomain`), so the gold reason is needed for single events (what a gain came from at a time), not for per-minute totals; and the replay does not store a 30 s net worth series.

## 7. A1: important fights from the gold and XP graphs (the owner's first request)

- **Series:** the per-minute Radiant gold and XP advantage from `TeamTimelineDomain`. (The first draft preferred a 30 s replay net worth; it does not exist, so per minute is the first version.)
- **Measure per fight:** the change of the advantage over the fight window plus a short lag (first guess 60 s, to cover the minute resolution), and three flags: size, a lead flip (the sign of the advantage changes), a slope break (the direction of the graph changes).
- **Important (one rule, everywhere):** a fight is important when the advantage change reaches EITHER 3 percent of the team's total net worth OR 1500 gold. Both numbers are first guesses and properties. The lead flip and the slope break are shown as flags and do not make a fight important by themselves.
- **Which fights:** importance is computed for every fight with a decided winner. A draw or an unknown winner is not important and gets no row. A fight shorter than the series resolution uses a minimum window of one minute plus the lag.
- **Storage:** `importance` and the flags are added to `fight_explanation`; reasons stay stored for all fights. The ranking, the Discord line and the loop use only the important ones.
- **Loop report:** first the advantage graph as a table (per minute gold and XP advantage, with the spikes, direction changes and important fights marked), then the reasons only for the important fights; the other fights are listed with their importance.
- **Home:** a stage of `FightLossAnalyzer` or a new analyzer that it requires; decided in the PR. `SpikeAnalyzer` already finds swings from the same series and is read, not changed.

## 8. A2: wards and vision (after A1)

- **Source:** the OpenDota ward logs stored by E2; the replay entity parse only if they are not enough.
- **Scope (owner decision):** only the important-fight windows, first guess from 90 s before the fight start to 15 s after its end (both are properties). Nothing else is analysed.
- **Per window:**
  - active wards near the fight and their age;
  - a ward placed just before the fight (smaller weight: a ward placed within 30 s before the fight weighs 0.5 of an older ward; both numbers are first guesses);
  - sentries and dust used or available (dust and sentry use from the combat log ITEM events);
  - invisible heroes present (from the hero constants: Riki, Clinkz, Weaver, Nyx Assassin, Templar Assassin, invisibility items) and the other team's detection;
  - enemy wards destroyed.
- **Topic `vision`:** one row per important fight window with those columns and empty verdict columns. Reason codes after acceptance: `NO_VISION`, `INVISIBLE_HERO_NO_DETECTION`, `LATE_WARD`.

## 9. A3: runes and the pre-game fight

- **Goal:** who takes the first runes, who takes the contested ones, and the first fight around 0:00: the bounty runes at the start, kills in a window around the rune spawn (pre-horn kills count; first guess -30 s to +90 s, a property). Rune weights are first guesses: bounty 1.0, power and water 0.5, and which team came out ahead.
- **Data:** `rune_event` (time, type, slot) from OpenDota `runes_log`; the hero position at the pickup from the track; kills from the DEATH rows in the window; gold and XP per team in the window.
- **Tables:** `rune_pickup` (who, which rune, time, zone) and `opening_summary` per match (runes per team, first taker, kills and gold balance, winner of the opening).
- **Topic `runes`:** one row per rune and one per opening.

## 10. A4: the laning stage

Steps, each with its own table in the topic `lanes`:

1. **Lane assignment:** a hero's lane is the lane zone where he spent most time in the first 2 to 4 minutes (first guess, a property; activity segments plus `MapLayout.zone`). Pair the lanes of the two teams (Radiant safe lane against Dire off lane). Roamers and junglers get "no lane". OpenDota `lane_role` is a cross-check.
2. **Laning start:** the hero reaches his lane zone (first guess: 0:00 to 1:00, a property).
3. **Laning end, per hero:** the first time after the start that he is outside his lane for at least 30 s (first guess; jungle, another lane, teleport), or his lane tower falls, or a cap (first guess 9 minutes). The lane ends when all heroes of the pair have left. The 9 minute cap and the 7 to 9 minute typical values are first guesses (properties), so there is no fixed 10 minutes. `PhaseWindows` stays the coarse game phase and is not reused for this.
4. **Creep stats by value:** a typed creep kill per hero (`creep_kill`: time, unit class, own or enemy side, deny flag, lane or camp). A class value is measured from the GOLD and XP events once the reason is stored (siege gives more than melee or ranged; ancient and large camps differ from small ones). The values live in one validated JSON file, not in code. A deny is scored by the experience it takes from the enemy.
5. **Lane result:** compare the pair on weighted creep score, denies, XP, level at lane end, kills and deaths inside the lane window, tower damage and tower state. Per lane: WON, LOST or EVEN with the margin; per hero: his share.
6. **Why** (contributors, the same extension idea as the fight reasons): partner absent or roaming, enemy gank or dive, enemy harassment (hero damage in the lane), a level lead from a rune, a pull or stack, supports present, role and hero matchup (hero constants), item start (Magic Stick, see A5), and how the hero's stats compare with his lane partner.

## 11. A5: item efficiency, a whitelist

- **Whitelist file** `replay/item-efficiency.json` (next to `replay/role-overrides.json`, validated at load the same way): items or groups, with the evaluator type and its parameters. One interface `ItemEfficiencyEvaluator` (`supports(item or group)`, `evaluate(player, match data)`) with one bean per kind. Adding an item is a JSON entry, plus a class only when its logic is new (open for extension).
- **Not the existing config.** `ItemEfficiencyConfig` (prefix `dbuff.report.item-efficiency`) feeds the OpenDota based purchase-time report and stays separate.
- **Bug to fix in this PR:** `application.properties` defines `groups[0]` twice: first `Magic Stick` (item id 34), then `Hand of Midas` (id 65). The second overwrites the first, so the Magic Stick group does not exist; Midas plus Octarine Core is `groups[1]`. The Magic Stick entry has to become its own index.
- **Magic Stick** (its cost is read from the item constants, never written in the plan or in code): charges used, HP and mana restored (needs charges from E3, the use from the ITEM events, hp and mana from the track), and saves: a use right before or during enemy damage after which the hero survived, with the damage taken around the use and his HP as evidence. Efficiency is the value restored plus saves against the cost.
- **Hand of Midas** (cost from the constants): gold and XP gained from the purchase to the end (its uses from the ITEM events, and what the creep gave, which needs the reason from E1), uptime (seconds ready but unused while the hero was farming), payback time (when the cumulative gain passed the cost). Efficiency is gain divided by cost at the end of the game.
- **Farming group** (for example Battle Fury, Maelstrom, Mjollnir; names to confirm in the constants): creep kills, gold per minute and camps per minute before and after the purchase, against the cost and the purchase time.
- **Output:** `item_efficiency` rows (match, slot, item or group, metrics, score), a report line per hero for whitelisted items, topic `items`.

## 12. A6: team behaviour (outline)

Rotations, split push against group, smoke use, objective focus (Roshan, towers after a won fight), the lane swap. Most data exists (activity segments, zones, building kills, Roshan gold in the minute counters); the open points are the definitions and thresholds. To be detailed after A1 and A4 are reviewed.

## 13. A7: other items

One line each, with the data they need: end game (buybacks readiness, throw detection; buyback events exist), the `/match` replay detail endpoint (reads stored tables), the key item behind a swing (item purchases and state changes), outlier explanations, courier (courier gold exists per minute), metrics per status, a 20-replay load test at 512 MB. Order follows the dependencies: typed creep kills before laning and Midas, the gold and XP reason before Midas and creep values, charges before Magic Stick.

## 14. Reports must be easy to read (owner request)

The loop reports the owner compares (advantage graph, important fights, runes, lanes, items, vision) are written for a person:

- Plain words, not codes: the localized hero name, "Mirana was out of position and died to Windrunner at 2:11", not `OUT_OF_POSITION` or `npc_dota_hero_windrunner`.
- Time as m:ss, gold as "1.2k", teams as Radiant and Dire, no internal ids or raw enum names in cells.
- One short table per question, few columns, the verdict or result first, then the numbers behind it, then the empty assistant and owner columns.
- A short "how to read this" header and a one-line summary at the top (counts, the main finding).
- Before and after tables for a round show only what changed.

The loop runner (`ReviewLoopTopic` with `name()`, `matchList()`, `outputDir()`, `sections()` and `render(matchId)`, in the test sources, package `looprunner`) gets one shared formatter for hero names, times, gold and team names, so every topic reads the same. The formatter lives in the same test sources (`looprunner`), next to the topic. L1 moves the existing roles and fights reports onto it, before any new topic is added.

## 15. Questions for the owner

Each with the default the plan assumes.

1. Fight importance threshold: a fight is important when the advantage change reaches either 3 percent of the team net worth or 1500 gold (both properties). Default: as written.
2. Can XP alone make a fight important? Default: no, only together with a gold change.
3. Lag after a fight when measuring the advantage change: 60 s. Default: 60 s.
4. Vision window: 90 s before to 15 s after the fight. Default: as written.
5. Opening window around the rune spawn: -30 s to +90 s. Default: as written.
6. Do power and water runes count the same as bounty runes? Default: no, bounty 1.0, power and water 0.5 (first guesses).
7. How to rate an opening won only by kills? Default: kills count, but a gold and XP balance is shown beside them.
8. Where does a tri-lane count, and how is solo mid treated? Default: a tri-lane is one lane with three heroes, solo mid is a lane of one against one.
9. Which creep classes are weighted first? Default: siege, then ranged, then melee, then camps.
10. Lane end cap: 9 minutes. Default: 9.
11. Item whitelist first entries: Magic Stick, Hand of Midas, one farming group (Battle Fury, Maelstrom, Mjollnir). Default: as written, extended later by JSON.
12. Is a per-minute advantage series enough for A1, or should a 30 s series be extracted later? Default: per minute.
13. Order of A3, A4 and A5 after A2. Default: runes, lanes, items.
14. After the plan is accepted: start Step 0 and E1 to E3 in parallel? Default: yes.

## 16. Verification

- This docs PR: every class, table and file named exists (checked against `main`), numbers are marked as first guesses, open questions are listed.
- Code PRs later: CI green on the PR head, local `:server:test`, the real-replay smoke test and the loop on the two Ability Draft matches (needs Step 0), then the deploy run on `main` green.
