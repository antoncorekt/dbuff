# Role learning loop

The role and team-composition rules are shaped by review rounds on a small fixed list of matches, not tuned against a labelled data set. The design is section 5 of `docs/superpowers/plans/2026-10-12-replay-ability-draft-roles-design.md`; this file is how to run a round.

## What a round does

`:server:roleLoop` takes every match of `server/src/test/resources/replay/role-loop/matches.txt` (match ids, one per line, `#` comments allowed) and, against a local throwaway PostgreSQL:

- a match whose replay run is already stored is re-analysed from the stored data, the same path as `POST /api/v1/replay/{matchId}/reanalyze` (no replay needed, a few seconds per match);
- otherwise, when `REPLAY_FIXTURE_DIR` has `<id>.dem.zst` and `<id>.json` (OpenDota's match JSON), the whole pipeline runs on the replay once, with a local HTTP server standing in for Valve, and the match is stored;
- otherwise the match is reported as skipped, with the reason.

It then reads the stored tables only (`player_role`, `player_role_profile`, `team_composition`, the draft picks, OpenDota's statistics) and writes the reports. The task never changes the analysis; a rule change is made in the code, and the task is run again.

## Commands

```
docker run -d --name roleloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5544:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:roleLoop
docker rm -f roleloop-pg    # when the loop is done; the stored matches go with it
```

Keep the container for the whole loop: it holds the stored matches, so a later round needs no replay. Replays vanish from Valve after about 3 months and are never committed; put the `.dem.zst` and `.json` of a new match in the fixture directory once, when it is added to the list.

| Variable | Meaning |
|---|---|
| `REPLAY_FIXTURE_DIR` | Directory with `<id>.dem.zst` and `<id>.json`; the task is skipped when it is not set |
| `SMOKE_PG_URL` | JDBC URL of the database, default `jdbc:postgresql://localhost:5544/dbuff` (user `postgres`, password `password`); only localhost is accepted |
| `ROUND` | The round number to write; default the one after the last in `server/build/role-loop` |

Replay processing and the Discord report stay off in the application; the task never touches production.

## Output

`server/build/role-loop/round-NN/` has one `<matchId>.md` per match and a `summary.md`:

- header: match, mode, date, score, winner;
- the `Roles` table (slot, hero, intent role, final role) and the `Verdicts` table (team, stage PICK or FINAL, tokens), which are what the diff compares;
- per player: hero, drafted skills, the intent and final stage (role, confidence, `fitRole`, lane, `roleScores`, the nine functions with `unknown` where nothing tells them, evidence), and OpenDota's `position_est`, `lane_role` and net worth rank for comparison;
- per team: the PICK and FINAL verdict, tokens and summary.

A match of a mode with no resolver says "No roles stored (no resolver for this mode)". An All Pick match has only FINAL rows and says there is no intent and no PICK verdict, instead of printing blanks.

`summary.md` and the console list, for every match, what changed against the same match in the latest earlier round: roles and verdict tokens that differ, rows added or removed. A regression on an earlier accepted row shows up here.

## Recording a round

Copy `round-template.md` to `docs/replay-role-loop/round-NN.md` (same NN as the reports) and commit it. One row per player, `match | slot | hero | intent | final | expected | assistant | owner | comment`, and a second table for the team verdicts. `assistant` and `owner` are `OK` or `REJECT`; `expected` is filled for a reject.

1. Run the task. The assistant reviews the reports and fills the `assistant` column.
2. The assistant changes weights, rules or tags to fix the rejects, notes the change in the round file, and runs again (the next round number).
3. The owner reviews the new reports and fills the `owner` column.
4. Change, assistant review, owner review again, until the owner accepts. A change must keep earlier accepted rows accepted.
5. Matches are added to the list as they come; the last accepted round is the baseline for the next.

## Accepted

A round is accepted when the owner has marked it so, because for every match on the list the final role of every player and the team verdicts are `OK` or an explained tolerated difference, and the intent rows read plausibly. The owner decides. A suggested bar: no rejected position 1 or 5, and at most one adjacent-role reject per match.

Ability Draft goes first. When it is accepted, the same task and tables run on All Pick matches, and the All Pick resolver is adjusted the same way.
