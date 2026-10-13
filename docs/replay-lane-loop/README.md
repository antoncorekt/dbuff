# Lanes learning loop

The laning stage (who won each lane, and why) is shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-next-analyses-plan.md` (section 10, A4). This file is what is specific to the `lanes` topic.

## What is stored

`ReplayLaneAnalyzer` (`lanes`, `@Order(47)`; requires `players`, `tracks`, `activity`, `combat-log`, `creep-kills`, `runes`, `roles`, `deaths`, `building-kills`) writes four tables. It is not the OpenDota based `LaneAnalyzer` of the old match report. All numbers are first guesses in `LaneProperties` (`replay.analysis.lanes.*`).

- `lane_assignment`, one row per player: his lane (BOTTOM, MID or TOP; empty for a hero with no lane, a roamer or a jungler), the pair id (one per lane, shared by the heroes of both teams in it), SAFE, MID or OFF from his own team's side (Radiant's safe lane is the bottom one, Dire's the top one, so Radiant safe is Dire's offlane), his laning window and why it ended.
- `lane_player_stat`, one row per hero with a lane: last hits (melee including flagbearers, ranged, siege, other: mega and unknown names; the four add up), denies, the value-weighted creep score, his share of his side's score, the experience and gold gained in the window (OpenDota's per minute series, read by a straight line between the minutes), his level at the end and `ultimate_seconds` (the first track sample at level `ultimate-level` (6) by the end of his window; empty when he had not reached it or the track does not say), kills and deaths inside the window, hero damage dealt and taken (combat log, inside the window), tower damage and camps stacked.
- `lane_result`, one row per side of each lane: the pair's window and end reason, the side's own end, the sums of the hero stats, the average level, whether a tower of the lane fell (the side's or the other side's), the result (WON, LOST, EVEN, or UNCONTESTED when the other team had no hero in the lane) and the margin.
- `lane_reason`, the reasons of each side, ranked (1 is the strongest), each HELPED or HURT the side, with a hero when it is about one, a severity and an evidence text.

## Rules

**Lane of a hero.** The lane zone (`MapLayout.zone`, from the activity segments) where he spent most of his time alive between `assign-from-seconds` (0) and `assign-to-seconds` (240), provided it is at least `min-lane-share` (0.4) of that time and more than his time farming the jungle. The heroes of both teams in the same geographic lane are one lane pair; a tri-lane is a pair with three heroes on one side, a solo mid is one against one.

**Lane of a hero who shadowed a teammate** (`LaneShadowAssigner`, analysis version 2). A hero with no lane by the rule above (a support who started in another lane, or walked with the carry outside the zone) is looked at once more, lane by lane of his own team's heroes who have one. Over the laning window of that lane's holders (first arrival to last departure), the seconds he was alive and either in the lane zone or within `shadow-distance` (1500 units) of a teammate who has the lane (and is inside his own window) must be at least `shadow-min-share` (0.5) of his time alive in that window, at least `shadow-min-seconds` (60) (time farming the jungle never counts, so a jungler next to the carry stays without a lane); the lane with most such seconds is his, a tie going to the first in lane order (bottom, mid, top). The holders are the lane heroes whose windows survived the late-arrival drop and the clip to the contest, and a hero who already has a lane by the first rule (even without a window) is never shadow-assigned. Then his laning window is found as for any lane hero and cut at the end of his holders' windows, so he must also stand in the lane zone at some time. A hero who roamed or jungled fails the share and stays without a lane. The properties are checked when the application starts (`LaneProperties.validate`).

**Laning window of a hero** (`LaningWindows`). It starts when he first stands in his lane (never before 0:00). It ends at the first of:

- he left: a stretch outside his lane that holds `away-seconds` (30) of farming the jungle or another lane, or holds `return-seconds` (60) in all. Time dead and the first `respawn-grace-seconds` (45) after a respawn (the walk back from the fountain) do not count (a teleport or a trip to the base he did not come back from, or standing in the river). The end is when the stretch began, so a fountain trip with a return inside 60 s, and passing through another lane, are not an end;
- the first tower of tier `tower-tier` (1) of that lane falls, whoever's it is (it ends the laning of the whole lane);
- `cap-seconds` (900), only a safety limit;
- the data of the hero ends;
- every hero of the other side has left (`OPPONENTS_LEFT`, property `end-when-opponents-left`).

A hero whose window has no time (he arrived after the tower fell or after the other side left) is not a lane member: he has a `lane_assignment` row with no lane and no stat or result rows, and no reason is built for him.

The lane pair's window is from the first arrival to the last departure; one hero leaving does not end the other's laning. The partner's absence is a reason, not an end.

**Creep score** (`CreepTally`). Per hero, inside his own window, the lane creeps he killed: a last hit is worth the gold (plus a flagbearer's bonus) plus `xp-weight` (0.5) times the experience of its class in `creep-values.json`, so siege (gold 64) outweighs melee (38) by its own values. A deny is worth `deny-xp-weight` (0.5) times the experience it takes plus `deny-gold-weight` (0.25) times the gold. Camp creeps do not count.

**Result** (`LaneVerdict`). For a side against the other side, five terms, each scaled to -1 .. 1: the creep score share (weight 0.35), the experience gained share (0.25), the average level lead over `level-full-lead` (2) levels (0.15), the kill lead over `kill-full-lead` (3) (0.15) and the deny lead over `deny-full-lead` (6) (0.10), plus or minus `tower-bonus` (0.25) when a tower of the lane fell (the other side's: plus; its own: minus). A margin below `even-band` (0.10) is EVEN. Every term compares the averages per hero of each side, so two heroes against one are not favoured by their number; the report shows the totals.

## Reasons

`LaneReasonContributor` beans (common to all modes) see one side of one lane and return reasons about that side. Equal code and hero: the more severe one is kept; below `min-severity` (0.1) nothing is kept; at most `reasons-per-side` (6) are stored.

| code | what it says |
|---|---|
| `PARTNER_ABSENT` | a partner of the side arrived late or left early, at least `partner-gap-seconds` (60) in all |
| `PARTNER_ROAMING` | a lone hero's support had no lane (roaming or jungling) |
| `ENEMY_GANK` | enemy heroes from other lanes, or with no lane, killed a hero of the side or did at least `gank-damage` (500) hero damage to it while it laned |
| `HARASSMENT` | hero damage taken from the lane's opponents against dealt to them differs by at least `harassment-damage` (300); damage of other enemies is `ENEMY_GANK`, never both |
| `MID_RUNES` | mid lane only: power, water and wisdom runes taken by the heroes of each side while laning (bounty is gold and is not counted) |
| `SUPPORT_PRESENCE` | one side had a support (role 4 or 5) in the lane and the other none |
| `STACKED_CAMPS` | a hero stacked camps while laning (OpenDota's per minute count) |
| `MATCHUP` | ranged heroes, nukers and disablers (hero constants) on one side more than the other; weak, low severity |
| `SIEGE_EDGE`, `DENY_EDGE` | siege last hits or denies differ by `siege-edge` (1) or `deny-edge` (3) |
| `NO_ULTIMATE_AT_LANE_END` | a hero of the side had not reached level 6 when his laning ended while every hero of the other side had (HURT; the reverse HELPED); severity `no-ultimate-severity` (0.25). A hero without his ultimate may farm efficiently and still not be "in the lane", so read the result with it |
| `EARLY_DEATH`, `EARLY_KILL` | a hero of the lane died, or killed a hero of the other side, before `early-kill-seconds` (300); one row per hero, the evidence counts them |

A new reason is a new bean; the analyzer does not change.

## What the data cannot tell

- Assists: the death rows carry only a count, so assists inside the window are not stored.
- A jungle pull is not a stored event; a stack is (the per minute count), so `STACKED_CAMPS` is the only trace of the support's camp work.
- Experience and gold in the window come from OpenDota's per minute series by a straight line; without the match detail import they are empty and the experience term counts as no difference.
- Lane heroes are told apart from heroes in a lane only by the 4 minutes of the assignment span: a hero who switches lane after minute 4 keeps his first lane, and his time in the other lane ends his laning.

- The shadow rule judges by the window of the lane his team holds, not by the first 4 minutes: a hero who laned with one carry for 2 minutes and then walked to another lane gets the lane where he spent most of that lane's window, and a hero with a lane of his own is never moved. His position is taken from the 5 second track samples, so a short visit is not seen. Two teammates in different lanes both far from him in a fight-heavy opening can leave him without a lane. The distance is measured to the teammate's position at the same second, in a straight line, with no allowance for cliffs or the river.
- The shadow window of a lane runs from the first holder's start to the last holder's end, gaps included (a teammate counts only inside his own window). Every sample of every candidate is checked against every holder of each lane, which is cheap for ten heroes. A broken or missing track (no step, no coordinates) makes the hero or the partner unseen, never an error. Other `LaneProperties` are not validated.
- Analyzers that read lanes (rotations, lane swaps, splits) take the new lanes only when run again: `POST /api/v1/replay/{matchId}/reanalyze` runs every analyzer again.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name laneloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5545:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:reviewLoop -Ploop.topic=lanes
docker rm -f laneloop-pg
```

