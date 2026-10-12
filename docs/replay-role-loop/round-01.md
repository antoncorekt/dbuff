# Round 01

- Date: 2026-10-12
- Commit or PR the reports came from: `main` at the merge of #49 (first Ability Draft resolver, hypothesis weights, 17 override entries)
- Matches: 9038725154 and 9037038717 (Ability Draft). The two All Pick matches were stored but are not reviewed until Ability Draft is accepted.
- Changes since the previous round: none, this is the first round.
- Status: assistant reviewed

`assistant` and `owner` are `OK` or `REJECT`. `expected` is the role (1 to 5) the reviewer says the player filled (final) or was meant to fill (intent, named in the comment). Read the roles from `server/build/role-loop/round-01/<matchId>.md`.

## Reading of the round

- **Final roles equal OpenDota's `position_est` for 30 of 30 players.** That is the lane label, not a role label, so the final stage adds nothing yet over what OpenDota says. Two players are the cases where function and label differ most: Brewmaster (30.6k, nearly double his team, jungled and farmed) and Venomancer (highest net worth of his team, labelled 4).
- **Intent is weak: 7 of 20 rejected, most with confidence under 0.2.** The worst is Enchantress, intent 5 with confidence 0.67 for a damage kit.
- **Causes found so far** (the first two are data gaps, not weights):
  1. attack-modifier and passive damage skills are scored as little damage or farm (Headshot, God's Strength, Impetus, Great Cleave, Focus Fire, Falcon Rush, Shapeshift);
  2. heal and protection skills are not recognised as save or heal (Nature's Attendants, Living Armor, Rhapsody), so `NO_SAVE_HEAL` fires on teams that have one;
  3. initiation from kits is near zero for any stun or area disable that is not an override, so `NO_INITIATOR` fires on every team;
  4. a role may be given to a player only because the other four took the better-fitting ones (Jakiro: best fit 3 then 2, assigned 5): the assignment hides that;
  5. in the final stage the lane bonus outweighs net worth and farm share.

## Players

| match | slot | hero | intent | final | expected | assistant | owner | comment |
|---|---|---|---|---|---|---|---|---|
| 9038725154 | 0 | Sniper | 3 | 3 | | OK | | kit Take Aim, Teleportation, Conjure Image, False Promise reads as a core; 1 or 3 both fine (24.0k, third in team, offlane) |
| 9038725154 | 1 | Enchantress | 5 | 2 | 2 | REJECT | | intent: Impetus, Sunder, Immaterial, Shrapnel is a damage kit, expected intent 2 (1 tolerated); confidence 0.67 on a wrong role. Final OK |
| 9038725154 | 2 | Riki | 4 | 4 | | OK | | stuns and poison, 14.8k |
| 9038725154 | 3 | Death Prophet | 2 | 5 | 5 | REJECT | | intent: tanky Power Cogs and Warcry kit, expected intent 3 or 4, not 2. Final 5 OK (lowest net worth) |
| 9038725154 | 4 | Phantom Assassin | 1 | 1 | | OK | | |
| 9038725154 | 128 | Sven | 1 | 5 | 5 | REJECT | | intent: Storm Bolt, Assassinate, Enchant, Reflection is a utility kit, expected intent 4 or 5, not 1. Final 5 OK |
| 9038725154 | 129 | Venomancer | 4 | 4 | 3 | REJECT | | final: highest net worth in the team (17.3k), offlane; role by function is a core (3). Intent 4 is tolerated |
| 9038725154 | 130 | Furion | 3 | 3 | | OK | | two damage ultimates (Metamorphosis, Exorcism) would also fit 1, but 13.9k and offlane: 3 tolerated |
| 9038725154 | 131 | Terrorblade | 2 | 1 | | OK | | poke kit, safe lane, no clear carry in the team: 1 tolerated |
| 9038725154 | 132 | Jakiro | 5 | 2 | 2 | REJECT | | intent: Headshot, God's Strength, Phantom Strike, Ransack is a damage kit played mid, expected intent 2, got the leftover 5 (best fit was 3 then 2). Final 2 OK |
| 9037038717 | 0 | Crystal Maiden | 5 | 5 | | OK | | confidence 0.07 on intent |
| 9037038717 | 1 | Void Spirit | 4 | 1 | 1 | REJECT | | intent: Focus Fire and Falcon Rush are attack-speed damage skills, expected intent 1, not 4. Final 1 OK |
| 9037038717 | 2 | Windranger | 2 | 2 | | OK | | |
| 9037038717 | 3 | Brewmaster | 3 | 3 | 1 | REJECT | | final: 30.6k net worth, nearly double the team, jungle farm and Great Cleave: carry by function (1), the lane label says 3 |
| 9037038717 | 4 | Ancient Apparition | 1 | 4 | 4 | REJECT | | intent: Storm Bolt, Powershot, Shapeshift, Talon Toss; expected intent 3 or 4, not 1. Final 4 OK |
| 9037038717 | 128 | Tusk | 3 | 1 | | OK | | weak team (lost 16 to 44), safe lane, second in net worth: 1 tolerated |
| 9037038717 | 129 | Lycan | 4 | 3 | | OK | | richest of the team (15.8k), wolves and Feral Impulse: 1 or 3 tolerated |
| 9037038717 | 130 | Mirana | 1 | 2 | | OK | | God's Strength and Shackleshot, mid: fine |
| 9037038717 | 131 | Doom | 5 | 5 | | OK | | confidence 0.01 on intent |
| 9037038717 | 132 | Largo | 2 | 4 | 4 | REJECT | | intent: Catchy Lick, Arrow, Living Armor, Rhapsody is a support kit, expected intent 4 or 5, not 2. Final 4 OK |

## Team verdicts

| match | team | stage | tokens | assistant | owner | comment |
|---|---|---|---|---|---|---|
| 9038725154 | RADIANT | PICK | NO_INITIATOR | REJECT | | has Ice Path and Power Cogs (stun and trap) |
| 9038725154 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | | |
| 9038725154 | DIRE | PICK | NO_INITIATOR,NO_SAVE_HEAL | REJECT | | Nature's Attendants is a heal; no initiator is plausible |
| 9038725154 | DIRE | FINAL | CARRIES_3,INTENT_NOT_PLAYED | OK | | three cores by farm priority, plausible |
| 9037038717 | RADIANT | PICK | NO_INITIATOR,NO_SAVE_HEAL | REJECT | | Storm Bolt, Astral Step, Frostbite: there are initiators; no heal or save is plausible |
| 9037038717 | RADIANT | FINAL | INTENT_NOT_PLAYED | OK | | |
| 9037038717 | DIRE | PICK | NO_INITIATOR,NO_SAVE_HEAL | REJECT | | Living Armor is a protection skill; Snowball and Doom are initiation |
| 9037038717 | DIRE | FINAL | INTENT_NOT_PLAYED | OK | | |

## Planned changes for round 02

1. Skills: add override entries for the attack-modifier and passive damage skills seen here and let the rules count attack damage and attack speed keys as damage and farm priority (data first, weights untouched).
2. Heal and protection skills: count heal, regeneration and damage-block keys as save or heal and add entries for the skills seen here.
3. Initiation: count a stun or area disable with a cast range as initiation, not only the override entries.
4. Final stage: let farm share and net worth carry more of the role than the lane (one weight in `RoleProfileProperties`, noted in the round).
5. Report: show the best three roles by fit next to the assigned one in the Roles table, so an assignment leftover is visible at a glance.
