# Round 03

- Date: 2026-10-12
- Commit or PR the reports came from: branch `feature/replay-roles-final-data` (round 2 was `feature/replay-role-round-02`)
- Matches: 9038725154 and 9037038717 (Ability Draft). The two All Pick matches were stored and re-analysed again; their roles did not change, and they are not reviewed until Ability Draft is accepted.
- Changes since the previous round (weights, rules, tags; one line each):
  - Final stage input: the FINAL profile (`ObservedProfiler`) now reads OpenDota's per-player statistics already stored in `player_match_statistic_domain`, as shares of the team total, each divided by a full share and cut at 1 (`observed.*`): hero damage to `damage` (`damage-full-share` 0.35), hero healing to `saveHeal` (`heal-full-share` 0.5), damage taken from enemy heroes to `durability` (`damage-taken-full-share` 0.35), observer and sentry wards placed to `visionDuty` (`ward-full-share` 0.5), and the share of last hits is blended into `farmPriority` (`last-hit-full-share` 0.35; `last-hit-farm-share` 0.3 of the value, the rest stays the net worth place). Tower damage is stored but not used. A stat missing for any player of the team is unknown for the whole team (an incomplete total would inflate the shares of the others); a team total of 0 gives everyone 0. With no net worth place, farm priority is the last-hit value alone. The properties are `replay.analysis.role-profile.observed.damage-full-share`, `heal-full-share`, `damage-taken-full-share`, `ward-full-share`, `last-hit-full-share` (each must be above 0) and `last-hit-farm-share`. `initiation` and `disable` stay unknown.
  - `fit.final-lane-factor` 0.5 to 0.1: the lane label is now a tie-break in the final stage. The weights per role are unchanged, so the new values enter through the existing damage, heal, vision, durability and farm weights.
  - Intent stage: unchanged, checked by the loop diff against round 2 (intent roles and `intent fit` columns identical for all 20 players; the new inputs are read by the FINAL profiler only). The intent remarks in the Players table below are carried over from round 2 and describe its rows, not changes of this round; the intent rejects (Sniper, Furion, Windrunner, Tusk, Enchantress, Sven, Ancient Apparition) are the round-2 ones, not new regressions. Round 2 names no concrete, safe change to the attack-skill rule (only that it is "probably too generous"), so it was left as it is.
  - All Pick: roles unchanged (its resolver does not read the profile). Its stored FINAL profile rows now carry the new values, so their `fitRole` and `roleScores` differ from round 2.
  - Analyzer version: `roles` 3 to 4.
- Status: accepted by the owner as it is (owner, 2026-10-13)

`assistant` and `owner` are `OK` or `REJECT`. `expected` is filled for a reject: the role (1 to 5) the reviewer says the player filled (final) or was meant to fill (intent, named in the comment). `intent` is `n/a` for a mode without intent rows (All Pick). Read the roles from `server/build/role-loop/round-03/<matchId>.md`. The owner column holds the owner verdict of 2026-10-13: round 3 is accepted as it is; `OK` where the assistant said `OK`, the other rows stay as recorded.

## Known hypotheses to review

- The full shares (0.35 damage, 0.5 healing and wards, 0.35 damage taken and last hits) and the 0.3 last-hit share of farm priority are guesses; none is fitted.
- Damage taken feeds durability, which role 3 weights 2: a hero that is hit a lot because he stands in front reads durable, but a squishy hero that is focused reads the same.
- Wards placed as vision duty and healing done as save and heal read the whole game, with no split by phase.
- With `final-lane-factor` 0.1 the lane label hardly moves a score: where the data cannot tell roles apart (no initiation, no disable), net worth place, damage and wards decide.
- Unchanged from round 2: initiation counted per skill independently of the control branch, a damage passive counted as an attack skill, saturation of the additive attack terms, the generic gap-closer keys.

## Reading of the round

