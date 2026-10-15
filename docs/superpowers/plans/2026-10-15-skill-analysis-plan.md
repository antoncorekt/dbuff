# Skill and hero analysis for Ability Draft: plan

Status: plan for review, nothing built. Written 2026-10-15 from the owner's request.

## Goal
For the focus group's drafts, say in plain words:
1. how strong each chosen skill and hero are, and how strong each pair is, from the last 12 months of Ability Draft games;
2. whether the build fits the role (a caster needs stun, magic damage and an escape; a carry needs damage and farm; a support needs heal and stun; a tank needs survival; every role can use an escape);
3. where a better skill was available: the enemy took a skill later that did clearly more damage and won fights, and our player could have taken it first.

## What research says (guides, 2026)
- Early picks rarely complete a combo (you do not know the teammates or the enemies yet); later picks have information and can finish a combo or take a key enemy spell. Deny-picking a strong enemy spell is often right.
- Always valuable: mobility (blink, charge), area stuns, team amplifiers, cooldown reset.
- Some spells need a partner (a damage ultimate that needs its setup spell); drafting them alone leaves the hero weak.
- The community has tier lists but no win-rate data; the numbers have to come from our own matches.
Sources: bo3.gg Ability Draft guide (May 2026), DOTAFire and dota2.ru guides (older).

## What the repo already has
- `ability` (match, slot, ability: damage dealt and received, use count) and `ability_upgrade` (skill order) from OpenDota for each parsed match; `AbilityRankingService` and `AbilityRankingRepository` already compute per skill and combo statistics for `/stats`.
- `ability_draft_pick` and `hero_draft_pick` (replay only): pick order, turn 1 to 50, the 48-skill pool and who took what, `poolRemainingBeforePick`.
- Role and intent machinery: `player_role`, `player_role_profile` with nine functions (initiation, disable, save and heal, damage, durability, farm, jungle, roaming, vision), `IntentProfileCalculator`, `role-overrides.json`.
- Per fight ability use (`fight_ability_use`: damage per skill per fight), `fight_explanation` (won or lost), `death_analysis` (killed alone or not), important fights.

## Part A: statistics (12 months)
- No nightly job and no statistics tables. A late analyzer (after the parse has released its memory, so it never holds the parse permit) runs the counts for the match's own skills at analysis time: the hero, each skill, hero with each skill, and every pair of skills the player held. About 15 counts, each one SQL aggregate over `ability` and the match results that returns only games and wins (no rows in memory), reusing the combo queries of `AbilityRankingRepository`. The window is the 12 months before the match date, and the match itself is left out. The result is stored per match (`skill_pick_stat`: subject, games, wins) so a later `/match report` shows the same numbers and runs no query. The raw win rate is shown; the Wilson lower bound only ranks.
- Window: the last 12 months of Ability Draft (mode 18), a property, and nothing else: there is no minimum number of games for a skill, a hero or a pair. Every figure carries its game count, a small count is worded as such ("only 3 games"), and the ranking uses the Wilson lower bound so a tiny sample never sorts above a solid one. Lift of a pair = the pair's raw win rate against the expected rate from the two skills alone (average of their win rates in games where they were NOT held together, so the pair's own games do not feed the baseline), shown with its game count and an interval.
- The pairs asked for: hero with each of its four skills; skill 1 with skills 2 to 4; skill 2 with skills 3 and 4; skill 3 with skill 4 (the slots as the hero holds them after the draft, so any order the player took them in).
- First task of S1, before any code depends on it: count the Ability Draft matches with ability data in the production database per month, and time the pair query on a copy of it (EXPLAIN, seconds, heap). Our database holds the games of tracked players, not all of Dota, so many pairs will have a handful of games; with no minimum they are still shown, with their count. The row count tells how much weight the owner should give them. If the pair query takes more than about 2 seconds, add stored weekly counts then, not before.
- Output per focus player: his hero, each skill and each pair with games, win rate and lift in words ("Storm Bolt with Thunder Clap: 58% in 14 games, 6 points above what each gives alone").

## Part B: does the build fit the role
- Add one function to the profiles: `ESCAPE` (blink, invisibility, self-teleport, long dash, untargetable), set through `role-overrides.json` per skill, plus rules from the constants. Reuse the other functions as they are.
- A table `role-needs.json` (validated at start-up like the overrides) says per role what a good build holds, as minimum function values: caster: disable, damage, escape; carry: damage, farm; mid: damage plus disable (what mid did in the game is Part D, not a skill rule); support: save and heal, disable; tank: durability, initiation; every role: escape is a bonus, and for a hero fighting alone it is a need.
- Both roles are judged: the INTENT role (what the draft was built for) and the FINAL role (what he played). When they match one verdict is shown; when they differ, both are shown and the words say so ("drafted as a support, played carry"). "Fighting alone" is the stored death reason (`death_analysis`: no ally within range at death), not a new field. The report words gaps plainly: "Pudge had no escape; he died alone 4 times".
- Evidence tie: a missing function is only stated as a problem when the match backs it (no escape and deaths alone; no farm skill and low net worth). Weights are first guesses, calibrated with the review loop (topic `skills`). To avoid circular proof, flags are tuned on the older months and checked on the newest 3 months; a flag that only tracks the result (it appears in most lost games whatever the build) is dropped.

