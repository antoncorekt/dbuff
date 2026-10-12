# Replay: explaining a lost fight, and why

Status: plan, nothing built. Replay processing and the report stay off in production. Builds on the merged roles (`player_role`, `player_role_profile`, `team_composition`), the game-mode profiles, and the review loop (`docs/replay-review-loop.md`).

## 1. The goal

The owner's example: a team fell behind in gold because it lost a fight. Find why the fight was lost (common reasons: a carry farming the jungle, no teleport, an item or ability on cooldown, no mana, bad position, no vision), and why that reason happened, with reasons that only make sense in one mode on top (Ability Draft: a weak skill combination or a role the draft did not fit; All Pick: a hero that does not suit the role it was picked for). The output is a short, ranked, readable chain in the Discord thread: swing, fight, who and what, and the reason behind it.

## 2. What exists today

- **Swing to fight:** `SpikeAnalyzer` stores `game_spike` rows and, as `game_spike_factor` rows of type `FIGHT`, the fights inside a swing (winner and deaths in the detail).
- **Per fight and player:** `fight_player` has presence (active, passive, absent), `activityAtStart`, `absenceReason` (`NO_TP`, `TP_ON_COOLDOWN`, `HAD_TP_NOT_USED`), frontline rank and delta. `fight_ability_use` has per ability `nonCastReason` (`ON_COOLDOWN`, `NO_MANA`, `DEAD_EARLY`, `READY_NOT_CAST`). `death_analysis` has a `DeathReason` per death (BKB reasons, `OUT_OF_POSITION`, `DISABLE_CHAIN`, `CAUGHT_OUT`, `TOWER`, `BURST`, `NO_BKB`, `OTHER`).
- **Roles and composition:** `player_role` (final), `player_role_profile` (intent and final, function profile), `team_composition` (PICK and FINAL tokens such as `NO_INITIATOR`, `INTENT_NOT_PLAYED`).
- **Report:** `SwingSection`, `FightSection`, `DeathSection`, `CompositionSection`, each reading these tables separately; nothing links a swing to the reasons behind its fight.

The gaps: the reasons are three enums on three tables with no common shape, nothing uses roles in a reason, nothing explains a fight in terms of a lost swing, no mode-specific reasons exist, and "no vision" has no data (wards are not extracted).

## 3. Design

### 3.1 One reason shape

A `Reason` is a code, a subject (a player slot, or the team), a scope (`PLAYER` or `TEAM`), a severity (0 to 1), a short evidence text, and a source (`COMMON` or the mode). It is stored in a new table `fight_reason` (match, fight index, subject slot null for the team, scope, code, severity, evidence, source, analysis version; unique on match, fight, subject, code). The existing columns and enums stay as they are (no migration, no behaviour change); a new analyzer reads them and writes `fight_reason` rows from them, so the three old reason families appear under one shape.

### 3.2 Contributors per mode

`ReasonContributor` is the extension point: `supports(mode)`, `contribute(FightContext)` returning reasons. A common contributor set runs for every mode, a mode adds its own, and the analyzer never changes when a mode is added (the same pattern as `RoleResolver` and `CompositionRules`).

