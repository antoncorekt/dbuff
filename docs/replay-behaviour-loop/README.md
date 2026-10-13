# Team behaviour learning loop

How the teams moved as a group, and who rotated, is shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-team-behaviour-plan.md` (A6). This file is what is specific to the `behaviour` topic, as far as PRs B1 and B1b (rotations, mid-game verdict and reasons) go; group or split, smoke and objectives come with B2 to B4.

## What is stored

`BehaviourRotationAnalyzer` (`rotations`, `@Order(90)`, version 2; requires `players`, `tracks`, `activity`, `combat-log`, `creep-kills`, `deaths`, `buybacks`, `fights`, `lanes`, `building-kills`) writes `team_rotation`, one row per rotation with its reasons and income numbers, replaced per match. All numbers are first guesses in `BehaviourProperties` (`replay.analysis.behaviour.*`).

## Rules

**Team cluster** (`TeamClusters`). Every 5 s, from the alive heroes of a team: the biggest set in which every two heroes are within `cluster-distance` (2500) units. Ties go to the tighter set, then the lower slots. Fewer than `min-cluster-size` (2) heroes is no cluster, and then nobody is away. It has a centre and the zone of the centre.

**Away trip** (`AwayTrips`). A hero who is alive, outside the cluster and not farming the jungle (activity `JUNGLE_FARM`) is away. A trip starts at the first such sample and ends when the hero joins the cluster, starts to farm the jungle, dies (the exact time of the combat-log death, an Aegis revival excluded) or buys back, the cluster disappears, or the track ends. A trip shorter than `away-seconds` (20) is dropped. The trip keeps where he left, where he got (the zone where he joined the cluster, else the last zone he was seen away in), the cluster's zone when he left, and whether a teleport (`TELEPORTING` segment) ended inside it.

**Rotation** (`RotationRules`). A trip that gets to a lane zone that is neither his own lane (the stored lane assignment, any lane for a hero with none) nor the lane he left. It is stored only when something came of it: a fight he took part in (presence active or close and passive) in the trip or `outcome-linger-seconds` (15) after it, a kill or an assist then, his death, or the lane he left losing the creep-kill proxy. Plain walking between lanes is not stored.

**Creep-kill proxy** (`LaneProxy`). Wave position is not stored, so for a hero who was laning when he left, the creeps (not denies) his lane partners killed in that lane in the `proxy-window-seconds` (60) after he left, against the creeps the enemy heroes of that lane killed. The lane is lost when the enemy killed more. No proxy for a hero with no lane or one whose laning had ended.

**Income** (`IncomeProxy`). The hero's gold and experience per minute over the trip plus `income-tail-seconds` (60) after it (no tail after a death), against the stretch of the same length just before the trip start, read off the per-minute totals (`player_timeline`) with a straight line between minutes. No reading when the stretch before would start before 0:00, when the totals do not reach the end of the tail, or when a total is missing. His farm dropped when the gold per minute fell to `income-share` (0.7) of the one before, or lower.

**Reasons** (`RotationReasons`, one rule each, all that apply are stored in `team_rotation.reasons`): DIED; DID_NOTHING (no hit on an enemy hero or a tower, no kill, no creeps from arrival to the end of the trip and the linger); INCOME_DROP; ARRIVED_AFTER_FIGHT_ENDED (a fight in the lane he got to ended after he left and before he arrived, and he took part in none there); LEFT_LANE_UNDEFENDED (the creep-kill proxy was lost); TOWER_LOST_WHILE_AWAY (an own tower, from `building_kill_context`, fell during the trip).

**Verdict** (`RotationVerdict`). DIED (he died before the trip ended; a buyback ends the trip too), else GOOD (a kill or assist, or a won fight), else WASTED (the lane he left was lost, or DID_NOTHING together with INCOME_DROP, or a tower fell while he was away), else NEUTRAL. A trip is stored when he died, fought, got a kill, or it is WASTED.

## What the data cannot tell

- A kill is any enemy hero death the hero killed or assisted in; it is not necessarily the target of the rotation.
- The creep-kill proxy exists in the laning stage only; later WASTED comes from the income drop or a lost tower. The income reads gold from OpenDota's totals and is also moved by kills, buybacks and item purchases of the heroes of the group, so it is a hint, not a proof.
- "Did nothing" counts hits, kills and creeps only: a hero who stood in a won fight without hitting is listed as having done nothing there.
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

One `<matchId>.md` per match and `summary.md` with the diff against the previous round: a "how to read this" line and a one-line summary (counts per verdict and per reason), then one table, `Rotations` (key: "Radiant Mirana at 12:10"): the trip in words (where he left, where he got, when, how long, whether teleported, how it ended), what came of it (fights and wins with the time and place of the key fight, kills or assists, creeps), the lane he left with the proxy counts, the verdict, why (every reason in words, with the farm numbers as 1.2k gold per minute), and an empty `owner verdict` column last.

## Reviewing

Copy `round-template.md` to `round-NN.md`. Look first at: trips that are not rotations (a hero who stayed while the group came), rows whose verdict does not match what the hero did, a missing rotation the owner remembers, and a WASTED whose lane did not really lose.
