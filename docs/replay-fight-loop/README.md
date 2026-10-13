# Fights learning loop

The reasons behind a lost fight are shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-12-replay-reasons-plan.md` (sections 3.3, 4, 5). This file is what is specific to the `fights` topic.

## What is stored and how it is ranked

`FightLossAnalyzer` (`@Order(70)`, after the fight reasons and the spikes) writes one `fight_explanation` per fight with a decided winner (not a draw, not unknown), seen from the loser: the swing whose `FIGHT` factors list the fight (when two swings list it, the one the winner benefited from), the gold the losing team's deaths in the fight cost it (OpenDota's `death_event.goldLost`, matched to the replay's deaths by hero and time within `replay.analysis.fight-loss.death-match-seconds`; empty when no death has a figure; an OpenDota death pays for at most one replay death, the nearest first, and the rest are counted as unmatched) and how many reasons `fight_reason` holds for the losing team. A lost fight with no reason is stored with a count of zero, so a gap in the rules is visible.

Nothing is capped in storage. The order of the reasons is computed on read (`ReasonRanking`, no stored rank, so a rule change needs no re-analysis): by severity, which weighs role and presence only, never hero gold; team reasons rank below player reasons, unless no player reason reaches `replay.analysis.fight-loss.min-player-severity`, in which case everything ranks by severity alone. Three rules shape it: (1) the absence reasons of one hero in one fight (`NO_TP`, `TP_ON_COOLDOWN`, `HAD_TP_NOT_USED`, `CARRY_FARMING_ELSEWHERE`, `SUPPORT_ABSENT`) are one entry, the strongest being the reason and the others its `also` codes, and ranks count entries; (2) draft-level reasons (`INTENT_NOT_PLAYED`, `WEAK_COMBINATION`, `STRONG_SKILL_LEFT`) rank after every other reason whatever their severity; (3) the cooldown reasons (`ABILITY_ON_COOLDOWN`, `ITEM_ON_COOLDOWN`) are not ranked in a fight that starts before `replay.analysis.fight-loss.early-fight-seconds` (300); they stay stored and the loop report shows them as "not ranked (early fight)". Whether a player reason leads is decided on the ranked entries only, so an unranked early cooldown never counts.

`FightExplanationReader` is the read model: per match the swings with the fights their losing team lost (costliest first), and the lost fights that belong to no swing, each with its ranked reasons. `read(matchId, topReasons, maxFightsPerSwing)` takes the limits as parameters; the report applies three and two, the loop passes none.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name fightloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5544:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:reviewLoop -Ploop.topic=fights
docker rm -f fightloop-pg
```

The reason sentences are the production `ReasonWording`; the formatter that writes every value is `LoopWording` in the loop runner package. The match list is `server/src/test/resources/replay/fight-loop/matches.txt` (the two Ability Draft matches 9038725154 and 9037038717); reports go to `server/build/fight-loop/round-NN/`. Round numbers are per topic.

## Output

One `<matchId>.md` per match, and `summary.md` with the diff against the previous round:

Every report follows the layout of `docs/replay-review-loop.md` ("Making a report easy to read"): after the title a "how to read this" line and a one-line summary (the lost fights per side, how many have no reason, the costliest fight), then the tables. Words, not codes: heroes by name ("Windrunner", or "Dire player 2" when unknown), `m:ss`, gold as `1.2k`, Radiant and Dire.

- `Lost fights` (compared between rounds; the first column is the key, for example "Dire lost fight 5 at 23:03"): main reason (the first ranked reason as a sentence, "and N more" when there are others, "no reason found" when none), gold lost (with the deaths that no OpenDota figure was matched to, when there are any), the swing the fight belongs to ("none", or marked when the side the swing favoured lost this fight) and the OpenDota fact to compare with: the teamfight that overlaps the fight most (OpenDota needs three deaths, so small fights show `none`), its deaths and the gold delta of the losing team.
- `Reasons` (compared between rounds; key: reason and fight): the reason as a sentence ("Windrunner had no teleport"), the fight, rank (or "not ranked (early fight)"), also (the other absence reasons the entry stands for, as phrases), severity, and empty `assistant` and `owner` columns for the verdicts.
- `Notes behind the reasons`: for each reason the stored code and evidence, for tracing it. This is the only place a stored code appears.
- `Swings`: each swing's window, the side that gained, its gold and cause in words, and its lost fights.

## Reviewing

Copy `round-template.md` to `round-NN.md` and fill one row per reason; `assistant` and `owner` are `OK` or `REJECT`, and a reject names the expected reason or order. Look first at: the top reason of each fight (is it what a player would say lost it), reasons that fire on every fight (too cheap), team reasons that outrank nothing, and lost fights with zero reasons.