## Part C: could a better skill have been taken first
For each skill the focus player took at turn T:
1. Candidates: skills the enemy took later (turn above T), so they were still in the pool at T (the pool and the picks are stored; `poolRemainingBeforePick` cross-checks it). The draft's own rules also apply: a regular skill cannot fill the ultimate slot and the reverse, and a player holds at most 3 regular skills and 1 ultimate, so a candidate counts only if it could have replaced the skill we took in that slot kind.
2. Strength in this match: the skill's total damage dealt over the whole match (`ability`), the owner's rule. The 12-month average of the skill is printed beside it as context (a skill far above its own average was probably carried by a fed hero), but it is not a gate.
3. Flag when the candidate did more than 10% more total damage than the player's skill (property), decided at least one important fight for the enemy, and was available at T. Skills with no damage (stun, heal) are compared on fights they were cast in and won, a second pass after the damage one.
4. Words: "The enemy took Chain Frost at pick 31; it did 38% more damage than your Dragon Blood and was in 3 of their 4 won fights. It was still free when you took Dragon Blood at pick 12."
This needs the replay data (turns). A focus match without `ability_draft_pick` rows gets no Part C lines at all and the section says in one line that pick order is not known for it; Parts A and B still work.

## Part D: what the mid hero did (a possible win reason)
The owner's point: mid is the role that gets XP and gold fastest, so it should do something after the laning stage. Not a skill rule; a reason read from the match, for the FINAL role 2 hero of each team:
- Level 6: the minute he reached it (track level) and what he did next: stayed in the mid lane, moved to another lane or the jungle, or joined a fight. Rotation rows (`team_rotation`) and activity segments say where he went; kills in the 3 minutes after leaving mid come from the combat log.
- Mid-game = the time after the lane stage (the lane pair's end time already stored by the lane analysis, else minute 12). In it: kills and assists, fights joined against the team median, rotations that came to something, and his gold and XP per minute against the enemy mid and against his own team's average.
- Verdicts in plain words: ACTIVE ("Puck reached level 6 at 7:10, went to the top lane and made 2 kills; his gold per minute was 18% above the enemy mid") or PASSIVE ("Lina stayed in the mid lane and jungle after level 6, joined 2 of 9 fights, and her experience per minute fell below the enemy mid"). Both can explain a win or a loss, so each is stated with the team result and the swing it fell in.
- Stored per match as `mid_activity` (one row per mid hero, with the numbers behind the verdict) by a late analyzer that reads stored tables only; it works for All Pick too, since it needs no draft data. It feeds the section and the AI reasons.

## Delivery
- Section `skill-picks` (Ability Draft only, order next to the draft board, off by default, on demand with `~report skill-picks`): per focus player the three most useful lines from A, B and C. The mid line of Part D is its own small section `mid-activity` (all modes).
- AI top 5 reasons take the pick analytics as input: `SectionFacts` gets the two sections, so the facts gain `PICK_n` (a skill or pair that was strong or weak, with games), `BUILD_n` (a role gap with its evidence), `MISSED_n` (a better skill left to the enemy) and `MID_n` (the mid verdict), with their `FactKind` weights set so they are the first dropped when the token budget bites, after the fights and swings. The prompt lists them like the other facts and the parser still accepts only known ids, so the model cannot cite a pick fact that was not given.
- Command `/skills` (text alias `~skills`), built on the same queries and stored rows as the report: `/skills hero:<hero>` shows the hero's 12-month win rate and its four skills with games; `/skills skill:<skill>` shows the skill alone, with the hero it is best on, and `/skills skill:<a> with:<b>` shows a pair with its lift. Validate, acknowledge, then work; hero and skill autocomplete from the constants; ability window and numbers as in Part A. Registered through the `CommandRegistry` like the other commands.
- Stats are also a REST read for checking (`/api/v1/skills/...`, API key), built in S1.

## PR order (each with tests, code-review, a first round for the owner)
1. S1: the row count and query timing on a copy of the real DB, then the analyzer, `skill_pick_stat` and the REST read.
2. S2: `ESCAPE`, `role-needs.json`, the build evaluator, loop topic `skills` (round 1 for the owner).
3. S3: the missed-pick analyzer on stored data, loop round.
4. S4: `mid_activity` analyzer and section, loop round.
5. S5: the `skill-picks` section, wording, and the AI facts (`PICK_n`, `BUILD_n`, `MISSED_n`, `MID_n`).
6. S6: the `/skills` command.

## Decisions taken (owner, 2026-10-15)
1. Window 12 months; no minimum games for any figure.
2. Ability Draft only for the skill statistics.
3. Mid activity is a win reason (Part D), not a skill-spike rule.
4. Missed-pick strength is total damage in the match, 10% margin.
5. A `/skills` command is built, and the pick analytics feed the AI top 5 reasons.
