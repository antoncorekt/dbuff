# Replay: Ability Draft role resolver (design for PR 5)

Status: design, nothing built. Scope: section 6 of `2026-10-12-replay-priority-and-game-modes-plan.md` ("Ability Draft resolver"). The All Pick resolver, the `player_role` table, `PlayerRoleAnalyzer` and the `RoleResolver` strategy are built by PR 4; this document only adds the Ability Draft strategy and the data it needs. Written against `main` at da67d20.

## 0. The problem in one paragraph

In All Pick the hero is picked for a role, so the role is almost a lookup. In Ability Draft the hero is a body (attack type, base stats, 2 to 4 abilities that are mostly replaced) and the role is made by the 4 skills drafted. A Sniper with Riki's Blink Strike and Death Prophet's Exorcism is not a carry. So the resolver answers: **which of the five roles did each of the 10 players actually play, and was that the role the team needed**. The product use is the owner's: "the team lost a lot of gold after a lost fight: why was it lost, and why did that cause happen". Role is the join key between a fight failure ("carry in the jungle", "no initiation") and its cause in the draft ("nobody on the team drafted a disable").

Two different questions must stay separate, because mixing them makes the explanation circular:

- **Intended role** (`intent`): what the hero plus the chosen skills are good for. Known from the draft alone.
- **Played role** (`played`): what the player did, from lane, farm and net worth. Known only after the fact.

The stored role is the combination (section 2.4); both parts are kept in the evidence so a reason such as "the best farmer in the draft was played as a roamer" stays expressible.

## 1. Inputs: what exists and what is missing

### 1.1 Exists today

| Need | Where it is | Notes |
|---|---|---|
| Who took which skill, in what order | `AbilityDraftPickDomain` (40 rows per match): `pickOrder`, `playerSlot`, `team`, `teamPickIndex`, `abilityId`, `abilityName`, `finalAbilitySlot` (0-2 regular, 3 ultimate), `ultimate`, `secondsSincePrevPick`, `poolRemainingBeforePick` | Written by `AbilityDraftExtractor` for mode 18 only. Pick order tells what each player valued early (first picks are usually ultimates). |
| The pool | `AbilityDraftPoolDomain` (48 rows): `poolIndex`, `abilityName`, `pickedBySlot`, `pickOrder`; `ultimate` is known for picked rows only | The 8 skipped skills show what nobody wanted; useful only as a sanity check. |
| Hero of each player | `PlayerMatchStatisticDomain.heroId`, `heroName`, `heroVariant`; `ReplayPlayerDomain.heroEntityName` | |
| Behaviour | `PlayerMatchStatisticDomain`: `lane`, `laneRole`, `laneEfficiency`, `goldAt5/10/15/20Min`, `lastHitsAt*`, `xpAt*`, `netWorth`, `heroDamage`, `heroHealing`, `stuns`, `teamfightParticipation`, `obsPlaced`, `senPlaced`, `campsStacked`; `PlayerTimelineDomain` (net worth over time); purchases in the OpenDota detail tables | OpenDota data, available even without a replay. |
| Behaviour from the replay | `PlayerActivitySegmentDomain` / `PlayerPhaseSummaryDomain` (jungle, lane, traveling, teleporting, in base, positioned seconds per phase), `FightPlayerDomain.presence`, `FrontlineRanker` rank, `ReplayStateChangeDomain` (items gained/lost, cooldowns, skill levels) | `ActivityAnalyzer` is `@Order(10)`, so the role analyzer sits after it and before fights (20), i.e. `@Order(15)`. |
| Mode switch and ordering | `GameModeProfile`, `ReplayAnalyzer.supports(mode)`, `requires()` | The resolver is a bean of the Ability Draft mode. |

### 1.2 What the constants give today, and why it is not enough

`ConstantsManagers` caches `getHeroConstantMap()`, `getAbilityConstantMap()` (id to name), `getHeroAbilitiesMap()` (hero to its original ability names), `getAllAbilityConstants()`, items, patch, match types.

