# Replay: A6 team behaviour, detailed plan (2026-10-13)

Status: plan only, no code. Detail of section 12 of `2026-10-13-replay-next-analyses-plan.md`. Every number below is a first guess for the review loop. The questions for the owner are in section 8, each with its default.

## 1. Goal

Say, in plain words, how each team played the middle game: where the heroes were as a group, who rotated and when, whether the team pushed together or split, whether smoke was used and what came of it, and whether a won fight was turned into objectives (towers, Roshan). Only what changed the game counts; a report is not a list of every move.

## 2. Data that exists (no new extraction needed)

- Hero track every 5 s: position, hp, mana, level, alive.
- `MapLayout.zone`: kind BASE, LANE (bottom, mid, top), RIVER or JUNGLE, per half of the map; a camp is an index inside a JUNGLE zone, not a kind.
- `ActivitySegmenter`: lane farm, jungle farm, `TELEPORTING`, traveling.
- Combat log: DEATH, DAMAGE, ABILITY, ITEM (hero users only, e.g. `item_smoke_of_deceit`), BUILDING_KILL, BUYBACK; the modifier whitelist already holds `modifier_smoke_of_deceit` and `modifier_teleporting`.
- Also: `LaneAssignmentDomain` (one lane per hero from the first minutes, with `startSeconds`, `endSeconds`, `endReason`), `LaneResultDomain`, `GameStateSegmentDomain` (team ahead, even, behind), `BuildingKillContextDomain`, `creep_kill` (kills by heroes only).
- Not stored and not invented here: wave position and creep counts per lane, tower health, who stood at Roshan. Where a topic needs them it uses the proxy named in that topic.
- `ObjectiveEventDomain` (OpenDota objectives: Roshan kills, tower and barracks kills, time, team) and the important fights from A1.
- Gate: B2 and B4 read the A4 and A1 outputs and start only after the owner accepted those rounds (merged is not enough); B1 reads tracks and zones only.

## 3. Building blocks (shared by every topic)

1. **Team cluster per time step** (every 5 s): the alive heroes of a team, the largest group within 2500 map units of each other, and its centre and zone. Heroes outside it are "away".
2. **Away trip**: a hero is away from his team's cluster for at least 20 s. A trip has a start and end time, the zone it went to, and what happened there (kill, tower damage, farm).
3. **Time windows come from important fights (A1)** where a topic needs "around a fight"; otherwise from the clock.

One class per building block, one analyzer per topic below, so a topic is replaceable without touching the others (the same `ReplayAnalyzer` extension point as before).

## 4. Topics (one table each in the review loop topic `behaviour`)

### 4.1 Rotation

- A rotation is an away trip that ends in another lane's zone, where it either (a) takes part in a fight, or (b) kills something, or (c) the hero's lane partner got more creep kills in the 60 s after he left than the enemy pair did (proxy: `creep_kill` per lane, since wave position is not stored).
- Per rotation: hero, from where to where, start and end as m:ss, result in words ("Mirana left mid at 12:10, helped win a fight in the top lane at 12:40, 2 kills").
- Verdict: GOOD (a kill or a won fight came out of it), WASTED (no kill and the lane he left lost the creep-kill proxy), DIED (the hero died before the trip ended; the death is shown and the trip is not scored as a success), NEUTRAL.
- Why it matters: an empty lane lost to farm; this joins the lane story from A4 (a lane partner absent).

### 4.2 Group or split

- For each minute after the last lane pair ended (`LaneResultDomain` end of the latest pair; a game with no lane result starts at the cap, 12:00 as a guess): how many of the five heroes are in the biggest cluster, and how many are pushing a lane alone.
- Label per minute: GROUPED (4 or 5 together), SPLIT (2 or more heroes alone in different lanes or far apart), MIXED.
- The report shows the stretches, not every minute ("Radiant split 18:00 to 23:30, lost 2 towers and 1 hero to a catch").
- Link to results: towers taken and heroes lost in each stretch, per team.

### 4.3 Smoke