- **Final roles now differ from OpenDota's `position_est` for 4 of 20 players** (round 2: 0 of 20), all in 9037038717 and two swaps: Brewmaster 3 to 1 with Void Spirit 1 to 3, and Doom 5 to 4 with Largo 4 to 5. The other 16 final roles equal round 2.
- **Brewmaster**: now 1. He has 30.6k net worth (the next in his team is 17.9k), 364 last hits and the most hero damage of the team (24.9k); his best fit is 1(3.22). At lane factor 0.5 his scores were already 1(2.82) over 3(1.96) with the new data, but Void Spirit's lane bonus kept the 1 away from him; at 0.1 the assignment gives it to Brewmaster. At 0 the other rows move as well (Sniper, Venomancer, Jakiro, Terrorblade, Mirana), so 0.1 is the lowest value that changes only these two pairs.
- **Void Spirit** paid for it: 1 to 3 (fits 1(2.36) 3(1.91) 2(1.39)). Round 2 accepted him as 1 and expected Brewmaster as 1; one role per team means both cannot hold. Void Spirit (16.4k, 195 last hits, safe lane) needs the reviewer: 2 or 3 is adjacent, and no initiation or durability data says which.
- **Venomancer**: not fixed. Final 4 as before; his best fit is now 2(1.81), then 1(1.64) and 3(1.39), and 4 is the leftover of the assignment (confidence 0.07). Furion fits 1(2.00) 3(1.94) and holds 3, and nothing in the data separates 3 from 4.
- **Doom and Largo**: swapped. Largo (6.3k, 16 wards, 1357 healing) as 5 reads right by the data; Doom (6.9k, 445 healing) as 4 is adjacent. Both were accepted in round 2 with the other role; flagged as changed rows.
- **Confidence** is low in the final stage (0.02 to 0.78): the margins are small now that the lane label no longer separates roles, and three functions stay unknown.
- **Verdicts**: `CARRIES_3` no longer fires on Dire of 9038725154 (FINAL) and on Radiant of 9040892342 (All Pick FINAL), because the farm priority now includes the last-hit share. No other verdict changed.
- **Not solved**: initiation, disable and the front-line reading are the data the role 3 and role 4 split needs; damage, healing and wards separate roles 1, 2 and 5 from the rest better than 3 from 4.

## Players

| match | slot | hero | intent | final | expected | assistant | owner | comment |
|---|---|---|---|---|---|---|---|---|
| 9038725154 | 0 | Sniper | 2 | 3 | 3 | REJECT | | intent was 3 and accepted; Oracle's False Promise now gives save 0.90 and farm is 1.00, best fits 2(1.46) 3(1.32) 1(1.28): regression, expected 3 (1 tolerated). Final OK |
| 9038725154 | 1 | Enchantress | 5 | 2 | 2 | REJECT | | intent still 5: best fits 3(0.76) 2(0.69) 1(0.45), so 5 is the leftover of the assignment; damage 0.33 for Impetus, Sunder, Immaterial, Shrapnel is low. Final OK |
| 9038725154 | 2 | Riki | 4 | 4 | | OK | OK | |
| 9038725154 | 3 | Death Prophet | 3 | 5 | | OK | OK | intent fixed (was 2); final 5 OK |
| 9038725154 | 4 | Phantom Assassin | 1 | 1 | | OK | OK | |
| 9038725154 | 128 | Sven | 3 | 5 | 4 | REJECT | | intent 3, was 1; Storm Bolt, Assassinate, Enchant, Reflection is a utility kit with a nuke (damage 1.00, farm 0.80); adjacent to the expected 4. Final 5 OK |
| 9038725154 | 129 | Venomancer | 4 | 4 | 3 | REJECT | | final unchanged and not fixed: fits 2(1.81) 1(1.64) 3(1.39), assigned 4 as the leftover (confidence 0.07); Furion holds 3 and no data separates 3 from 4 |
| 9038725154 | 130 | Furion | 5 | 3 | 3 | REJECT | | intent was 3 and accepted; Nature's Attendants is now a heal (save 0.67), so he reads 5 with best fits 3(0.74) 5(0.68): regression, expected 3. Final 3 OK |
| 9038725154 | 131 | Terrorblade | 2 | 1 | | OK | OK | |
| 9038725154 | 132 | Jakiro | 1 | 2 | | OK | OK | intent was the leftover 5, now 1 with damage 1.00; expected 2, 1 tolerated. Final 2 OK |
| 9037038717 | 0 | Crystal Maiden | 5 | 5 | | OK | OK | intent confidence 0.89 now |
| 9037038717 | 1 | Void Spirit | 1 | 3 | 1 | REJECT | REJECT: accepted by the owner, kept as is | final 1 to 3: conflicts with Brewmaster (expected 1); fits 1(2.36) 3(1.91) 2(1.39), 16.4k, 195 last hits. Round 2 accepted 1; 2 or 3 adjacent. Intent OK |
| 9037038717 | 2 | Windrunner | 4 | 2 | 2 | REJECT | | intent was 2 and accepted; Infernal Blade, Ice Blast, Leap, Warcry now fits 3(2.07) 4(1.81) 2(1.63), 4 is the leftover: regression, expected 2 (3 tolerated). Final 2 OK |
| 9037038717 | 3 | Brewmaster | 3 | 1 | | OK | OK | final fixed (was 3): 30.6k, 364 last hits, top damage of the team; intent 3 OK |
| 9037038717 | 4 | Ancient Apparition | 2 | 4 | 4 | REJECT | | intent 2, was 1; Storm Bolt, Powershot, Shapeshift, Talon Toss: expected 3 or 4, 2 is adjacent; best fits 3(2.54) 2(2.20). Final 4 OK |
| 9037038717 | 128 | Tusk | 2 | 1 | 3 | REJECT | | intent was 3 and accepted; Walrus Punch, Windrun, Scorched Earth are now attack skills (damage 0.95, farm 0.83 with the crit multiplier counted as scaling), best fits 2(2.83) 1(2.35) 3(1.74), 2 is adjacent. Final 1 OK |
| 9037038717 | 129 | Lycan | 4 | 3 | | OK | OK | |
| 9037038717 | 130 | Mirana | 1 | 2 | | OK | OK | |
| 9037038717 | 131 | Doom | 3 | 4 | | OK | OK | intent 3, was 5; final 5 to 4: adjacent, 6.9k, 445 healing, 1 ward; round 2 accepted 5 |
| 9037038717 | 132 | Largo | 5 | 5 | | OK | OK | intent fixed (was 2): Living Armor and Rhapsody give save 1.00. Final 4 to 5: 16 wards, 1357 healing, reads right; round 2 accepted 4 |