The Java classes keep almost nothing of what OpenDota sends:

- `HeroConstant`: `id`, `name`, `localized_name` only.
- `AbilityConstant`: `dname` only.
- `BaseApiConstantService` converts the response to these classes and **saves the converted map** to `ConstantDomain`, so fields we do not model are dropped from storage too, not only from the object.

OpenDota's raw data has (checked against `/api/constants/heroes`, `/abilities`, `/hero_abilities`, 2026-10-12):

- Heroes (127): `roles` (coarse list such as Carry, Support, Nuker, Disabler, Durable, Escape, Initiator, Pusher, Jungler), `primary_attr`, `attack_type`, base health, mana, armor, magic resistance, attack damage min and max, attack rate, attack range, projectile speed, move speed, base and gain per attribute, `legs`, vision.
- Abilities (3084 entries, 1334 with detail): `behavior` (Passive, Unit Target, Point Target, No Target, AOE, Channeled, Autocast, Attack Modifier, Instant Cast, Hidden; string or list), `dmg_type`, `bkbpierce`, `dispellable`, `target_team`, `target_type`, `cd` and `mc` per level, `dmg` (74 entries only), `is_innate`, `attrib` (a list of `{key, header, value[]}` such as `bonus_damage`, `stun_duration`, `slow`, `abilitycastrange`), `desc`.
- `hero_abilities`: per hero the 4 to 6 abilities, then talents.

### 1.3 Missing

1. **Wider constant classes**: `HeroConstant` (roles, attr, attack type, base stats) and `AbilityConstant` (behavior, dmg type, targets, bkbpierce, cd, mc, dmg, attrib). Not a new fetch, a wider mapping; the stored `constant` rows are refreshed once when the version changes (the existing constant refresh path; to be confirmed in PR 5a).
2. **No "is ultimate" or "is a disable" or "is a blink" in OpenDota.** Ultimate comes from our pick rows (slot 3) and from the last ability of a hero in `hero_abilities`. Everything else is derived by us (section 2.1).
3. **Draft is known only for replay-processed matches** in `AbilityDraftPickDomain`. For matches with no replay the skills can still be read from OpenDota `ability_upgrades_arr` (stored in `AbilityUpgradeDomain`), which lists learned skills only, so a skill never levelled is invisible. Phase 1 resolves roles only for replay-processed matches.
4. **Role-relevant OpenDota fields not stored today**: `position_est` (see 3.2), `is_roaming`, `lane_pos`. `lane_role` and `lane` are stored.
5. Aghanim's, talents and shard effects, and item builds are out of the first version (second-order, noisy).

## 2. The scoring model

### 2.1 Ability and hero features

Each skill becomes a vector of 10 features in 0..1, computed by a pure function `AbilityProfiler` from the widened constants and cached by ability name. Each feature has a rule and a checked-in override file `ability-tags.json` (about 600 skills are in the Ability Draft pool; the rules are expected to be right for most and the override file fixes the rest, reviewed once and extended per patch).

| Feature | Rule from OpenDota fields (starting point) | Used for |
|---|---|---|
| `farm` | AOE or No Target damage with `cd <= 12` and `mc` small relative to mana pool; summons, illusions, split attacks, attack modifiers with cleave or multi-target | carry, mid |
| `scaling` | Attack Modifier or Passive whose `attrib` keys match `bonus_damage|crit|attack_speed|lifesteal|armor_reduction|damage_per_stack`; plus the hero's base `attack_rate` and `attack_range` | carry |
| `burst` | summed `dmg` or `damage` attrib at level 1 to 4 divided by `cd` and `mc`, for Unit/Point target nukes | mid, soft support |
| `control` | `attrib` keys `stun|silence|root|hex|sleep|fear|taunt|disarm|cyclone|slow`; value is duration in seconds, discounted by `cd` and by `dispellable`; `bkbpierce=Yes` raises it | offlane, supports |
| `initiate` | control combined with `AOE` target or a gap closer (`blink|leap|charge|teleport` in name or attrib) on the same hero | offlane |
| `mobility` | blink, leap, speed, invisibility, phase, escape keywords; ally-targeted gap closers count for `save` too | all, mid and soft support most |
| `durability` | heal, barrier, damage block, armor, `bonus_health|bonus_strength`, plus the hero's base health and strength gain | offlane |
| `save` | `target_team` includes friendly and `heal|shield|barrier|invuln|save|dispel|lifesteal_aura` | hard support |
| `roam` | global or long range, teleport, invisibility, low `mc`, level-1 usable control or nuke | soft support |
| `itemDependence` | share of the 4 skills that are Attack Modifier or passive stat scaling and not castable | pushes toward carry, pulls away from supports |

