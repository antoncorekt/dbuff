# Team behaviour learning loop

How the teams moved as a group, and who rotated, is shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-team-behaviour-plan.md` (A6). This file is what is specific to the `behaviour` topic, as far as PRs B1, B1b, B2 and B3 (rotations, mid-game verdict and reasons, group or split, lane swap, smoke) go; objectives come with B4.

## What is stored

`BehaviourRotationAnalyzer` (`rotations`, `@Order(90)`, version 2; requires `players`, `tracks`, `activity`, `combat-log`, `creep-kills`, `deaths`, `buybacks`, `fights`, `lanes`, `building-kills`) writes `team_rotation`, one row per rotation with its reasons and income numbers, replaced per match. All numbers are first guesses in `BehaviourProperties` (`replay.analysis.behaviour.*`).

`BehaviourSplitAnalyzer` (`team-splits`, `@Order(91)`, version 1; requires `players`, `tracks`, `activity`, `deaths`, `lanes`, `building-kills`) writes `team_split_stretch`, and `BehaviourLaneSwapAnalyzer` (`lane-swaps`, `@Order(92)`, version 1; requires `players`, `tracks`, `lanes`) writes `team_lane_swap`; both replaced per match.

`BehaviourSmokeAnalyzer` (`smoke`, `@Order(93)`, version 1; requires `players`, `tracks`, `combat-log`, `deaths`, `building-kills`) writes `smoke_use`, replaced per match. Thresholds: `smoke-group-seconds` (3), `smoke-expiry-slack-seconds` (1.0), `smoke-outcome-seconds` (20); the application refuses to start with a negative group window or slack, or an outcome window below 1.

## Rules

**Team cluster** (`TeamClusters`). Every 5 s, from the alive heroes of a team: the biggest set in which every two heroes are within `cluster-distance` (2500) units. Ties go to the tighter set, then the lower slots. Fewer than `min-cluster-size` (2) heroes is no cluster, and then nobody is away. It has a centre and the zone of the centre.

**Away trip** (`AwayTrips`). A hero who is alive, outside the cluster and not farming the jungle (activity `JUNGLE_FARM`) is away. A trip starts at the first such sample and ends when the hero joins the cluster, starts to farm the jungle, dies (the exact time of the combat-log death, an Aegis revival excluded) or buys back, the cluster disappears, or the track ends. A trip shorter than `away-seconds` (20) is dropped. The trip keeps where he left, where he got (the zone where he joined the cluster, else the last zone he was seen away in), the cluster's zone when he left, and whether a teleport (`TELEPORTING` segment) ended inside it.

**Rotation** (`RotationRules`). A trip that gets to a lane zone that is neither his own lane (the stored lane assignment, any lane for a hero with none) nor the lane he left. It is stored only when something came of it: a fight he took part in (presence active or close and passive) in the trip or `outcome-linger-seconds` (15) after it, a kill or an assist then, his death, or the lane he left losing the creep-kill proxy. Plain walking between lanes is not stored.

**Creep-kill proxy** (`LaneProxy`). Wave position is not stored, so for a hero who was laning when he left, the creeps (not denies) his lane partners killed in that lane in the `proxy-window-seconds` (60) after he left, against the creeps the enemy heroes of that lane killed. The lane is lost when the enemy killed more. No proxy for a hero with no lane or one whose laning had ended.

**Income** (`IncomeProxy`). The hero's gold and experience per minute over the trip plus `income-tail-seconds` (60) after it (no tail after a rejoin, a death or a buyback, the same rule as the outcome linger), against the stretch of the same length just before the trip start, read off the per-minute totals (`player_timeline`) with a straight line between minutes. No reading when the stretch before would start before 0:00, when the totals do not reach the end of the tail, or when a total is missing. His farm dropped when the gold per minute fell to `income-share` (0.7) of the one before, or lower.

**Reasons** (`RotationReasons`, one rule each, all that apply are stored in `team_rotation.reasons`): DIED; DID_NOTHING (no fight taken part in, no hit on an enemy hero or tower, no kill, no creeps from arrival to the end of the trip and the linger); INCOME_DROP; ARRIVED_AFTER_FIGHT_ENDED (a fight in the lane he got to ended after he left and before he arrived, and he took part in none there); LEFT_LANE_UNDEFENDED (the creep-kill proxy was lost); TOWER_LOST_WHILE_AWAY (an own tower, from `building_kill_context`, fell during the trip; it decides the verdict only when it is in the lane he left).

**Verdict** (`RotationVerdict`). DIED (he died before the trip ended; a buyback ends the trip too), else GOOD (a kill or assist, or a won fight), else WASTED (the lane he left was lost, or DID_NOTHING together with INCOME_DROP, or a tower fell while he was away), else NEUTRAL. A trip is stored when he died, fought, got a kill, or it is WASTED.

**Group or split** (`PostureClassifier`, `PostureMinutes`, `SplitStretches`). From the end of the latest lane pair (`lane_result.end_seconds`, else `split-fallback-start-seconds`, 720), rounded up to a whole minute, every 5 s sample of a team is labelled from its living heroes (dead ones are not counted; a sample where the team has fewer than `posture-min-living-heroes` (4) living heroes gets no label, so two or three survivors are never GROUPED or SPLIT): GROUPED when the biggest cluster holds `grouped-min-heroes` (4) heroes, or all of them when fewer are alive; else SPLIT when at least `split-min-alone-heroes` (2) heroes are alone (a group of three and two lone heroes is SPLIT, a group of three and a pair is MIXED) (outside the cluster, not farming the jungle, no living teammate within `cluster-distance`, so two alone heroes are always far apart, in different lanes or not); else MIXED. A minute takes its most frequent label (a tie is MIXED). Neighbouring equal minutes join; a stretch shorter than `min-stretch-minutes` (2) is absorbed by its longer neighbour (the one before on a tie), shortest first; a minute with fewer than two living heroes breaks the run. Each stretch stores towers the team lost and enemy towers it took (`building_kill_context`, a deny is not taken), its own heroes that died and the enemy heroes that died (`own_heroes_died`, `enemy_heroes_died`, from the death rows, so any cause counts: a kill, a tower, neutrals, a denied hero).

**Lane swap** (`LaneSwaps`). The main lane of a hero with a stored lane is the lane zone he stood in for most of his living samples in minutes `swap-from-minute` (8) to `swap-to-minute` (12), needing at least `swap-min-share` (0.5) of them and no tie; a different lane than the stored one is a swap, from the first to the last time he was seen in it in the window. A hero mostly in the jungle, river or base has no main lane and is not a swap. A hero without a stored lane is not examined.

**Smoke** (`SmokeEvents`, `SmokeFinder`, `SmokeOutcomes`). A use is an ITEM event `item_smoke_of_deceit` (the log records it for hero users only). Its group is the heroes of the user's team that got a `modifier_smoke_of_deceit` (MODIFIER_ADD, whose attacker is the user) within `smoke-group-seconds` of the use; the user must be among them, else the use is dropped with one INFO line per match. Two uses with the same group and start within that window (a stack used twice) are one row; two different groups of a team stay separate. A hero's modifier ends at its MODIFIER_REMOVE, or at the next ADD for the same hero; it was broken when it lasted shorter than its full duration (the duration of the ADD event, 45 s in the stored matches) minus `smoke-expiry-slack-seconds`. The smoke ends for the group at the first broken removal (end reason `BROKEN`), else at the last removal (`EXPIRED`); a modifier with no logged removal or no positive duration is `UNKNOWN`, and without a broken one the whole smoke is `UNKNOWN`. Start and end zone are the zone of the middle of the group's living heroes at the start and the end (a hero who spawns at the use is read one track sample later, one who died at the end one sample earlier). Only what the group did counts, from the use to `smoke-outcome-seconds` after the end: PAID_OFF when a group member killed or assisted in the death of an enemy hero (each death is one kill, by victim and time), a group member was among the killer and the nearby attackers (`building_kill_context`) of an enemy tower, barracks or ancient (not a deny), or a group member struck the last blow on Roshan (a DEATH of `npc_dota_roshan`); else BACKFIRED when a group hero died (an Aegis revival is not a death), whatever enemies died to others; else NOTHING. The table stores the counts too (`kills`, `buildings`, `roshan_kills`, `group_deaths`, `enemy_deaths`, the last not used for the outcome) and the group as comma separated slots.

## What the data cannot tell

- Group or split is geometric. With 4 of 5 heroes needed within 2500 units of each other most minutes after the lane stage come out MIXED; the thresholds are first guesses to tune. MIXED also covers a pair and a trio, which is a normal way to push.

- "What happened" counts everything inside the stretch, not only what the posture caused; the death counts include any cause.
- The lane swap does not use the stored lane window: a hero whose lane window ended before minute 8 is exactly the swap case, so the rule is a sustained share (50%) of his living time in minutes 8 to 12 in another lane.
- Finding the activity segment of a sample is a linear search (samples x heroes x segments); small in absolute terms, not optimised.
- `grouped-min-heroes` must be at least `min-cluster-size`, `posture-min-living-heroes` 2 to 5 and `split-min-alone-heroes` 2 to 5; the application refuses to start otherwise.
- The lane swap needs a stored lane; a hero the lane analysis gave no lane (a support who stood in a lane next to his carry, for example) is never listed, and his carry then shows no lane partner.

- Smoke: whether a modifier ended by breaking or by running out is read from how long it lasted, not from a log field; a hero who died with the modifier on counts as broken. A hero who got the smoke after the group window (he walked up to the user) is not in the group.
- Smoke: Roshan and buildings are credited to the group by the hero who struck the blow and by the heroes near the building (`attacker_slots`, within 1500 units in the ten seconds before). An illusion, a summon or a creep that struck the Roshan blow is not a hero and is not counted. A group member who was too far from the building is not an attacker.
- Smoke: the group is stored as comma separated slots in `smoke_use.group_slots`, not as a related table; changing that format needs an `analysisVersion` bump.
- A kill is any enemy hero death the hero killed or assisted in; it is not necessarily the target of the rotation.
- The creep-kill proxy exists in the laning stage only; later WASTED comes from the income drop or a lost tower. The income reads gold from OpenDota's totals and is also moved by kills, buybacks and item purchases of the heroes of the group, so it is a hint, not a proof.
- "Did nothing" means no fight taken part in (active or present passive), no hit on an enemy hero or tower, no kill and no creeps at the destination.
- ARRIVED_AFTER_FIGHT_ENDED looks at the lane and the time of the fight only, not at which team was in it.
- Experience is stored and shown but does not decide INCOME_DROP; only gold does.
- The reasons are stored as one comma-separated string in `team_rotation.reasons`.
- A tower lost makes a trip WASTED only when it is in the lane he left; a tower of another lane is listed as a reason and does not decide. No income is read when the stretch before the trip overlaps another trip of his or any time he was dead.
- The group is a geometric group, not the team's intent: a hero who walks to the group's lane is a rotation even if the group moved there first.
- The cluster is of living heroes only, so a team with one hero alive has nobody away.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name behaviourloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5545:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:reviewLoop -Ploop.topic=behaviour
docker rm -f behaviourloop-pg
```

