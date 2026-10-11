# dbuff — Replay parsing research & analytics gap analysis

## Context
dbuff currently builds all Ability Draft (AD) / All Pick (AP) analytics from OpenDota's `/matches/{id}` JSON (+ optional Dotabuff scraping). That data has no AD draft information (pick order, pool, what was skipped), and parts of what OpenDota *does* return are discarded by our mappers. The goal: understand what we report today, what data we hold, what a full replay parse would add, and define the target DB shape for full draft/lane/game analytics.

Deliverable of the implementation step: this research written to `docs/superpowers/specs/2026-10-11-replay-parsing-research.md` (in a worktree `dbuff-docs-replay-research`, branch `docs/replay-research`), plus a spike to confirm the one unverified item (AD draft fields in replay).

---

## 1. Reports dbuff already produces

**Per-match report** (`service/match/report/MatchReportOrchestrator` → analyzers, Discord thread):
| Analyzer | What it reports | Data used |
|---|---|---|
| `ability/ChosenAbilityAnalyzer` | For each skill the player had: their W/L + WR with that skill over 12 months | `ability_domain` × `player_match_statistic_domain.win` |
| `ability/AbilityHistoryStatAnalyzer` | This game's damage/uses per skill vs player's historical avg | `ability_domain.damageDealt/useCount` |
| `item/ChosenItemAnalyzer`, `ItemHistoryStatAnalyzer`, `ItemEfficiencyAnalyzer` | Same for items + efficiency | `item_domain` |
| `damage/DamageHistoryAnalyzer` | Hero/tower damage vs history | player stats |
| `lane/LaneAnalyzer` | Lane matchups (bot/mid/top), gold+xp @10 per side, lane kills/deaths ≤10 min, early items | `lane`, `goldAt10Min`, `xpAt10Min`, kill log |
| `OtherPlayerAnalyzer` | Other 9 players: skills by damage, items by time, dmg/heal | abilities, items |
| `handlers/AiSummaryReportHandler` | OpenAI summary of the match | all of the above |

**Discord commands** (`service/discord/command/impl`): `/stats overall|heroes|items|skills` (incl. skill+item combo WR via `AbilityRankingRepository.findAbilityComboStatistics`), `/hero` (hero record + top items/skills), `/scout player|scoreboard` (OCR opponents → OpenDota stats), `/match rerun|retry`, `trace_matches` option; scheduled weekly/monthly/half-year summaries.

**AD-specific today**: skill pick rate, skill WR, skill-combo WR, skill-on-hero WR, per-skill damage/uses — all *per tracked player*, all *outcome-only* (no draft context).

## 2. What the Dota API (OpenDota) gives and what we keep

Flow: `RecentMatchesJob` → `DotaApiMatchDetailsService` (`GET /matches/{id}`) → `PlayersMapper` / `BasicMatchDetailsMapper` → DB. `DotaApiParseRequestService` POSTs `/request/{id}` and `MatchParseCheckJob` polls `od_data.has_parsed`.

**Stored** (tables): `match_domain` (duration, scores, winner, patch, mode, FB time), `player_match_statistic_domain` (~60 end-game fields + gold/xp/lh @5/10/15/20, lane/lane_role/lane_efficiency, hand damage), `ability_domain` (skills + dmg/uses), `item_domain`, `kill_log_domain`, `player_statistic_summary`, `match_analysis_domain`.

**Returned by OpenDota but dropped / degraded:**
- `picks_bans`, `draft_timings` — AP pick/ban order, team, time → **not stored**.
- `ability_upgrades_arr` — converted to a `Set` in `PlayersMapper.java:303` → **skill build order lost**, talents filtered out.
- `gold_t`, `xp_t`, `lh_t`, `dn_t` — full per-minute series, we keep only 4 points.
- `radiant_gold_adv`, `radiant_xp_adv`, `teamfights`, `objectives` — not stored.
- `damage_targets`, `ability_targets`, `killed_by` — who hit whom with what → not stored.
- `lane_pos`, `is_roaming`, `buyback_log`, `runes_log`, `obs_log/sen_log`, `permanent_buffs`, `randomed`, `party_id`, `cluster`, `replay_salt`, `replay_url`.
- `purchase_log`: `item_domain` PK `(itemId, matchId, playerSlot)` keeps only one purchase per item.