The ultimate (slot 3) gets weight 1.5, regular skills 1.0. Hero features: `rangedBonus`, `primaryAttr` (agility favors 1/2, strength 3, intelligence 2/4/5), `durability` from base health and armor, `attackRate` and `attackRange` for `scaling`, and OpenDota `roles` as a weak prior (0.15 per matching role label: Carry to 1, Nuker to 2, Durable and Initiator to 3, Support and Disabler to 4 and 5).

### 2.2 Intent score per role

`intent(p, r) = sum over features f of W[r][f] * F(p, f)`, where `F(p, f)` is the weighted sum over the player's 4 skills plus the hero feature, clipped to 1. Starting weights (to be tuned, section 3.3):

| Role | Positive | Negative |
|---|---|---|
| 1 Carry | `scaling` 0.30, `farm` 0.30, `mobility` 0.10, `durability` 0.10, `burst` 0.05, ranged or agility hero 0.15 | `save` 0.15, mana-hungry kit (sum `mc` / base mana) 0.10 |
| 2 Mid | `burst` 0.30, level-1 power (`burst` and `control` at level 1) 0.20, `farm` 0.15, `mobility` 0.15, `control` 0.10, intelligence hero 0.10 | `itemDependence` 0.15 |
| 3 Offlane | `durability` 0.25, `initiate` 0.25, `control` 0.15, `mobility` 0.15, aura or damage-block skills 0.10, strength hero 0.10 | `itemDependence` 0.10 |
| 4 Soft support | `roam` 0.25, `control` 0.25, `burst` at level 1 0.15, `mobility` 0.15, `save` 0.10, cheap kit (low `mc`) 0.10 | `farm` 0.15, `scaling` 0.15 |
| 5 Hard support | `save` 0.30, `control` 0.20, utility from skills that do not scale with items 0.20, low `itemDependence` 0.20, long-range or global effect 0.10 | `farm` 0.20, `scaling` 0.20 |

Every score is normalized per role over the whole hero and skill population (z-score against the 127 heroes with their original kits) so a role is not favored because its weights sum higher.

### 2.3 Behaviour score per role

The behaviour score is read from what the player did, so a perfectly "supportive" kit played as a farmer is shown as a farmer.

- **Economy rank in team** at 10 and 20 minutes and at the end, from `goldAt10Min`, `goldAt20Min`, `netWorth`: rank 1 and 2 map toward roles 1 and 2, rank 5 toward 5, with a Gaussian match `exp(-(rank - expected)^2 / 2)`. Net worth rank agrees with the OpenDota lane based position only 37 of 90 times in the 9 Ability Draft matches we hold (section 3.2), which is why the lane alone cannot be the answer here.
- **Lane fit**: `laneRole` (1 safe, 2 mid, 3 off, from OpenDota, relative to the team) gives 0.7 to the matching role, 0.15 to the others; the roaming flag moves the 0.7 to roles 4 and 5.
- **Jungle**: `jungleFarmSeconds / (laneFarm + jungleFarm)` in the 0 to 15 minute phase; a high share is evidence for a farmer role (1 to 3), never for 4 and 5, and is also the "carry in the jungle" input (that reason belongs to the assessors, not to the role).
- **Support signals**: `obsPlaced + senPlaced`, `campsStacked`, wards, dust, smoke and courier in the purchase log, `heroHealing`, low last-hit share. They split role 4 from 5 (4 has more kills and assists, 5 has more wards and the lower net worth).
- **Fight presence**: `fightsJoined / fightsAvailable`, front-line rank from `FrontlineRanker`: front line is evidence for 3, back line for 1 and 5.