The match list is `server/src/test/resources/replay/behaviour-loop/matches.txt` (9038725154 and 9037038717); reports go to `server/build/behaviour-loop/round-NN/`.

## Output

One `<matchId>.md` per match and `summary.md` with the diff against the previous round: a "how to read this" line and a one-line summary (counts per verdict and per reason), then four tables. `Rotations` (key: "Radiant Mirana at 12:10"): the trip in words (where he left, where he got, when, how long, whether teleported, how it ended), what came of it (fights and wins with the time and place of the key fight, kills or assists, creeps), the lane he left with the proxy counts, the verdict, why (every reason in words, with the farm numbers as 1.2k gold per minute), and an empty `owner verdict` column last. `Group or split` (key: "Radiant 18:00 to 23:30"): how the team stood in words (together, split up, mixed) and what happened (towers lost and taken, heroes that died on each side). `Lane swaps` (key: "Dire Mirana at 9:00"): one sentence with the hero, his role when known, the lane before and after and the clock. `Smoke` (key: "Radiant Crystal Maiden at 17:13"): the group by hero name, from and to as m:ss with the seconds and how the smoke ended (broke or ran out), where the group went (zone of the start and the end), the outcome in words (paid off, came to nothing, backfired) and what happened (enemy heroes killed, towers or barracks fallen, Roshan, group deaths). Each table has its own how-to-read line and an empty `owner verdict` column last.

## Reviewing

Copy `round-template.md` to `round-NN.md`. Look first at: trips that are not rotations (a hero who stayed while the group came), rows whose verdict does not match what the hero did, a missing rotation the owner remembers, and a WASTED whose lane did not really lose.
