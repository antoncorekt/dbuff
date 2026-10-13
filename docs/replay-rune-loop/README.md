# Runes learning loop

Runes and the opening of the game are shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-next-analyses-plan.md` (section 9, A3). This file is what is specific to the `runes` topic.

## What is stored

`RunePickupAnalyzer` (`runes`, `@Order(14)`; requires `players`, `tracks`, `activity`, `combat-log`) reads OpenDota's rune log (`rune_event`) and writes:

- `rune_pickup`, one row per rune taken (it also keeps `effectEndSeconds`, see below): the player slot and team, the rune type, the game second, the zone and lane the hero's track puts him in at that second (the nearest 5 s sample, classified by the frozen map layout; empty when the layout is not frozen), the bounty gold for a bounty rune and a `midLane` flag. The gold is the taker's own `GOLD` entry with the bounty-rune reason (17) within `replay.analysis.runes.gold-match-seconds` (1 s) of the pickup; a bounty rune pays every hero of the taker's team the same amount. `midLane` is a proxy until the lane analysis exists: true only when the taker stood in the mid lane zone at the pickup (his track; his activity segment when there is no position), or the pickup is before `mid-lane-pickup-seconds` (600) and mid is his main lane of the first `mid-lane-early-seconds` (240 s). It is not true for every pickup of a mid hero. The lane analysis (A4) reads these rows.
- `opening_summary`, one row per team. The "opening" is the window around the first (0:00) bounty-rune spawn, the pre-game fight; it says nothing about the power rune contests of the later spawns, which are read in the `Runes` table and in the fight reasons. The window is `first-spawn-seconds` (0) minus `opening-before-seconds` (30) to plus `opening-after-seconds` (90); inside it the runes taken and the bounty gold the team's heroes received, the team's first taker (slot and second), enemy heroes killed (`DEATH` rows), gold and experience received (all `GOLD` and `XP` entries of the team's heroes, deaths counted as losses), and the balance: the team's gold plus experience less the other team's. The result is `WON` for the higher balance; kills break an equal balance; still equal is `DRAW`. It needs the gold and experience reasons of a version 2 combat log: for a version 1 blob the pickups are stored without gold, the summary is skipped (one INFO line) and the earlier summary stays.

All numbers are first guesses in `RuneProperties`.

## Rune codes

`RuneType` names OpenDota's codes, checked against the `modifier_rune_*` effects that `ModifierWhitelist` now keeps (about 15 to 25 extra events a game, well under 1 percent of a blob): 0 double damage, 1 haste, 2 illusion, 3 invisibility, 4 regeneration, 5 bounty, 6 arcane, 7 water, 8 wisdom, 9 shield. A code not listed is `UNKNOWN`. The power runes (a fight effect) are double damage, haste, invisibility, arcane and shield. The effect durations in `RuneProperties.effect-seconds` follow the modifiers' own durations: 45, 22, 45, 50 and 75 s. The analyzer pairs each power-rune pickup with the `modifier_rune_*` add event on the hero within `modifier-match-seconds` (2 s) and stores `effectEndSeconds`: the removal of that modifier, or the hero's death if sooner. A pickup with no such span (illusion has no modifier, or the blob was stored before the whitelist change) has no end stored and the duration property is used.

## Rune advantage in fights

`RuneAdvantageReasonContributor` (all modes) reads the pickups through `FightContext.runes()`. A power rune whose effect window (`pickup .. effectEndSeconds`, else `pickup + duration`) overlaps the fight, so a hero who died or lost the effect before the fight does not count, taken by a hero who took part in the fight (ACTIVE or PRESENT_PASSIVE), gives `RUNE_ADVANTAGE` (player reason, the taker) and `ENEMY_RUNE_ADVANTAGE` (team reason for the other team), whatever the result. One reason per hero and one team reason per team and fight: a second rune is listed in the same evidence (separated by "; ") and adds `rune-advantage-extra-step` (0.1) to the severity, so none replaces another. The evidence is "double damage rune taken at 9:20 by Windrunner, 40 s from the fight start, active in the fight"; `ReasonWording` says "Windrunner took a double damage rune 40 s before the fight". Severities are in `ReasonProperties.rune-advantage-severity` (double damage 0.5, haste, invisibility and shield 0.4, arcane 0.3). The reasons are stored for every fight; the ranking and the report tell the important ones.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name runeloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5544:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:reviewLoop -Ploop.topic=runes
docker rm -f runeloop-pg
```

The match list is `server/src/test/resources/replay/rune-loop/matches.txt` (9038725154 and 9037038717); reports go to `server/build/rune-loop/round-NN/`.

## Output

One `<matchId>.md` per match and `summary.md` with the diff against the previous round, in the layout of `docs/replay-review-loop.md`: a "how to read this" line and a one-line summary, then three tables compared between rounds:

- `Opening` (key: the team): the result first (won, lost or drew the opening), who took the first rune, runes taken, bounty gold (the window and the whole game), kills, gold, XP and the balance.
- `Runes` (key: the sentence "Windrunner took the double damage rune at 9:20"): team, bounty gold, where the hero stood, mid lane or not, and for a power rune whether an important fight overlapped its effect window and which.
- `Rune advantage in fights` (key: the reason sentence): the fight, who won, whether it is important, whether the other side's list says it, severity.

The `assistant` and `owner` columns are empty, last.

## Reviewing

Copy `round-template.md` to `round-NN.md` and fill a row per rune or opening. Look first at: the mid lane flag (is the taker really the mid laner), runes taken at 0:00 (who was first), the power runes marked as in an important fight (did the taker fight), and an opening decided by a tiny balance.