`behaviour(p, r)` is a weighted sum of these terms with starting mix: economy 0.40, lane 0.25, jungle and farm 0.10, support signals 0.15, fight position 0.10.

### 2.4 Team demand and the combination

Demand answers "what did this team lack". For a team the capacity vector `C` is the sum of the five players' features (clipped). Each function has a target `T` (starting: `farm` 1.0, `scaling` 1.2, `control` 1.5, `initiate` 0.8, `durability` 1.0, `save` 0.6, `roam` 0.6, in team units). `D = max(0, T - C)` is the shortfall.

`score(p, r) = a * intent(p, r) + b * behaviour(p, r) + c * demand(p, r)`, with `a = 0.40`, `b = 0.45`, `c = 0.15`, and `demand(p, r) = sum_f W[r][f] * D[f] * F(p, f)`: a player is pulled toward the role that needs exactly what the team lacks and what the player's kit can give.

The five players of a team are then assigned one role each by maximizing the total score over the 120 permutations (or the Hungarian algorithm; the numbers are tiny). One hero per role is the same constraint All Pick has, which keeps the two modes' output comparable. If the team plays a real double-core or no-support lineup, the assignment will still give a role to everybody and say so in the evidence (low margin, "no hard support in the draft").

### 2.5 Confidence

For each assigned player: `confidence = 0.45 * margin + 0.35 * agreement + 0.20 * completeness`, clipped to 0.10 to 0.95, where

- `margin`: (best total assignment score minus the best total score when this player is forced out of the assigned role) divided by the best total, scaled so that 0.15 means 1.0; a coin flip between two roles gives about 0.
- `agreement`: 1 minus the normalized distance between the rank of the assigned role in `intent` and in `behaviour` (both agree on the role: 1; the kit says carry and the play says support: 0).
- `completeness`: share of inputs present (draft rows 4 of 4, net worth timeline, activity segments, purchases).

Consumers treat `confidence < 0.40` as unknown: they do not use the role to explain anything, but the report may still print it with a "?".

## 3. Calibration data and thresholds

### 3.1 How many matches, from where