**Not available from OpenDota at all (needs replay):** AD draft pick order, the AD ability pool (incl. abilities nobody took), per-pick timing, ability level-up timestamps per level, per-second positions, cast events with targets/time, modifiers (stuns/slows applied), per-lane actual partners from movement.

## 3. Research findings

### 3.1 Getting the replay
- URL: `http://replay{cluster}.valve.net/570/{match_id}_{replay_salt}.dem.bz2`. `cluster` + `replay_salt` (and `replay_url`) appear in OpenDota's match response **after** the match is parsed — we already request parsing, so we can read them in `MatchParseCheckJob`.
- Valve retention is unofficial; measured ~3 months for the sampled matches (see the plan). → Download after the parse check succeeds and backfill matches up to 90 days old.
- Fallback without OpenDota: fetch salt from the Dota GC with a Steam account (JavaSteam + Dota GC `CMsgGCMatchDetailsRequest`). More moving parts; only if OpenDota proves unreliable.
- Size ~30–100 MB `.bz2`; stream-decompress, parse, discard. Don't store raw replays.

### 3.2 Parsing library — recommendation: **Clarity** (`com.skadistats:clarity:5.0.1`)
- Java, BSD-3, requires Java 21 → matches our toolchain (`build.gradle` `languageVersion 21`); runs in-process inside the Spring app, no extra service.
- Supports combat log, entities (heroes, players, game rules), modifiers, user messages, game events, string tables, end-of-game overview.
- It is what OpenDota's own parser (`odota/parser`, Java HTTP service on :5600, POST `.dem` → line-JSON) is built on. Their `Parse.java` is the best reference for which entity properties to read: `m_vecPlayerTeamData` (hero, level, KDA, randomed, facet), `m_vecDataTeam` (gold, xp, lh, dn, networth, stuns…), hero `m_vecAbilities.N`→`m_iLevel` (ability level changes), `m_hItems.N` (inventory), `CBodyComponent` (x,y), game rules (`m_nGameState`, `m_BannedHeroes`, `m_SelectedHeroes`, draft timings).
- Alternatives considered: `manta` (Go, Dotabuff) and running `odota/parser` as a Docker sidecar. Sidecar is a faster MVP but gives us no AD draft data either and adds a service; in-process Clarity is the better long-term fit.
- **Unverified / spike needed:** odota parser has *no* AD draft handling. AD draft state must be read from game-rules entity fields during the draft phase (pool, current drafter, picked ability per player) or derived from abilities appearing on hero entities in draft order. A spike dumps all `CDOTAGamerulesProxy` field changes during `DOTA_GAMERULES_STATE_HERO_SELECTION` for one AD replay to identify exact field names.

