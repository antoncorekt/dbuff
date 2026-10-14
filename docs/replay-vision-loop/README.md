# Vision learning loop

What each team could see around the important fights. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-next-analyses-plan.md` (section 8, A2). This file is what is specific to the `vision` topic. The vision rows are not yet part of the report or of `fight_reason`; they are added after the owner accepts this table.

## What is stored

`VisionAnalyzer` (`@Order(80)`; requires `important-fights`, `fights`, `combat-log`, `state-changes`, `players`, `tracks`) stores `fight_vision`: for every important fight (`FightExplanationReader.importantFights`) and each of the two teams one row. Nothing outside the windows of the important fights is read. A match with no ward log (OpenDota's `ward_event`) gets no rows.

The window (`replay.analysis.vision.*`, all first guesses) is from 90 s before the fight's start to 15 s after its end. For a team:

- **Wards covering the fight:** the team's observers (radius 1600 world units) and sentries (900) that were alive when the fight began and are within their radius plus `ward-position-tolerance` (620, the measured ward position error) of any of the team's heroes in the fight, at their track position at the fight's start (the fight's centre when no track is known). A ward with no logged removal is taken to live its full time (observer 360 s, sentry 420 s). The age of the youngest and oldest covering observer and the distance in units from the nearest observer alive, covering or not, to the nearest hero (`nearestWardDistance`, for calibrating the radius) are stored. `lost` says whether the team lost the fight.
- **Late ward:** an observer near the fight placed 30 s or less before it. It weighs 0.5, an older one 1 (the weight is stored; the verdict uses counts). The row stores the weight and how many seconds before the fight the latest late one was placed.
- **Wards destroyed:** enemy wards whose removal the log calls killed, inside the window and anywhere on the map, with the heroes the log names as the killers.
- **Invisible enemy heroes:** heroes of the other team that took part in the fight (active or close) and were invisible during it: inside a span of the game's `modifier_invisible` (it is on the modifier whitelist), or used an ability or item of `replay/invisible-heroes.json` from 15 s before the fight to its end, or own a passive invisibility ability (Riki's, if the patch has it). An ability counts for any hero that owns it, so Ability Draft is covered; the hero key of the file only says whom it belongs to.
- **Detection:** the team has a sentry near the fight, or used dust from 15 s before the fight to its end, or placed a sentry near the fight in that time, or a hero in the fight held dust, a gem or a sentry ward when it began. "Used" is dust used or a sentry placed. Oracle and other rare detection are not read.

The verdict codes, in this order, are all that apply, or `FINE`: `NO_VISION` (no observer covers the fight), `INVISIBLE_HERO_NO_DETECTION` (an enemy hero was invisible and the team had no detection), `LATE_WARD` (no full-weight observer covers the fight but at least one late one does, whatever their number).

Ward cells are converted by `WardCoordinates`: `world = (cell - 128) * 128`, fitted on the four fixture matches against the placing hero's track (134 placements: a least-squares line gives 126.4 to 126.8 units per cell and a zero at cell 128.1 to 128.4); the median distance to the hero is about 620 units and 90 percent are within 920, which is the cast range plus the 5 s track step, not the error of the ward position.

## What the data cannot tell

- Towers, creeps, heroes, Roshan's pit and the fog of the lane are not vision here: a fight at a tower may read as "no vision".
- A ward the log does not remove (removal unknown) may in fact have been killed; the report counts them per match.
- Invisibility the combat log does not show is missed; the modifier is read for any hero in the fight, even for an instant.
- Smoke of Deceit is not treated as invisibility.

## Known limits

Recorded hypotheses, not handled in this version:

- A ward placed in the first seconds of the fight counts as not alive at its start.
- Ward events come from OpenDota's match import, not from an extractor, so the analyzer cannot declare them as a requirement; a match without them gets no rows.
- Invisibility is taken from the modifier span or the use of an ability or item; its real duration, and invisibility cast on an ally (for example Lycan's wolves), are not modelled.
- Dust or a sentry held in the stash or by a hero not in the fight is not counted; dust held by a hero in the fight is counted even when he could not have used it.
- Towers, creeps and nearby heroes are not counted as vision (owner decision), so "no vision" means no ward; the Discord `vision` section (off by default) says the same.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name vision-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5566:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays SMOKE_PG_URL=jdbc:postgresql://localhost:5566/dbuff ./gradlew :server:reviewLoop -Ploop.topic=vision
docker rm -f vision-pg
```

The match list is `server/src/test/resources/replay/vision-loop/matches.txt`; reports go to `server/build/vision-loop/round-NN/`. The fixture seeding stores OpenDota's ward logs with the match, so a match stored before that must be stored again (delete its `replay_parse_run` row).

## Output

One `<matchId>.md` per match and `summary.md` with the diff against the previous round. After the how-to-read header and the one-line summary (the important fights, the team rows and how many carry each verdict) come:

- `Vision per important fight` (compared between rounds; key: the time window and the team and fight): the window, the team and the fight with its start, who lost the fight, the wards near the fight (observers with the youngest one's age, sentries), whether an observer was placed just before the fight and how many seconds, the invisible enemy heroes by name, the detection (not available, available and used, available and not used), the enemy wards the team destroyed with the killers, the verdict in words, and the empty `assistant` and `owner` columns.
- `What the data cannot tell`: the match's ward counts (those with no logged removal and those still within their life at the end), the conversion error and what is not counted.

## Reviewing

Copy `round-template.md` to `round-NN.md` and fill one row per team row that is wrong. Look first at: "no vision near the fight" in a fight at a tower or in the jungle, heroes called invisible that were not, detection the team could not have used, and late wards that were in fact old.
