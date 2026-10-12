# Replay: Ability Draft roles (design for PR 5)

Status: design, nothing built. Based on `main` at 2195e8d, which already has the All Pick resolver (`PlayerRoleAnalyzer` `@Order(15)`, `RoleResolver`, `AllPickRoleResolver`, `RoleAssignmentSolver`, table `player_role`).

## 0. Scope and decisions

The owner's decisions, which shape this document:

1. **This PR set only prepares and refactors**: extension points, the data we must store, interfaces. It does not deliver a tuned algorithm. The algorithm is learned afterwards in a review loop on a fixed list of matches (section 5). Ability Draft first, then the All Pick resolver is put through the same loop.
2. **Roles are a model, not labels.** Each player is a function profile; the role label is the best fit of that profile, and the team gets a composition verdict (section 2).
3. **Replay-processed matches only**; the replay is the source of truth.
4. **Both intent and final are stored and shown** (section 4).

The point of the feature stays the owner's example: a team lost a lot of gold after a lost fight, so find why the fight was lost (carry in the jungle, no teleport, item on cooldown, no mana, bad position, no vision, and in Ability Draft a weak ability combination) and why that happened. Roles and the composition verdict are the join between a fight failure and a draft cause.

## 1. Inputs today and what is missing

**Exists** (all for replay-processed matches):

- Draft: `AbilityDraftPickDomain` (40 rows: `pickOrder`, `playerSlot`, `team`, `abilityName`, `finalAbilitySlot` 0 to 2 regular and 3 ultimate, `ultimate`, `secondsSincePrevPick`) and `AbilityDraftPoolDomain` (48 rows; skipped skills have no `pickedBySlot`).
- Behaviour: `PlayerMatchStatisticDomain` (`laneRole`, `lane`, `netWorth`, gold, last hits and xp at 5/10/15/20 min, `obsPlaced`, `senPlaced`, `campsStacked`, `heroHealing`, `stuns`, `teamfightParticipation`), `PlayerTimelineDomain` (net worth by minute), activity segments and `PlayerPhaseSummaryDomain` (jungle, lane, traveling, teleporting seconds), `FightPlayerDomain.presence`, `FrontlineRanker`, `ReplayStateChangeDomain` (items, cooldowns), purchases.
- `player_role` (`matchId`, `playerSlot`, `role`, `source` ASSIGNED or DERIVED, `confidence`, `evidence` of 400 characters, `analysisVersion`, unique on match and slot).
- Re-analysis from stored data: `POST /api/v1/replay/{matchId}/reanalyze` runs every analyzer without the replay.

**Missing**

1. **Hero and ability metadata.** `ConstantsManagers` serves the maps, but `HeroConstant` keeps only `id`, `name`, `localized_name` and `AbilityConstant` only `dname`. `BaseApiConstantService` stores the converted map, so the other fields are lost in the stored `constant` rows too. OpenDota sends, per hero, `roles` (Carry, Support, Nuker, Disabler, Durable, Escape, Initiator, Pusher, Jungler), `primary_attr`, `attack_type`, base health, mana, armor, attack damage, attack rate, range, move speed and attribute gains; per ability `behavior`, `dmg_type`, `bkbpierce`, `dispellable`, `target_team`, `target_type`, `cd` and `mc` per level, `dmg`, `is_innate` and an `attrib` list (`stun_duration`, `bonus_damage`, `slow`, cast range, ...). It has **no** ultimate, disable or blink flag: ultimate comes from our slot 3, the rest we derive.
2. **`position_est`**: OpenDota's 1 to 5 estimate is in the match JSON but not stored. In the 9 Ability Draft matches we hold it equals the lane (89 of 90 rows) and agrees with the in-team net worth rank in only 37 of 90. It is therefore a lane label; store it as a baseline column shown beside our result in the report, not as truth.
3. **No profile, no composition result, no intent row** exist; `RolePlayer` carries no draft data.

## 2. The model

### 2.1 Golden model (from the owner's research)