### 3.3 What is useful for analytics
**Ability Draft**
- Draft: pick number/round (1–4) per skill, pool contents, which skills were available but skipped, ult vs regular, hero assigned (random) + its innate/facet.
- Value of a pick: WR by pick position ("first-pick worthy"), pick rate *when available* (needs pool), skill × hero fit, skill pairs (synergy), skill vs enemy skill (counter), denial picks (taken by player whose build doesn't use it — low cast count).
- Player history ("prev status"): player's WR/damage/casts with that skill, with that combo, on that hero body.
- Execution: level-up order and timing, casts/minute, damage & stun contribution per skill, time-to-ult.
- Lane: actual lane + partners from positions 0–10 min, NW/XP/LH diff vs lane opponents at 5/10, lane deaths.
- Game arc: per-minute NW/XP curve, team gold adv, power spike timings (aghs/shard/BKB), WR by duration bucket ("late-game potential" of a skill set), teamfight participation and damage share.

**All Pick**: pick/ban order and phase, first/last pick, counterpick (hero vs hero WR, lane opponent WR), team composition, facet, plus the same lane/arc metrics.

## 4. Target data model (gap → new tables)

| Need | Have | Source | New/changed table |
|---|---|---|---|
| AP picks/bans | ✗ | OpenDota `picks_bans`, `draft_timings` | `draft_event(match_id, seq, kind HERO_PICK/HERO_BAN/ABILITY_PICK, team, player_slot, hero_id, ability_id, round, time)` |
| AD pick order | ✗ | replay | same `draft_event` (kind `ABILITY_PICK`) |
| AD pool / skipped | ✗ | replay | `ability_draft_pool(match_id, ability_id, is_ultimate, picked_by_slot NULL, pick_seq NULL)` |
| Skill build order | degraded (Set) | OpenDota `ability_upgrades_arr` (order) / replay (timing) | `ability_level_event(match_id, slot, ability_id, level, time)` incl. talents |
| Per-minute curves | 4 points | OpenDota `gold_t/xp_t/lh_t/dn_t`; replay adds networth, level, x,y | `player_timeline(match_id, slot, minute, gold, xp, lh, dn, networth, level, x, y)` |
| Team advantage | ✗ | OpenDota `radiant_gold_adv/xp_adv` | `team_timeline(match_id, minute, gold_adv, xp_adv)` |
| Lane result | approx (`lane` + @10) | replay positions + timeline | `lane_assignment(match_id, slot, lane, role, partner_slots, nw_diff_10, xp_diff_10, lh_10, deaths_10)` |
| Who hit whom with what | ✗ | OpenDota `damage_targets/ability_targets`, replay combat log | `damage_by_target(match_id, slot, inflictor, target_hero_id, damage, phase LANE/MID/LATE)` |
| Casts & stuns per skill | uses only | replay combat log + modifiers | add `cast_count, stun_duration, first_level_time` to `ability_domain` |
| Item purchases | first only | OpenDota `purchase_log` | `item_purchase_event(match_id, slot, item_id, time)` |
| Teamfights / objectives | ✗ | OpenDota `teamfights`, `objectives` | `teamfight`, `objective_event` |
| Facet, randomed, party | partial | OpenDota | columns on `player_match_statistic_domain` |
| Replay status | ✗ | — | `match_domain.replay_url, replay_salt, cluster, replay_parsed_at, replay_error` |

Granularity: per-minute (not per-second) timelines keep row count bounded (~10 × duration-min rows per match).

## 4b. Empirical OpenDota check (2026-10-11, player 208611215 "Лолец пастухов")
Pulled 1000 matches (`significant=0` is required, otherwise AD is hidden from `/players/{id}/matches`) and 11 full `/matches/{id}` payloads (9 AD, 2 AP).

| Question | Finding |
|---|---|
| Is parsed data worse for AD than AP? | **No.** Parsed AD matches carry the same per-player fields as AP: `ability_upgrades_arr`, `gold_t/xp_t/lh_t`, `purchase_log`, `kills_log`, `damage_targets`, `ability_targets`, `lane_pos`, `runes_log`, `teamfight_participation`, `lane_efficiency`, `teamfights`, `objectives`, `radiant_gold_adv`. AD even has extra: `networth_t`, `hero_damage_t`, `tower_damage_t`, `deaths_log`. |
| What AP has that AD lacks | Only `picks_bans` (AD returns `[]`), plus `stomp/comeback` vs `throw/loss` variants. |
| What neither has | `draft_timings` empty in both; `hero_variant` is 0 for all players in both; `rank_tier` often null. |
| Is AD draft data present at all? | **No.** `ability_upgrades_arr` only shows skills that were *leveled* (incl. talents and non-hero abilities like `ability_lamp_use`, `twin_gate_portal_warp`). Pick order, pool, skipped skills, and never-leveled picks are absent. |
| Parse reliability | AD parsed rate since 2025-08 = ~98-100% per month (we request parses). Before 2025-08: 0-45% (nobody requested). AP in the sample: 17/156 parsed overall. So "unreliable" = *unrequested matches are never parsed*, not AD-specific. Always request + verify `has_parsed`. |
| Replay availability | `replay_url` present for parsed matches. Download works (HTTP 200) up to ~3 months back: 2026-06-21 -> 404, 2026-07-03 -> 200; 2026-06-09, 2026-01, 2025-08 -> 404. |

Conclusions:
1. Phase 1 (OpenDota) is safe for **both** modes except `picks_bans` (AP only). Its value is mostly in AD: build order, per-minute curves, purchase log, damage targets, lane positions.
2. Only the replay can give AD draft order/pool. Replays older than ~3 months are lost -> any historical AD draft data starts from the day the pipeline goes live; the replay window argues for building Phase 3 early.
3. Reliability rule: treat every OpenDota field as optional; store `has_parsed` and per-source completeness flags (extend `MatchCompletenessService`) so analytics can exclude partial matches.
4. Stratz GraphQL might expose draft data - **unverified**, not checked (needs API token + schema explorer); optional alternative to the replay path.

## 5. Implementation roadmap (each phase its own worktree/branch)
- **Phase 0 - doc**: this file.
- **Phase 1 - OpenDota completeness (both modes)**: new tables/mappers for `picks_bans` (AP), ordered `ability_upgrades_arr` incl. talents, `*_t` series (+ AD-only `networth_t`), `radiant_gold_adv/xp_adv`, `purchase_log`, `damage_targets`, `teamfights`, replay `cluster/salt/url`. One focused mapper per table (no Flyway: Hibernate `ddl-auto=update` owns the schema). Add `has_parsed` + completeness flags; make mappers null-tolerant.
- **Phase 1b - replay-window capture (small, do early)**: store `replay_url/salt/cluster` for every parsed match now, so Phase 3 can backfill anything newer than ~3 months.
- **Phase 2 - spike**: one recent AD replay (e.g. 9038725154: `http://replay191.valve.net/570/9038725154_496163112.dem.bz2`) through Clarity; dump `CDOTAGamerulesProxy` / hero-ability entity changes during hero selection to find pool + pick fields. Also check whether OpenDota facet 0 can be read from the replay (`m_iHeroFacetKey`).
- **Phase 3 - replay pipeline**: `ReplayDownloadService`, `ReplayParseService` (Clarity, bounded executor), extractors (`AbilityDraftExtractor`, `TimelineExtractor`, `LaneExtractor`, `CombatExtractor`), triggered from `MatchParseCheckJob`; backfill job for matches inside the 3-month window.
- **Phase 4 - analytics**: `analyzer/draft` (pick value by round, skipped skills, synergy/counter), `/stats draft`.

Ask before any large refactor or full build/test run (token budget rule).

## Verification
- Phase 0: doc renders, all class/file references exist.
- Phase 1: unit tests for new mappers using a saved OpenDota JSON fixture; `./gradlew :server:test --tests '*Mapper*'`; run app locally against Postgres, `/match retry` one AD and one AP match, check new tables populated.
- Phase 2: spike prints for a known AD match: 40 picks in order with player slot, and pool of 48 (the 48+12 figure does not hold for the current patch); compare against the in-game scoreboard/replay.
- Phase 3: parse a match end-to-end, timeline row count ≈ 10 × minutes, lane NW diff @10 matches OpenDota's `gold_t` within tolerance; check parse time and heap.

## Sources
- [skadistats/clarity](https://github.com/skadistats/clarity) · [odota/parser](https://github.com/odota/parser) (`Parse.java`) · [Liquipedia: Ability Draft](https://liquipedia.net/dota2/Ability_Draft) · [hawk.live match-id guide (retention)](https://hawk.live/posts/dota2-match-id-guide) · [Steam forum on replay expiry](https://steamcommunity.com/app/570/discussions/0/558754259963453975)