- **Common, from data that exists:** `NO_TP`, `TP_ON_COOLDOWN`, `HAD_TP_NOT_USED`, `ITEM_ON_COOLDOWN`, `ABILITY_ON_COOLDOWN`, `NO_MANA`, `OUT_OF_POSITION`, the BKB family, `DIED_FIRST`.
- **Common, with roles (new):** `CARRY_FARMING_ELSEWHERE` (final role 1 or 2 whose activity at the start of the fight was jungle or lane farming while the fight was away from it), `SUPPORT_ABSENT` (roles 4 and 5 not in the fight), `ROLE_NOT_FILLED` (the team composition at the final stage had a missing function token such as `NO_INITIATOR` or `NO_DISABLE`, so the team reason is stated at team level).
- **Ability Draft:** `INTENT_NOT_PLAYED` seen as a fight reason (a hero played a role his skills were not drafted for), `WEAK_COMBINATION` (hypothesis: low initiation, disable and damage together for the team's drafted skills), `STRONG_SKILL_LEFT` (a skill high in function value stayed in the pool until the draft ended, from `ability_draft_pool`).
- **All Pick:** `HERO_ROLE_MISMATCH` (the hero's usual roles from the constants against the role he played), `COMPOSITION_GAP` from the FINAL team tokens.
- **Vision (later, needs new data):** `NO_VISION` from wards, see 3.5.

Every threshold is in one properties class, a hypothesis for the loop.

### 3.3 From a swing to a ranked explanation

A `FightLossAnalyzer` (after the fight, death, role and composition analyzers) writes `fight_explanation` per lost fight: the losing team, the gold of the fight (from the swing factors, or the deaths' gold), and the top reasons by severity times the share of the loss they explain. A read model joins `game_spike`, its `FIGHT` factors, `fight_explanation` and `fight_reason`. Ranking rule, first guess: team-level reasons first only when no player reason reaches the minimum severity; at most three reasons per fight, at most two fights per swing.

### 3.4 Report

`SwingSection` gets one more line per swing when an explanation exists, for example "Radiant lost 3.2k from the fight at 23:10: Terrorblade was farming the jungle, Venomancer had no teleport, the team had no disable" and, for Ability Draft, "Jakiro was drafted as a damage hero and played mid". It respects the existing detail levels and caps and shows nothing when no explanation is stored.

### 3.5 Wards (separate group, not required for the rest)

No vision needs ward data: placed and killed wards per team and area. The earlier spike (S5) says the combat log has no placement and that entity create and delete is the source. This is its own extraction PR with a parse cost to measure at 512 MB before anything depends on it; reasons that need it are added after it exists.

## 4. Review loop for fights

Reasons are judgement calls, so they go through the review loop (`docs/replay-review-loop.md`). This is the second topic, so the generic parts are extracted first: round numbering, match list, diff, the local-database guard and the Gradle task get a neutral name, with the role renderer on top. A fights renderer prints, per swing, the fights inside, per fight the ranked reasons with their evidence, the OpenDota fight facts to compare with, and the verdict columns. The match list starts with the four matches in `role-loop/matches.txt`; more matches with lost-fight swings may be added.

## 5. PR split

1. **6a, loop made generic** (rename, topic parameter, no behaviour change; tests prove the role loop still works).
2. **6b, reason model:** `Reason`, `ReasonContributor`, `fight_reason`, the analyzer that mirrors the existing absence, ability and death reasons into it (the old columns untouched). Common contributors for data that exists. Tests per contributor, DB test, smoke test on the four matches.
3. **6c, role-aware common reasons** (`CARRY_FARMING_ELSEWHERE`, `SUPPORT_ABSENT`, team gap from composition tokens).
4. **6d, `FightLossAnalyzer`, `fight_explanation`, the swing read model** and the fights renderer for the loop.
5. **6e, mode contributors:** Ability Draft first, All Pick after.
6. **6f, report line** in `SwingSection`.
7. **Loop rounds on fights** after 6d, until the owner accepts; mode contributors are reviewed in their own rounds.
8. **Roles, final-stage data** (damage dealt and healing done per player from the stored combat log into the final profile, then a roles round 3) can run in parallel with 6a to 6c and is the fix for the two open final rows (Brewmaster, Venomancer).
9. **Wards extraction** (3.5) as its own group, then `NO_VISION`.

Each PR is reviewed with the code review before the next, and nothing is merged without the owner's word.

## 6. Decisions of the owner (2026-10-12)

1. **Storage and message are separate.** The lost-fight analysis stores every reason it finds (several per fight, no cap). The Discord line only shows the top three. The caps (three reasons, two fights per swing) belong to the report, not to the analyzer.
2. **Weight by role and presence only,** not by the gold of the heroes. Gold weighting is not built.
3. **Wards mean vision in a fight in general,** mostly for invisible heroes (sentry ward, dust used or missing) and a ward that appeared just before the fight (smaller weight). Extracted after the reasons that need no new data are accepted; the extraction scope is wards placed and killed, sentry and dust use, with times.
4. **The fights loop starts with the same two matches** as the first role loop round (Ability Draft 9038725154 and 9037038717). All Pick matches and more lost-fight swings are added later.

Next step: 6a and 6b in parallel (no shared classes), then 6c, 6d, 6e, 6f as in section 5.