- Use is read from the ITEM event `item_smoke_of_deceit`; group and duration come from `modifier_smoke_of_deceit` added and removed on each hero. Smoke breaks when a hero comes near an enemy, so the end of the modifier is "expired or broken", not always expiry; a use with no modifier logged is dropped with one INFO line.
- For each use: who was in the smoke group, where it went, how long, and the outcome inside 60 s after it ended: a gank (kill of an enemy hero), a tower, Roshan, nothing, or the group died.
- Verdict: PAID OFF (kill, tower or Roshan), NOTHING, BACKFIRED (lost heroes without a kill; a hero dying inside the smoke window counts here, whatever section 7 says about ending trips). Also "smoke used but no one was caught" counts as NOTHING.
- Note: smoke is a "reason" candidate for the fight reasons too (an enemy came out of smoke); that comes after this table is accepted.

### 4.4 Objectives after a fight

- For each important fight a team won (A1 gives the winner): within 90 s did the winner take a tower, barracks or Roshan, or did the clock pass with nothing? Towers come from `BuildingKillContextDomain`; "undefended" is not known, so "missed" names only the nearest enemy tower still standing by the building kills. Roshan: the killing team from `ObjectiveEventDomain`; who was present is taken from the tracks near the pit at that time; "ahead before" from `GameStateSegmentDomain`.
- Result: "converted" (took an objective), "missed" (nothing, with the nearest undefended tower noted), and the time it took.
- Roshan: who was there, the time of the kill, whether the team was ahead in the fight before it (a Roshan after a won fight is expected; a Roshan while behind is a gamble).

### 4.5 The lane swap

- Compare the stored assignment (`LaneAssignmentDomain`, first minutes) with the lane zone where the hero spent most of minutes 8 to 12 (computed here from tracks and zones, a small new step in B2, no new extraction); a hero whose main lane changed is a swap. A hero in the jungle or in base most of that time has no lane and is not a swap.
- Output only when the team changed the lane pairing; one line each ("Dire moved the off-lane carry to mid at 9:00").

## 5. Output

- Tables (new): `team_rotation`, `team_split_stretch`, `smoke_use`, `objective_conversion`. Each row has match, team, times, hero slots, `analysisVersion`, a short code for the verdict (the code stays in the database; reports show plain words).
- Review-loop topic `behaviour`, readable by the rules of the plan (hero names, m:ss, Radiant and Dire, few columns, verdict last, a how-to-read header and a one-line summary).
- Discord: nothing at first. A line may join the swing line after the owner accepts each table.

Each analyzer gets an `@Order` after `lanes` and `important-fights`, names them in `requires()` (so a failed one makes it unavailable, never a run on missing input) and replaces its rows per match, like the other analyzers. Every threshold below is a property in `ReplayAnalysisProperties` (`replay.analysis.behaviour.*`).

## 6. PR order

Each PR has tests, a review-loop section, and gets the `code-review` run before the merge question.

1. **B1** the shared blocks (cluster, away trip) with their tests, plus topic 4.1 rotation.
2. **B2** group or split (4.2) and the lane swap (4.5).
3. **B3** smoke (4.3).
4. **B4** objectives after a fight (4.4).

B1 can start as soon as the plan is accepted. B2 needs the accepted A4 lanes; B4 needs the accepted A1 important fights.

## 7. Risks and known limits

- Guesses, all tuned in the loop and all properties: cluster distance 2500, away 20 s, the 60 s creep-kill proxy window, GROUPED at 4 or more and SPLIT at 2 or more heroes alone, the 12:00 fallback, lane window minutes 8 to 12, smoke outcome 60 s, objectives window 90 s.
- Teleport arrivals look like an instant rotation; the activity `TELEPORTING` marks them so they are not counted as walking.
- OpenDota objectives are coarse for towers (no tower health); the replay BUILDING_KILL rows are the exact source and are preferred when present.
- A hero who dies or uses buyback during a trip ends the trip; a rotation then gets the verdict DIED (4.1), a smoke the BACKFIRED case (4.3).

## 8. Questions for the owner (default in brackets)

1. Is a rotation only counted when it ends in a kill, a fight or a changed creep balance? [Yes; plain walking between lanes is not reported.]
2. Cluster distance 2500 units and "away" at 20 s: start with these? [Yes, tune in the loop.]
3. Should a hero in the jungle farming count as away? [No, jungle farming is its own state and is not a rotation.]
4. Is the lane swap worth a table, or drop 4.5? [Keep it, it is small.]
5. Smoke: judge the outcome in 60 s after the smoke ends? [Yes.]
6. Objectives: window 90 s after a won important fight? [Yes.]
7. Start order B1 to B4 as above? [Yes.]