Sources: Liquipedia Hero Roles, the esports.gg and game-wisdom position guides, the bo3.gg Ability Draft guide, GosuGamers on Ability Draft. They agree on: position 1 hard carry (highest farm priority, win condition), 2 mid (1v1, tempo, rotations), 3 offlane (tanky, initiation, makes space), 4 soft support or roamer (runes, ganks, early skirmishes), 5 hard support (lowest farm priority, vision, stacking, protects the carry). Farm priority falls from 1 to 5. In practice a carry may buy wards, a team may run 2 or 3 carries and no support, or five supports.

Caveat: some public figures (farm split percentages, composition win rates) are unsourced fan data. **None is hard-coded.** The model uses only the ordering (farm priority falls 1 to 5) and the qualitative duties above; every number below is a hypothesis for the loop.

### 2.2 Function profile (hypothesis: the set of functions and their rules)

Per player and stage, nine values in 0..1:

| Function | INTENT (hero and drafted skills) | FINAL (observed play) |
|---|---|---|
| `farmPriority` | farm skills (low cooldown AOE damage, summons, illusions), item-scaling skills (attack modifiers, crit, lifesteal), ranged agility body | net worth share and rank in team, last-hit share, gold at 10 and 20 min |
| `lane` (safe, mid, off) | none (hero cannot say) | `laneRole`, activity zone in the laning phase |
| `jungle` | jungling hero tag | jungle farm seconds share, 0 to 15 min |
| `roaming` | blink, speed, invisibility, cheap level-1 control or nuke | time away from lane in 0 to 10 min, `is_roaming` |
| `visionDuty` | none | wards placed, stacks, ward and dust purchases |
| `initiation` | AOE control plus gap closer | `FrontlineRanker` rank at fights, first-in presence |
| `disable` | stun, silence, root, hex seconds per cooldown (BKB-piercing counts more) | disable seconds landed in fights (`FightAbilityUseDomain`) |
| `saveHeal` | ally-target heal, shield, invulnerability, dispel | `heroHealing`, ally-target casts |
| `damage` | nuke damage per cooldown and mana, attack damage | hero damage share |
| `durability` | base health, armor, strength gain, block and heal skills | damage taken, deaths, front-line time |

Ability features come from a pure `AbilityProfiler` (OpenDota `behavior`, `attrib` keys, `cd`, `mc`, `dmg`, targets) plus a small checked-in override file for what the rules get wrong. The ultimate counts 1.5 and a regular skill 1.0 (hypothesis).

### 2.3 Role label as best fit

For each role r, `fit(p, r) = sum over functions W[r][f] * profile(p, f)`. `W` is the golden model as signs and ordering, for example role 1 high `farmPriority`, role 5 low `farmPriority` and high `visionDuty` and `saveHeal`, role 3 high `initiation` and `durability`, role 4 high `roaming` and `disable`, role 2 high `damage` and mid `lane`; the numbers start from the All Pick resolver's `FARM_WEIGHT`-style table and change in the loop. The five players of a team are assigned one role each with the existing `RoleAssignmentSolver` (so both modes keep "each role once" and the same `player_role` shape). Confidence reuses the solver margin, combined with the agreement of the intent and final roles and with how many inputs were present. Hypothesis: the combination weights.

The role is a label on top of the profile, and the profile is what the composition verdict and the reports read, so a team with three carries and no support still gets a meaningful result: the solver still gives five roles, the verdict says what the team lacks.

### 2.4 Team composition verdict (hypothesis: tokens and thresholds)

Computed per team at two stages: **PICK** (from the INTENT profiles, known right after the draft) and **FINAL** (from the FINAL profiles). It compares the team's summed profile and the count of players per dominant function with the golden model, and emits tokens:

- counts: `CARRIES_3`, `CARRIES_0`, `SUPPORTS_5`, `SUPPORTS_0`;
- missing functions: `NO_INITIATOR`, `NO_DISABLE`, `NO_WAVECLEAR`, `NO_FRONTLINE`, `NO_SAVE_HEAL`, `NO_VISION_DUTY` (FINAL only);
- balance: `NO_FARM_ENGINE` (nobody above the farm threshold), `TOP_HEAVY` (everyone wants farm);
- change between stages: `INTENT_NOT_PLAYED` (the kit says carry, the play says support).

Each token has a threshold on the summed functions; all start as guesses and are tuned in the loop. The verdict is a list of tokens with one short text per token, stored and shown as in section 4.

## 3. Output and storage (decision 4)

