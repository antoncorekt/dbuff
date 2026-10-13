# Team behaviour learning loop

How the teams moved as a group, and who rotated, is shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-team-behaviour-plan.md` (A6). This file is what is specific to the `behaviour` topic, as far as PR B1 (rotations) goes; group or split, smoke and objectives come with B2 to B4.

## What is stored

`BehaviourRotationAnalyzer` (`rotations`, `@Order(90)`; requires `players`, `tracks`, `activity`, `combat-log`, `creep-kills`, `deaths`, `buybacks`, `fights`, `lanes`) writes `team_rotation`, one row per rotation, replaced per match. All numbers are first guesses in `BehaviourProperties` (`replay.analysis.behaviour.*`).

## Rules

**Team cluster** (`TeamClusters`). Every 5 s, from the alive heroes of a team: the biggest set in which every two heroes are within `cluster-distance` (2500) units. Ties go to the tighter set, then the lower slots. Fewer than `min-cluster-size` (2) heroes is no cluster, and then nobody is away. It has a centre and the zone of the centre.

**Away trip** (`AwayTrips`). A hero who is alive, outside the cluster and not farming the jungle (activity `JUNGLE_FARM`) is away. A trip starts at the first such sample and ends when the hero joins the cluster, starts to farm the jungle, dies (the exact time of the combat-log death, an Aegis revival excluded) or buys back, the cluster disappears, or the track ends. A trip shorter than `away-seconds` (20) is dropped. The trip keeps where he left, where he got (the zone where he joined the cluster, else the last zone he was seen away in), the cluster's zone when he left, and whether a teleport (`TELEPORTING` segment) ended inside it.

**Rotation** (`RotationRules`). A trip that gets to a lane zone that is neither his own lane (the stored lane assignment, any lane for a hero with none) nor the lane he left. It is stored only when something came of it: a fight he took part in (presence active or close and passive) in the trip or `outcome-linger-seconds` (15) after it, a kill or an assist then, his death, or the lane he left losing the creep-kill proxy. Plain walking between lanes is not stored.

**Creep-kill proxy** (`LaneProxy`). Wave position is not stored, so for a hero who was laning when he left, the creeps (not denies) his lane partners killed in that lane in the `proxy-window-seconds` (60) after he left, against the creeps the enemy heroes of that lane killed. The lane is lost when the enemy killed more. No proxy for a hero with no lane or one whose laning had ended.

**Verdict** (`RotationVerdict`). DIED (he died before the trip ended; a buyback ends the trip too), else GOOD (a kill or assist, or a won fight), else WASTED (the lane he left lost the proxy), else NEUTRAL.

## What the data cannot tell

- A kill is any enemy hero death the hero killed or assisted in; it is not necessarily the target of the rotation.
- After the laning stage the proxy is mostly absent, so WASTED is a laning-stage verdict; later a rotation is GOOD, DIED or NEUTRAL.
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

One `<matchId>.md` per match and `summary.md` with the diff against the previous round: a "how to read this" line and a one-line summary (counts per verdict), then one table, `Rotations` (key: "Radiant Mirana at 12:10"): the trip in words (where he left, where he got, when, how long, whether teleported, how it ended), what came of it (fights and wins with the time and place of the key fight, kills or assists, creeps), the lane he left with the proxy counts, the verdict, and an empty `owner verdict` column last.

## Reviewing

Copy `round-template.md` to `round-NN.md`. Look first at: trips that are not rotations (a hero who stayed while the group came), rows whose verdict does not match what the hero did, a missing rotation the owner remembers, and a WASTED whose lane did not really lose.