## Team verdicts

| match | team | stage | tokens | assistant | owner | comment |
|---|---|---|---|---|---|---|
| 9038725154 | RADIANT | PICK | no findings | OK | OK | Ice Path and Power Cogs now count as initiation |
| 9038725154 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |
| 9038725154 | DIRE | PICK | no findings | OK | OK | Nature's Attendants is a heal; Storm Bolt and Doom-like stuns count as initiation |
| 9038725154 | DIRE | FINAL | INTENT_NOT_PLAYED | OK | OK | CARRIES_3 gone: the farm priority now includes the last-hit share |
| 9037038717 | RADIANT | PICK | CARRIES_3,NO_SAVE_HEAL | REJECT | | no heal is plausible and Storm Bolt and Astral Step now count as initiation, but three carries is too many: Void Spirit and Brewmaster farm, Ancient Apparition (0.65) does not; expected CARRIES_2 or none |
| 9037038717 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |
| 9037038717 | DIRE | PICK | no findings | OK | OK | Living Armor is a protection, Snowball and Doom are initiation |
| 9037038717 | DIRE | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |

## Open after this round (follow-ups, not blockers)

- Venomancer (expected 3) is still 4; Void Spirit now conflicts with Brewmaster for the 1 and needs a reviewer's decision.
- Next final-stage inputs: initiation and disable landed at fights (the fight tables), tower damage.
- Intent rows still rejected as in round 2 (Sniper, Enchantress, Sven, Furion, Windrunner, Ancient Apparition, Tusk); the attack-skill rule was not changed for lack of a concrete safe change.
- All Pick is not reviewed yet; it follows the same loop when the owner asks for it.

## Owner verdict (2026-10-13)

Round 3 is accepted as it is (owner, 2026-10-13). The open rows stay as recorded hypotheses: Void Spirit against Brewmaster for role 1 (the assistant's REJECT on Void Spirit is kept, accepted by the owner, kept as is), the Doom and Largo swap, and Venomancer. The other REJECT rows are the recorded intent hypotheses above and carry no owner mark.