**Intent and final: companion table, not a stage column on `player_role`.** Reasons: `player_role` has a unique constraint on (match, slot), and the project uses Hibernate `ddl-auto=update`, which adds columns but never changes an existing constraint, so a stage column would fail on the INTENT row in production; every current consumer of `player_role` (and the All Pick tests) keeps reading the final role unchanged; and the profile needs columns that a role row does not have.

- `player_role` stays: the **FINAL** role. Same columns, same `source`, `confidence`, `evidence`. Evidence for Ability Draft: `final=3(0.64) intent=1(0.71) top3=[...] nwRank=1 lane=2 jungle10=0.05` (fits 400 characters).
- New `player_role_profile` (unique on match, slot, stage): `stage` INTENT or FINAL, `role`, `confidence`, the nine function values as columns, `roleScores` (five numbers, text), `evidence`, `analysisVersion`. FINAL rows duplicate the final role on purpose so a report reads one table; the difference between intent and final is a query (`role` differs), not a stored field. All Pick writes FINAL rows only for now; INTENT for All Pick (hero picked for a role) can be added by the same table later.
- New `team_composition` (unique on match, team, stage): `stage` PICK or FINAL, `tokens` (comma separated), `summary` text, counts, `analysisVersion`.
- `PlayerMatchStatisticDomain.positionEst`: OpenDota's estimate, as a baseline.
- **Discord `DraftSection`** (Ability Draft only, the section already supports by mode) prints per focus player: hero, skills, "intended 1, played 3" with the profile difference in a few words, and the team's PICK verdict beside the FINAL verdict.

## 4. Extension points (the refactor)