The match list is `server/src/test/resources/replay/lane-loop/matches.txt` (9038725154 and 9037038717); reports go to `server/build/lane-loop/round-NN/`.

## Output

One `<matchId>.md` per match and `summary.md` with the diff against the previous round: a "how to read this" line and a one-line summary, then three tables compared between rounds:

- `Lanes` (key: the lane sentence "Radiant safe lane: Axe and Lich vs Dire off lane: Mirana and Doom"): who won and how clearly, the laning window with the end reason in words, creep score and denies per side, XP and level, key facts (including one phrase per side on the ultimate: "Radiant: both heroes had the ultimate", "Dire's Mirana did not reach level 6").
- `Players in the lane` (key: the hero): lane and window, last hits by class, denies, creep score and share of his side, XP and level, kills and deaths; the XP and level cell says when he reached level 6 ("reached level 6 at 7:40, before the lane ended") or that he did not ("did not reach level 6 when the lane ended (level 5)"). A hero with no lane is listed as such.
- `Why` (key: the reason sentence): the ranked reasons of each side, in plain words, with the severity.

The `assistant` and `owner` columns are empty, last.

## Reviewing

Copy `round-template.md` to `round-NN.md`. Look first at: the lane of each hero (is a roamer given a lane, is a lane hero given none), the end of each window (does the reason in words match what the hero did), the winner of a lane that was close, and the first reason of each side (is it the real cause).
