# Round 02

- Date: 2026-10-12
- Commit or PR the reports came from: branch `feature/replay-role-round-02` (round 1 was `main` at the merge of #49)
- Matches: 9038725154 and 9037038717 (Ability Draft). The two All Pick matches were stored and re-analysed again; their roles and verdicts did not change, and they are not reviewed until Ability Draft is accepted.
- Changes since the previous round (weights, rules, tags; one line each):
  - Skills, damage and farm: attribute keys of attack speed and attack damage (`attack-damage-keys`), an `Attack Modifier` behaviour and a passive that deals damage count as an attack skill; each adds `attack-skill-damage` (0.2) to damage and `attack-skill-farm` (0.15) to farm priority. Overrides only for `kez_falcon_rush_ad` and `kez_talon_toss_ad`, which have no attributes in the constants.
  - Heal and protection: more keys in `save-keys` (`heal_per_second`, `damage_block_base`, `damage_block`, `damage_absorb`, `shield_amount`), and a no-target skill counts as a save when it has a heal key (`area-save-keys`), since the constants give no target team for it and a no-target block or barrier may protect only the caster (Nature's Attendants and Living Armor come from the rules). Override only for `largo_amphibian_rhapsody` (armor aura, no key).
  - Initiation: a stun, hex, root, sleep, fear, taunt or silence of at least 1 s on a skill with cast range 400 or more, or in an area, adds 0.5 to initiation whatever its cooldown (Ice Path, Storm Bolt, Snowball, Cold Feet, Shackleshot); `max_travel_distance` and `leap_distance` are gap-closer keys (Astral Step, Leap). Overrides for `doom_bringer_doom` and `rattletrap_power_cogs`, whose control is not in a stun key.
  - Final stage, Ability Draft only (All Pick scores and profiles are unchanged): the lane bonus is multiplied by `fit.final-lane-factor` (0.5), and a function the FINAL stage does not know counts `fit.final-unknown-value` (0) instead of 0.5, because with 0.5 the unknown initiation and durability gave roles 3 and 4 a constant lead over 1 and 5 and every player fitted 3 best.
  - Report: the `Roles` table has `intent fit` and `final fit` columns (the three best roles by score); the diff compares the columns both reports share.
  - Analyzer versions: `roles` 2 to 3, `composition` 1 to 2.
- Status: accepted for now (owner, 2026-10-12): the owner agrees with every assistant verdict and accepts the round for roles at this point; the rejected rows stay open as follow-ups below, not as blockers

`assistant` and `owner` are `OK` or `REJECT`. `expected` is filled for a reject: the role (1 to 5) the reviewer says the player filled (final) or was meant to fill (intent, named in the comment). `intent` is `n/a` for a mode without intent rows (All Pick). Read the roles from `server/build/role-loop/round-02/<matchId>.md`.

## Known hypotheses to review

- Initiation is counted per skill independently of the control branch that feeds disable.
- A passive that deals damage counts as an attack skill.
- The additive attack terms can saturate damage and farm priority at 1.
- The generic gap-closer keys `max_travel_distance` and `leap_distance`.

## Reading of the round

- **Final roles did not change for any of the 20 players**: they still equal OpenDota's `position_est` 20 of 20. The two final rejects of round 1 are still rejected: Brewmaster (best fit now 1, assigned 3) and Venomancer (best fit 3, assigned 4). The cause is the assignment, not the scores: Brewmaster cannot be 1 while Void Spirit (safe lane, third in net worth, the accepted 1) holds it, and Venomancer shares the offlane with Furion. Lowering the lane bonus further (0.2 and 0) moved other players (Windrunner to 1, Mirana to 3) and was not kept. The roles table now shows these leftovers: `final fit` reads `1(2.05) 3(1.94) 2(0.60)` for Brewmaster and `3(1.29) 4(1.27) 1(0.51)` for Venomancer next to the assigned role (Venomancer is nearly a tie between 3 and 4).
- **Intent**: 4 rejected rows of round 1 are fixed (Death Prophet, Void Spirit, Largo, Jakiro tolerated), 4 accepted rows of round 1 moved to a worse intent (Sniper, Furion, Windrunner, Tusk) and Enchantress, Sven and Ancient Apparition are still rejected. The new attack rule lifts damage kits a lot (Void Spirit farm 0.93, Mirana 0.98, Ancient Apparition 0.65 from Shapeshift); the rule may be too generous for a skill that is a stat boost on a hero without the attack to use it.
- **Team verdicts**: `NO_INITIATOR` no longer fires on any team and `NO_SAVE_HEAL` only on Radiant of 9037038717, which has no heal. All four PICK verdicts of round 1 were rejected; three are now accepted, and Radiant of 9037038717 has `CARRIES_3`, which is too many (Ancient Apparition is not a carry). No FINAL verdict changed.
- **Not solved**: the final stage still follows the lane label for every player; the data it lacks (damage, healing, vision, initiation at fights) is the real gap, and the weight only helps where the assignment has a choice.

## Players

| match | slot | hero | intent | final | expected | assistant | owner | comment |
|---|---|---|---|---|---|---|---|---|
| 9038725154 | 0 | Sniper | 2 | 3 | 3 | REJECT | REJECT | intent was 3 and accepted; Oracle's False Promise now gives save 0.90 and farm is 1.00, best fits 2(1.46) 3(1.32) 1(1.28): regression, expected 3 (1 tolerated). Final OK |
| 9038725154 | 1 | Enchantress | 5 | 2 | 2 | REJECT | REJECT | intent still 5: best fits 3(0.76) 2(0.69) 1(0.45), so 5 is the leftover of the assignment; damage 0.33 for Impetus, Sunder, Immaterial, Shrapnel is low. Final OK |
| 9038725154 | 2 | Riki | 4 | 4 | | OK | OK | |
| 9038725154 | 3 | Death Prophet | 3 | 5 | | OK | OK | intent fixed (was 2); final 5 OK |
| 9038725154 | 4 | Phantom Assassin | 1 | 1 | | OK | OK | |
| 9038725154 | 128 | Sven | 3 | 5 | 4 | REJECT | REJECT | intent 3, was 1; Storm Bolt, Assassinate, Enchant, Reflection is a utility kit with a nuke (damage 1.00, farm 0.80); adjacent to the expected 4. Final 5 OK |
| 9038725154 | 129 | Venomancer | 4 | 4 | 3 | REJECT | REJECT | final unchanged: richest of the team (17.3k, farm 0.50 because the laning place counts half), fits 3(1.29) 4(1.27), Furion takes 3 and the solver gives Venomancer 4 |
| 9038725154 | 130 | Furion | 5 | 3 | 3 | REJECT | REJECT | intent was 3 and accepted; Nature's Attendants is now a heal (save 0.67), so he reads 5 with best fits 3(0.74) 5(0.68): regression, expected 3. Final 3 OK |
| 9038725154 | 131 | Terrorblade | 2 | 1 | | OK | OK | |
| 9038725154 | 132 | Jakiro | 1 | 2 | | OK | OK | intent was the leftover 5, now 1 with damage 1.00; expected 2, 1 tolerated. Final 2 OK |
| 9037038717 | 0 | Crystal Maiden | 5 | 5 | | OK | OK | intent confidence 0.89 now |
| 9037038717 | 1 | Void Spirit | 1 | 1 | | OK | OK | intent fixed (was 4): Focus Fire, Falcon Rush and the katana give farm 0.93 and damage 1.00 |
| 9037038717 | 2 | Windrunner | 4 | 2 | 2 | REJECT | REJECT | intent was 2 and accepted; Infernal Blade, Ice Blast, Leap, Warcry now fits 3(2.07) 4(1.81) 2(1.63), 4 is the leftover: regression, expected 2 (3 tolerated). Final 2 OK |
| 9037038717 | 3 | Brewmaster | 3 | 3 | 1 | REJECT | REJECT | final unchanged: 30.6k, farm 0.88, jungle 1.00, best fit 1(2.05) over 3(1.94) but Void Spirit needs the 1; intent 3 OK |
| 9037038717 | 4 | Ancient Apparition | 2 | 4 | 4 | REJECT | REJECT | intent 2, was 1; Storm Bolt, Powershot, Shapeshift, Talon Toss: expected 3 or 4, 2 is adjacent; best fits 3(2.54) 2(2.20). Final 4 OK |
| 9037038717 | 128 | Tusk | 2 | 1 | 3 | REJECT | REJECT | intent was 3 and accepted; Walrus Punch, Windrun, Scorched Earth are now attack skills (damage 0.95, farm 0.83 with the crit multiplier counted as scaling), best fits 2(2.83) 1(2.35) 3(1.74), 2 is adjacent. Final 1 OK |
| 9037038717 | 129 | Lycan | 4 | 3 | | OK | OK | |
| 9037038717 | 130 | Mirana | 1 | 2 | | OK | OK | |
| 9037038717 | 131 | Doom | 3 | 5 | | OK | OK | intent 3, was 5: Snowball, Ice Shards and Doom give initiation 1.00; confidence now readable. Final 5 OK |
| 9037038717 | 132 | Largo | 5 | 4 | | OK | OK | intent fixed (was 2): Living Armor and Rhapsody give save 1.00. Final 4 OK |

## Team verdicts

| match | team | stage | tokens | assistant | owner | comment |
|---|---|---|---|---|---|---|
| 9038725154 | RADIANT | PICK | no findings | OK | OK | Ice Path and Power Cogs now count as initiation |
| 9038725154 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |
| 9038725154 | DIRE | PICK | no findings | OK | OK | Nature's Attendants is a heal; Storm Bolt and Doom-like stuns count as initiation |
| 9038725154 | DIRE | FINAL | CARRIES_3,INTENT_NOT_PLAYED | OK | OK | unchanged |
| 9037038717 | RADIANT | PICK | CARRIES_3,NO_SAVE_HEAL | REJECT | REJECT | no heal is plausible and Storm Bolt and Astral Step now count as initiation, but three carries is too many: Void Spirit and Brewmaster farm, Ancient Apparition (0.65) does not; expected CARRIES_2 or none |
| 9037038717 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |
| 9037038717 | DIRE | PICK | no findings | OK | OK | Living Armor is a protection, Snowball and Doom are initiation |
| 9037038717 | DIRE | FINAL | INTENT_NOT_PLAYED | OK | OK | unchanged |

## Open after acceptance (follow-ups, not blockers)

- Final stage follows the lane label (20 of 20 equal to OpenDota's `position_est`); Brewmaster (expected 1) and Venomancer (expected 3) are the rows that show it. Next input to add: damage dealt and healing done per player from the stored combat log.
- Intent rows still rejected: Sniper, Enchantress, Sven, Furion, Windrunner, Ancient Apparition, Tusk; the attack-skill rule is probably too generous (see the known hypotheses above).
- Radiant of 9037038717 reads `CARRIES_3` at the pick stage, which is too many.
- All Pick is not reviewed yet; it follows the same loop when the owner asks for it.
