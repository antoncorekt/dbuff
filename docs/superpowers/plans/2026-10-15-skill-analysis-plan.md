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
- A nightly job (not at report time; the host has 2 GiB) fills `skill_stat`: per skill, per hero, per (hero, skill), per pair of skills held by the same player. Rows hold picks, wins and a shrunk win rate (Wilson lower bound, so a skill seen 5 times does not top the table), plus a sample size.
- Window: the last 12 months of Ability Draft (mode 18), a property. A pair counts only above a minimum sample (default 30 games). Lift of a pair = observed win rate against what the two skills predict alone (log-odds sum), so the table shows real synergy, not two strong skills added together.
- The pairs asked for: hero with each of its four skills; skill 1 with skills 2 to 4; skill 2 with skills 3 and 4; skill 3 with skill 4 (the slots as the hero holds them after the draft, so any order the player took them in).
- Check first: how many matches in the production database have ability data and how far back it goes. If the 12 months are thin, the report says "not enough games" instead of guessing.
- Output per focus player: his hero, each skill and each pair with games, win rate and lift in words ("Storm Bolt with Thunder Clap: 58% in 140 games, 6 points above what each gives alone").

## Part B: does the build fit the role
- Add one function to the profiles: `ESCAPE` (blink, invisibility, self-teleport, long dash, untargetable), set through `role-overrides.json` per skill, plus rules from the constants. Reuse the other functions as they are.
- A table `role-needs.json` (validated at start-up like the overrides) says per role what a good build holds, as minimum function values: caster: disable, damage, escape; carry: damage, farm; mid: damage plus a mid-game spike (a skill that works at level 6 to 12); support: save and heal, disable; tank: durability, initiation; every role: escape is a bonus, and for a hero fighting alone it is a need.
- The role comes from the stored FINAL role (what he played), and the intended role from the INTENT profile; the evaluator reports gaps by plain words: "Pudge had no escape; he died alone 4 times".
- Evidence tie: a missing function is only stated as a problem when the match backs it (no escape and deaths alone; no farm skill and low net worth). Weights are first guesses, calibrated with the review loop (topic `skills`): we compare the flagged builds with their win rate in the 12-month data and drop flags that do not predict anything.

## Part C: could a better skill have been taken first
For each skill the focus player took at turn T:
1. Candidates: skills the enemy took later (turn above T), so they were still in the pool at T (the pool and picks are stored).
2. Strength in this match: damage dealt per minute of the owner's game and in won fights (`ability` and `fight_ability_use`), compared with the same slot kind (regular or ultimate) and with the skill's own 12-month average, so a hero with a great carry behind it does not look like a great skill.
3. Flag when the candidate did more than 10% more damage than the player's skill (property), decided at least one important fight for the enemy, and was available at T. Skills with no damage (stun, heal) are compared on fights they were cast in and won, a second pass after the damage one.
4. Words: "The enemy took Chain Frost at pick 31; it did 38% more damage than your Dragon Blood and was in 3 of their 4 won fights. It was still free when you took Dragon Blood at pick 12."
This needs the replay data (turns), so it works only for matches parsed with the new parser version.

## Delivery
- Section `skill-picks` (Ability Draft only, order next to the draft board, off by default, on demand with `~report skill-picks`): per focus player the three most useful lines from A, B and C. Facts feed the AI top 5 reasons.
- Stats are also a REST read for checking (`/api/v1/skills/...`, API key).

## PR order (each with tests, code-review, a first round for the owner)
1. S1: `skill_stat` tables, the nightly job and its measure on the real DB (rows, time, heap).
2. S2: `ESCAPE`, `role-needs.json`, the build evaluator, loop topic `skills` (round 1 for the owner).
3. S3: the missed-pick analyzer on stored data, loop round.
4. S4: the section, wording, AI facts, docs.

## Questions for the owner (defaults in brackets)
1. Window 12 months and minimum 30 games per pair (yes).
2. Ability Draft only for the statistics (yes; All Pick has no skill choice).
3. "Mid should do something in the mid game": measure it by a skill that matters from level 6 to 12 (a spike by ability cooldown and level), or leave mid to damage plus disable (leave it first, add after the first round).
4. The 10% margin for a missed better pick, by damage per minute (yes).
5. Should the stats also be a command (`/skills hero:`) or report-only (report-only first).