| Source | Size | Use |
|---|---|---|
| Two Ability Draft replays (9038725154, 9037038717), full replay facts | 2 matches, 20 players | end-to-end test fixtures, worked examples |
| OpenDota match JSON for Ability Draft matches already fetched (the owner's tracked history: 9 Ability Draft and 1 All Pick match JSON in the spike scratch; `ad.json` lists 30 Ability Draft matches of the tracked account) | 30 matches, 300 players | gold set, **hand labelled by the owner** (open question 1) |
| OpenDota Explorer SQL (`/api/explorer`, game_mode 18, current patch, parsed matches): `player_matches` joined to `matches`: hero, `lane_role`, `net_worth`, `gold_t`, `purchase_log`, `ability_upgrades_arr`, `position_est` | 300 to 500 matches, 3000 to 5000 players | weak labels for tuning the weights and thresholds; no replay needed because intent needs skills only (learned skills) and behaviour uses the OpenDota fields |
| Production DB | unknown, read-only count of Ability Draft matches with replay facts | extra check, only on the owner's word |

### 3.2 The weak label

- **`position_est`** (1 to 5) is in the OpenDota match JSON of every one of the 10 fixtures, perfectly shaped (each team has 1 to 5 exactly once), and **is not stored by us today**. In the 9 Ability Draft matches it matches `lane_role` almost always (89 of 90: position 1 safe lane, 2 mid, 3 off lane, 4 and 5 the supports) but agrees with the in-team net worth rank only 37 of 90 times (41%). So it is a lane label. It is a good baseline ("what would the old rule say") and a poor ground truth: that gap is the reason this resolver exists and it is the figure to beat, as measured against the gold set.
- **Realized-role weak label** for the 3000+ rows: `1` = highest net worth in team and lane role 1 or last hits in the top 2; `5` = lowest net worth with at least 3 wards or no farming; others by lane. Used only for tuning, with the known bias (a feeding carry looks like a support).
- **Gold set**: the owner labels 30 matches (300 players) as "role actually played" in a spreadsheet; 10 minutes per match with the report open. This is the only real ground truth.

### 3.3 Thresholds to tune

Weights in section 2.2 and 2.3 (grid on the weak set, then verified on the gold set), mix `a/b/c`, demand targets `T`, `margin` scale (0.15), the confidence cut (0.40), the jungle share counted as "jungle role" (0.35 of farm seconds in 0 to 15 minutes), the roaming cut (more than 30% of 0 to 10 minutes outside the home lane), ultimate weight 1.5.

Acceptance (proposal): exact role match on the gold set at least 70% against 41% to 60% for the lane or net worth baselines; within one position at least 92%; among rows with confidence at least 0.70, correct at least 85%; role 5 and role 1 precision at least 80% each (they matter most for the reasons).

## 4. Output contract

Same table and rows as All Pick (`player_role`, PR 4): `matchId`, `playerSlot`, `role` 1 to 5, `source = DERIVED`, `confidence` 0 to 1, `evidence` string, plus the analysis version. No new column is required. Ability Draft adds a standard evidence format (one string, `key=value` separated by `; `, so it stays queryable and printable):

```
intent=2(0.71) played=1(0.64) top3=[2:0.71,1:0.55,4:0.31] margin=0.09 demand=[scaling:0.8,control:0.5]
kit=[farm:0.8,scaling:0.1,burst:0.7] nwRank=1 lane=2 jungle10=0.05 team=NO_HARD_SUPPORT;LOW_CONTROL
```

- `intent` and `played` are the best role by each part; if they differ, the evidence records it, which is the sentence the owner wants ("drafted a mid kit, played the safe lane").
- `team=` carries the demand shortfalls (`NO_HARD_SUPPORT`, `LOW_CONTROL`, `NO_FRONTLINE`, `NO_FARM_ENGINE`) as tokens that reason contributors can match.
- The per-role score breakdown stays in the evidence string; no extra table.

The re-analysis path is unchanged: roles are recomputed from stored facts when the analyzer version changes.

## 5. How mode-specific reasons plug in, and which assessors use roles

Plan section 7: common reasons stay in the shared assessors; a `ReasonContributor` per mode adds the rest. Concretely:

```
interface ReasonContributor { boolean supports(GameModeProfile m);
                              List<Reason> explain(FightContext ctx, PlayerRoles roles); }
```

Contributors are beans, called by the assessors after the common rules, results are stored as strings (no schema change, as the plan says). The Ability Draft contributor, in the order of the owner's example for a lost fight with a large gold swing (`SpikeAnalyzer` spike, FIGHT factor, `FightDomain` rows):

1. **Fight lost because of the lineup**, from role evidence (`team=` tokens plus the fight): `NO_CONTROL` (our side landed fewer disable seconds, `FightAbilityUseDomain`), `NO_INITIATION`, `NO_FRONTLINE`, `WEAK_ABILITY_COMBINATION` (the five kits' capacity `C` far below the enemy's on the function that decided the fight).
2. **Why that happened**: the draft cause from pick rows: `secondsSincePrevPick`, who took the last useful skill, whether a needed skill was in the pool and skipped (pool rows), whether the first picks were ultimates.
3. **Why a particular hero was missing** (with role): `CARRY_IN_JUNGLE` (assessor, needs role 1 and activity `JUNGLE_FARM` at the fight time), `SUPPORT_FARMING`, `ROLE_SKIPPED` (the demand said hard support, nobody played it).

Existing assessors that consume roles after PR 4 and PR 5:

| Assessor | What the role adds |
|---|---|
| `FightAssessor` (`AbsenceReason`: `NO_TP`, `TP_ON_COOLDOWN`, `HAD_TP_NOT_USED`) | absence is a fault for roles 1 to 3, tolerated for a lane-holding 5 |
| `FightAbilityAssessor` (`NonCastReason`) | a core not casting is worse than a support not casting; weights by role |
| `DeathAssessor` (`OUT_OF_POSITION`, `NO_BKB`, `BKB_READY_UNUSED`) | position expectation by role (front line for 3, back line for 1 and 5); BKB expectation by role |
| `OutlierAnalyzer` | a net worth share is an outlier relative to the role's norm, not the team's |
| `PhaseSummaryAnalyzer` / `PhaseSection` | "carry spent 40% of minutes 0 to 15 in the jungle" phrased with the role |
| `SpikeAnalyzer` | the swing's player chain names the role |
| `DraftSection` | prints each player's role and the intent versus played line |

"No vision" remains blocked on wards, as the plan says.

## 6. Risks and open questions

Risks:

- **Circularity**: net worth is a result of the role and the match; a feeding carry looks like a support. Mitigation: intent and played are kept apart and agreement is part of the confidence. Roles are labelled as "played", not "intended".
- **Ability tags decay**: every patch changes skills; the override file needs a patch check (the `PatchConstant` exists; compare the skill list hash and flag untagged skills at startup).
- **Narrow gold set**: 30 matches of one account's lobbies, so mid-skill games with the owner's habits dominate.
- **Constants refresh**: widening the classes changes the stored `constant` rows; roll out with the refresh; PR 5a adds two columns, so a `pg_dump` comes first as for any table change.

Open questions for the owner:

1. Can you label "role actually played" for 30 Ability Draft matches (about 5 hours)? Without that the model is tuned on a weak label only; I would then ship with a lower acceptance bar (60% exact).
2. Which role should the resolver call a hero that really plays two (a Sniper-like core with strong control)? Proposal: one role per player (the five-role assignment), and the second role in the evidence only. Or do you want multi-role rows?
3. Do you want resolved roles for Ability Draft matches without a replay (skills from `ability_upgrades_arr`, behaviour from OpenDota only)? It widens the data for stats but the skill order hides never-levelled skills; proposal: no, replay matches only.
4. Is "intended versus played" in the Discord draft section wanted, or only the final role?

## 7. PR split

| PR | Content | Tests |
|---|---|---|
| 5a | Widen `HeroConstant` and `AbilityConstant`, constants refresh; store `position_est` and `is_roaming` on `PlayerMatchStatisticDomain` (Hibernate `ddl-auto=update`, T: two columns) | mapper tests on the real OpenDota JSON of the fixtures |
| 5b | `AbilityProfiler`, `ability-tags.json`, hero features; pure functions, no DB | unit tests per feature with named skills (Blink Strike, Exorcism, Shrapnel) |
| 5c | `IntentScorer`, `BehaviourScorer`, `TeamDemand`, assignment and confidence; `AbilityDraftRoleResolver` behind the PR 4 `RoleResolver` | the two replay fixtures; golden roles checked by hand; property test: each team gets roles 1 to 5 once |
| 5d | Calibration tool (a Gradle `JavaExec` or test-scope main outside the app), explorer export, gold-set import, report of agreement and confidence calibration; tuned numbers committed as `role-weights.yml` | runs only locally; CI tests the loader |
| 5e | `ReasonContributor` interface and the Ability Draft contributor (mode reasons) and the assessors' use of roles; `DraftSection` role line | reasons on the fixtures; this is PR 6 of the plan and may be merged into it |

Order: 5a, 5b, 5c, then 5d (tuning needs the scorer), 5e last. Nothing is merged or switched on in production without the owner's word.
