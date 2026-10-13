# Creep values and the gold and experience reasons

Measured on the four fixture games (All Pick 9040892342 and 9040889566, Ability Draft 9038725154 and 9037038717), from the replay's own combat log. Everything here is a first guess for later analyses (laning, farm, item efficiency), not a rule. The machine-readable copy is `server/src/main/resources/replay/creep-values.json`, read by `CreepValues`.

## Gold and experience reasons

`CombatLogRecorder` now stores Clarity's `getGoldReason()` for GOLD events and `getXpReason()` for XP events in `CombatEvent.aux` (before, `aux` held the stun duration, always 0 for these kinds). `GoldReason` and `XpReason` name the numbers; a number not named reads as `UNKNOWN`. What each value was checked against is in the last column; every GOLD and XP event has the receiving hero as `target`.

Gold, all four games together:

| Reason | Name | Events | Average | Checked against |
|---|---|---|---|---|
| 0 | UNSPECIFIED | 88 | 452 | 600 at -90 s for each hero; a few one-off grants |
| 1 | DEATH | 291 | -161 | negative; the dying hero |
| 11 | BUILDING | 334 | 129 | the same second as a BUILDING_KILL; every hero of the team |
| 12 | HERO_KILL | 1145 | 135 | the killer's gold has a hero DEATH at the same second; assists have none |
| 13 | CREEP_KILL | 3826 | 43 | the same second as a lane creep DEATH by the hero |
| 14 | NEUTRAL_KILL | 2087 | 31 | the same second as a camp creep DEATH by the hero |
| 15 | BOSS_KILL | 20 | 162 | Roshan or the Tormentor (`npc_dota_miniboss`); the DEATH row of the killer says which |
| 16 | COURIER_KILL_TEAM | 57 | 70 | four heroes at the second of a courier kill |
| 17 | BOUNTY_RUNE | 340 | 58 | five heroes of one team at once: 40 gold at 0:00, 46 at 6:00 |
| 20 | WARD_KILL | 25 | 157 | the DEATH of an observer or sentry ward |
| 21 | COURIER_KILL | 13 | 68 | the DEATH of `npc_dota_courier` |
| 6, 19, 22 | UNKNOWN | 49, 46, 74 | 174, 100, 56 | not explained (22 shows up beside kills of a hero's summons) |

Experience:

| Reason | Name | Events | Average | Checked against |
|---|---|---|---|---|
| 0 | UNSPECIFIED | 433 | 10 | mostly 0 |
| 1 | HERO_KILL | 709 | 481 | a hero DEATH |
| 2 | CREEP_KILL | 9798 | 41 | every creep that died near the hero, last hit or not |
| 3 | ROSHAN_KILL | 6 | 317 | the Roshan DEATH second; 215 then 520 |
| 4 | WISDOM_RUNE | 58 | 634 | 7:00, 14:00, 21:00, 28:00 with 200, 500, 800, 1100 |
| 7, 8 | UNKNOWN | 81, 304 | 57, 5 | not explained |

So a hero kill, a lane creep, a camp creep, a bounty rune, Roshan and a building are all told apart by the gold reason alone; Roshan versus the Tormentor needs the DEATH row.

### Old stored matches

The blob layout did not change, but its meaning did, so `CombatLogCodec.VERSION` is 2 and the decoder still reads version 1. A version 1 blob holds 0 in `aux` of its GOLD and XP events, which reads as `UNSPECIFIED`. `CombatLogReader.codecVersion` gives the version, and `CreepKillAnalyzer` skips a match with a version 1 blob (it keeps the rows it has and logs one INFO line); the match gets its reasons by being parsed again from the replay (the replay file must still exist, about 3 months).

## Siege creeps and the other names

Names of what heroes killed (DEATH rows with a real hero as the attacker), from the four games:

- Melee, ranged and flagbearer lane creeps: `npc_dota_creep_<goodguys|badguys>_<melee|ranged|flagbearer>`, with `_upgraded` for super creeps; the flagbearer of a super wave is `flagbearer_upgraded`. The mega creep is `npc_dota_creep_<side>_melee_upgraded_mega` (seen only as the killer of a fort, never killed by a hero here).
- The siege creep is `npc_dota_goodguys_siege` and `npc_dota_badguys_siege`, with no `creep_` part. `ActivitySegmenter` tested the prefix `npc_dota_creep_`, so every siege last hit and deny was missed: 48 kills in the four games. It now counts them (an enemy siege creep is lane farm, an own one is a deny and is not). Because the rule changed, `ActivityAnalyzer` is version 2, and a re-run changes a lane farm label only for a hero with a siege last hit within the look-back window.
- Camp creeps: `npc_dota_neutral_*`, 45 different units. Ancient camp units are listed in `CreepClass` (black dragon and drake, rock and granite golem, ice shaman and frostbitten golem, big and small thunder lizard, prowler shaman and acolyte, ancient frog and frog mage); the measured values below confirm it (about 80 gold for the leaders, 105 to 139 experience).
- Not creeps and not counted: summons of heroes (`npc_dota_lycan_wolf1` to `4`, `npc_dota_dark_troll_warlord_skeleton_warrior`, `npc_dota_grimstroke_ink_creature`, `npc_dota_unit_undying_zombie*`, tombstones), wards, the courier, Roshan, the Tormentor, towers, barracks and fillers.

All of this is in one place, `CreepClass.of(unitName)`, used by `ActivitySegmenter` and by `CreepKillAnalyzer`.

## What a creep pays

The kill's gold and experience entries are written just before its DEATH row at the same moment, so the entries between the previous DEATH row and this one belong to the kill (`CreepKillLinker`). A flagbearer pays a second gold entry after its DEATH row, shared with the nearby team, kept as `bonusGold`. Experience is split between the heroes in range at that moment (`xpRecipients`), so the value of one creep is read only where the killer was the only recipient.

Gold is the median the killer received, for last hits (not denies). Gold in brackets is the 10th to 90th percentile; a few kills pay 160 (a Hand of Midas style bonus), which the median ignores.

| Class | Kills | Gold | Experience (one recipient) | Remark |
|---|---|---|---|---|
| LANE_MELEE | 1492 | 38 (35 to 41) | 57 (823) | gold 37, 38, 40 in the first 10, 10 to 20 and later minutes |
| LANE_RANGED | 595 | 52 (46 to 58) | 85 (353) | gold 48, 51, 56 by the same periods; experience 69, 77, 93 |
| LANE_FLAGBEARER | 242 | 38 (35 to 41) | 60 (143) | plus a bonus of 47 |
| LANE_SIEGE | 47 | 64 (60 to 71) | 88 (29) | |
| LANE_MELEE_UPGRADED | 24 | 23 (20 to 26) | 25 (7) | few samples |
| LANE_RANGED_UPGRADED | 8 | 35 (31 to 38) | 22 (2) | too few samples |
| LANE_FLAGBEARER_UPGRADED | 1 | 24 | none | one sample, bonus 33 |
| LANE_MEGA, LANE_OTHER | 0 | not measured | | |
| NEUTRAL | 1394 | 23 (12 to 55) | 47 (1107) | varies by unit, see below |
| NEUTRAL_ANCIENT | 154 | 49 (40 to 81) | 110 (117) | |

Camp creeps differ so much that one class value is a rough guide only; the JSON has per unit values for units with at least five kills. A few of them (gold, experience): kobold 7, 24; satyr trickster 15, 34; centaur outrunner 20, 42; forest troll berserker 22, 38; giant wolf 23, 50; ogre mauler 28, 42; grown frog 42, 65; alpha wolf 35, 70; warpine raider 51, 91; centaur khan 60, 100; satyr hellcaller 65, 100; ursa warrior 66, 100; black dragon 80, 139; big thunder lizard 83, 134.

How to measure again after a change: process the fixtures through `ReplayPostgresSmokeTest` against a local PostgreSQL and take the medians over the `creep_kill_domain` table, for example `percentile_disc(0.5) within group (order by gold)` per `creep_class` over `denied = false`, and the experience over `xp_recipients = 1`.

## Creep kills against OpenDota

`creep_kill_domain` holds one row per creep a hero killed. Its last hits are the rows that are not denies; OpenDota's `last_hits` is the comparison. The smoke test asserts, per hero, never more than OpenDota plus three (denies plus two) and, per match, at least 75 percent in total.

| Match | Last hits here | OpenDota | Share | Denies here | OpenDota |
|---|---|---|---|---|---|
| 9040889566 | 1029 | 1123 | 92% | 70 | 74 |
| 9040892342 | 1124 | 1358 | 83% | 49 | 51 |
| 9038725154 | 1542 | 1660 | 93% | 69 | 77 |
| 9037038717 | 1291 | 1511 | 85% | 72 | 76 |

Heroes without summons are within about 10 percent, the denies are mostly exact. The missing last hits are creeps killed by a hero's summons (wolves, skeletons, zombies, spirits), which OpenDota credits to the owner and the stored combat log does not keep, because it keeps deaths only where a hero is the attacker or the target. A fix would be a wider whitelist in `CombatLogRecorder` and is not part of this change.

## Linking limits

About 99 percent of the last hits get their gold, and 96 percent their experience (most of the rest are kills with two gold entries before one DEATH row, left empty on purpose). The place of a kill comes from the gold or experience entry (the log's DEATH rows carry no position), so a deny has none and the zone is null; 120 of the stored kills fall in a BASE zone, which is where creeps are killed near the high ground.

## Known limits

- The experience recipients are counted from the entries between the previous DEATH row and this one, so another creep dying in the same moment can raise `xpRecipients` and make a value read from it too low.
- Entries and DEATH rows are matched by order inside one centisecond; two kills of one hero in that moment with two gold entries before one DEATH row leave the gold empty.
- The ancient camp list in `CreepClass` is a first guess, set from names and the measured values.
- Kills by a hero's summons are not in the log (see above).