- `RoleResolver` gains a stage: `resolve(RoleContext)` where `RoleContext` holds `RoleStage` (INTENT or FINAL) and the team's `RolePlayer`s. `AllPickRoleResolver` ignores INTENT and returns the same result as today (its tests must pass unchanged). A resolver is a `FunctionProfiler` (profile per player, per mode) plus a shared role fitter (`RoleFitter`, uses `RoleAssignmentSolver`); All Pick keeps its own code until it goes through the loop.
- `RolePlayer` gets the draft input: hero id and the drafted ability names (empty for All Pick), loaded by `PlayerRoleAnalyzer` from `AbilityDraftPickRepo`; `requires()` adds `ability-draft` only for that mode.
- `PlayerRoleAnalyzer` writes the FINAL role to `player_role` and both stages to `player_role_profile`; a new `TeamCompositionAnalyzer` (`@Order(16)`, `ReplayAnalyzer`, mode-specific verdict rules behind a `CompositionRules` interface registered per mode) writes `team_composition`.
- A `ReasonContributor` (plan section 7) reads roles and the composition tokens later; this document only guarantees the tokens are stored as strings it can match (`NO_INITIATOR`, `NO_DISABLE`, `CARRIES_3`, ...). Existing assessors that will consume roles: `FightAssessor` (absence is a fault for a core, tolerated for a lane-holding 5), `FightAbilityAssessor`, `DeathAssessor` (`OUT_OF_POSITION`, BKB expectations), `OutlierAnalyzer` (share against the role's norm), `PhaseSummaryAnalyzer` ("carry in the jungle"), `SpikeAnalyzer`. None changes in the preparation PRs.

## 5. The learning loop (replaces hand labelling and bulk calibration)

The algorithm is not tuned against a labelled data set; it is shaped by review rounds on a small fixed list, because the goal is to match what a Dota player would say about these games.

**Pieces (built in PR 5d)**

- `server/src/test/resources/replay/role-loop/matches.txt`: match ids only, one per line, Ability Draft first. Starts with 9038725154 and 9037038717; the owner adds more. The stored tables are the source (a replay is needed once to store a match, never again; replays vanish after about 3 months, stored facts do not).
- A Gradle task `:server:roleLoop` (JUnit tag `roleloop`, local only, never in CI, same style as `replayTest`) against the local PostgreSQL. For each id it calls the same path as the reanalyze endpoint (`ReplayAnalysisService.analyze(matchId, mode)`, no replay), then reads the stored tables and writes `build/role-loop/round-NN/<matchId>.md` and `summary.md`. It reads stored rows only, so a rule change plus the task is a few seconds per match.
- The per-match report: header (hero list, result, duration); per player: hero, drafted skills, **intent role and final role**, scores per role (five numbers each stage), the nine profile values, evidence text, OpenDota `position_est` and net worth rank for comparison; per team: PICK and FINAL composition verdict tokens with their texts.
- `docs/superpowers/plans/role-loop/round-NN.md` (committed): one table per round, one row per player, `match | slot | hero | intent | final | expected | assistant | owner | comment`, where assistant and owner are `OK` or `REJECT` and `expected` is filled for a reject. A second small table lists the team verdicts as `OK` or `REJECT`.

**Rounds**

1. Run the task on the list, the assistant reviews the reports and fills the `assistant` column.
2. The assistant changes weights, rules or tags to fix the rejects and notes the change in the round file; re-run.
3. The owner reviews the new reports, fills the `owner` column.
4. Change, then the assistant reviews, then the owner again, until the owner accepts. Each change must keep earlier accepted rows accepted (the task prints rows that changed since the previous round, so a regression is visible).
5. Matches are added to the list as they come (the goal is to improve the algorithm from the latest few matches), and the last accepted round is the baseline for the next.

**"Accepted"** means the owner has marked a round where, for every match on the list, the final role of every player and the team verdicts are `OK` or an explained tolerated difference, and the intent rows read plausibly. The owner decides; a suggested bar is no rejected position 1 or 5 and at most one adjacent-role reject per match.

Ability Draft goes first. When it is accepted, the same task and tables run on All Pick matches and the existing `AllPickRoleResolver` is adjusted in the same way.

## 6. PR split

Preparation and refactor only:

| PR | Content |
|---|---|
| 5a | Widen `HeroConstant` and `AbilityConstant`; refresh the stored constants. Store `positionEst` on `PlayerMatchStatisticDomain`. Mapper tests on real OpenDota JSON. |
| 5b | `RoleStage`, `RoleContext`, `FunctionProfile`, `RolePlayer` with draft input, `RoleResolver` contract, `RoleFitter`; `player_role_profile`; `PlayerRoleAnalyzer` writes both. All Pick results and tests unchanged. |
| 5c | `TeamCompositionAnalyzer`, `CompositionRules` per mode, `team_composition`; `DraftSection` prints intent, final and the verdicts. Rules start as a first hypothesis version. |
| 5d | Loop tooling: `matches.txt`, `:server:roleLoop`, the per-match report writer, the round file template, the changed-rows diff. |
| 5e | The first hypothesis Ability Draft resolver (`AbilityProfiler`, tag overrides, weights file) so round 1 has something to review. |

Then the loop (process, not PRs; each accepted change is a small PR on the same branch of work), Ability Draft, then All Pick. `ReasonContributor` stays plan PR 6.

Order: 5a, 5b, 5c, 5d, 5e. Nothing is merged or switched on in production without the owner's word; PRs 5a to 5c add tables, so a `pg_dump` comes first.

## 7. What changes in the existing code

- `service/constant/data/HeroConstant`, `AbilityConstant`: the missing fields; `BaseApiConstantService` stored map refreshed; `ConstantsManagers` unchanged in shape.
- `dao/model/PlayerMatchStatisticDomain` and `PlayersMapper`: `positionEst`.
- `analysis/role/RoleResolver`: takes a `RoleContext` with a stage; `AllPickRoleResolver` adapted, behaviour unchanged.
- `analysis/role/RolePlayer`: hero id and drafted abilities; `PlayerRoleAnalyzer` loads them (`AbilityDraftPickRepo`), writes `player_role_profile`, version bump, `requires()` per mode.
- New: `FunctionProfile`, `RoleStage`, `RoleFitter`, `AbilityProfiler`, `AbilityDraftRoleResolver`, `PlayerRoleProfileDomain` and repo, `TeamCompositionAnalyzer`, `CompositionRules` (and the Ability Draft implementation), `TeamCompositionDomain` and repo.
- `report/DraftSection`: intent versus final and the composition verdicts, Ability Draft only.
- `server/build.gradle`: the `roleLoop` task; test resource `role-loop/matches.txt`; committed round files.
- `CLAUDE.md` "Replay pipeline": the stage concept, the two new tables, the loop.
- Untouched until the loop accepts: the death, fight, outlier and phase assessors and the `ReasonContributor`.
