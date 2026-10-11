# Replay extraction and analytics plan (Groups 1-6)

Status: plan, not implemented. Written 2026-10-11 for an executing agent.
Companion docs (same branch `docs/replay-research`):
- `docs/superpowers/specs/2026-10-11-replay-parsing-research.md` - why Clarity, replay download, OpenDota gaps.
- `docs/superpowers/specs/2026-10-11-ability-draft-replay-spike.md` - Ability Draft fields, verified on 2 replays.

Read section 0 first. Sections 2 and 3 are the shared foundation; sections 5-10 are one per group; section 11 is
the consolidated DB design; section 12 is the work-package order; section 13 is the spike backlog.

---------------------------------------------------------------------------------------------------

## 0. How to execute this plan

**Project rules that bind the executor** (from the repo and the user's global rules):
- One git worktree per work package, named `dbuff-<branch>` next to `dbuff/`, branch `feature/<short-name>`; never
  edit `dbuff/` on `main`. Merging to `main` auto-deploys to production, so open a PR and stop; the user merges.
- SOLID and DRY. If duplication or a SOLID violation is the better option, stop and ask the user first.
- Comments only where the code cannot say it; log at INFO, DEBUG only when needed, every log line must earn its place.
- No AI attribution anywhere (commits, PRs, code). Commit identity is not configured: use
  `git -c user.name="Anton Kozlovskyi" -c user.email="antoncorekt@users.noreply.github.com" commit`.
- Ask before expensive actions: subagents, full test suites, large replay batches, broad sweeps.
- Schema is owned by Hibernate `ddl-auto=update` (Flyway is not on the classpath). New tables/columns need no
  migration script; the user takes a `pg_dump` backup before merging. Do not add Flyway.
- Local build quirks: Gradle must run on JDK 21 (`JAVA_HOME=~/.sdkman/candidates/java/21.0.0.0+35-tem`);
  `spotlessApply` crashes locally, so format with the standalone google-java-format 1.25.2 jar and put static imports
  after regular imports; `git push` over SSH is broken, push with
  `git -c credential.helper='!gh auth git-credential' push https://github.com/antoncorekt/dbuff.git <branch>`.
  CI (`Build, test, check formatting`) is the source of truth for formatting. See the user's memory note
  `dbuff-local-build-quirks`.
- The prod host is one `t4g.small` (arm64, 2 GiB RAM, JVM `-Xmx512m`, Postgres co-located). Any native dependency
  needs an aarch64 build; every design choice below is sized for that box.

**Method per work package:** read the section, run the listed spikes first, write code with tests, run only the
targeted tests, open a PR. Each extractor must be independently switchable and must never fail match ingestion.

**Base branch:** branch every work package from a freshly fetched `origin/main`, not from a local `main` that may be
stale (the PR #6 classes below only exist after it). SSH is broken here: fetch over https with
`git -c credential.helper='!gh auth git-credential' fetch https://github.com/antoncorekt/dbuff.git main`.

**Patterns to reuse** (already merged to `main`, PR #6):
- `MatchDetailMapper` + `MatchDetailService` (service/match/data): one bean per table slice, `replace()` is atomic,
  deletion is wired once. The replay pipeline should follow the same shape (`ReplayExtractor` beans).
- Surrogate `@GeneratedValue(SEQUENCE)` id + `@UniqueConstraint` on the natural key (batch friendly, no per-row SELECT).
  Avoid reserved words (`minute`, `time`, `value`, `key`, `type` break H2): use `gameMinute`, `eventTime`, ...
- `MatchRepo.updateDetailState` style targeted updates instead of saving stale entities.
- `DotaApiMatchDetailsService.fetchOnce`, `QuietHoursGuard.isServerQuietTime()`, Quartz global job pattern
  (`MatchDetailBackfillScheduler`).
- `io.hypersistence:hypersistence-utils-hibernate-60` is already a dependency: use it for `jsonb` and array columns.

---------------------------------------------------------------------------------------------------

## 1. What we want to learn (requirements, by group)

| Group | Questions it must answer |
|---|---|
| 1 Pick | Was the focus player first/second/... in the pick queue? What was the ability and hero pool? What did the focus player choose, and what did everyone else choose (for counter/synergy analytics)? |
| 2 Early game | What happened before and around 0:00? Who got the runes? Who went to which lane and how did each lane go? How were the first 5/10/15 minutes (kills, creeps, XP, gold)? Wards, stacks, courier kills and other early events: which matter most? |
| 3 Fights | Find all fights. For each focus player, what happened in the 1-2 min before each fight, and why were they missing from it (no TP, farming, dead, far)? Why did abilities go unused (mana, cooldown, spent on creeps)? Was a non-support in the front line? |
| 4 Movement | Tower kills; where each player spent time (lane until ~12 min, jungle ~12-25); relate that to fights; behaviour when the team is winning, losing or even. |
| 5 End game | Why did a carry die in the late fights (was BKB owned/ready, did a disable pierce it)? Position of the focus group. Buybacks. |
| 6 Post-process | We know the winner and the gold/XP curves: find the spikes in the gold/XP difference, any change of direction, and the cause of each (a fed hero, a key item, a fight, an objective). |

Everything is stored **per game, for all 10 players**. "Focus group" (the tracked players of a Discord instance) is
applied at report time, never at parse time, because the focus group changes and replays expire after about 3 months.

---------------------------------------------------------------------------------------------------

## 2. Verified replay facts (2 Ability Draft replays: 9038725154, 9037038717)

All of this was read with Clarity 5.0.1 on JDK 21. Items marked (verify) were not proven and have a spike in section 13.

### 2.1 Clock, ticks, identity, coordinates
- 30 ticks per second. Tick of `m_pGameRules.m_nGameState` becoming 5 (GAME_IN_PROGRESS) is game clock 0:00
  (matches `m_flGameStartTime` = 596.53 s on the demo clock; pre-game started at `m_flPreGameStartTime` = 506.53 s,
  i.e. the clock starts at -90 s). Game states seen: 2 hero selection, 3 strategy, 4 pre-game, 5 playing, 6 post-game.
- Combat log timestamps (`CombatLogEntry.getTimestamp()`) are on the same demo clock: game clock =
  `timestamp - m_flGameStartTime`. Negative values are pre-horn. Pauses: `m_nTotalPausedTicks`, `m_bGamePaused`
  exist (verify a paused match).
- Player ids in the replay are **2 x the player index**: 0,2,4,6,8 Radiant (`m_iTeamNum` 2), 10..18 Dire (3).
  `CDOTAPlayerController`: `m_nPlayerID`, `m_steamID` (account id = steamID - 76561197960265728; 0 for an
  anonymous player, verify), `m_hAssignedHero`, `m_iTeamNum`. Map to the existing `player_slot` convention
  (Radiant 0-4, Dire 128-132) as `idx = replayPlayerId / 2; slot = idx < 5 ? idx : 128 + idx - 5` (never apply the formula to the raw id), and cross-check against OpenDota's
  `players[].account_id` and `hero_id`.
- Hero entity: `CDOTA_Unit_Hero_<Name>`; `m_iPlayerID` is also on the hero; entity handles resolve with
  `entities.getByHandle`.
- World position = `(CBodyComponent.m_cellX - 128) * 128 + CBodyComponent.m_vecX` (same for Y). Plausibility checked:
  a hero at the fountain gave about (-6926, -6408) and the first gold events log (-6700, -6700). (verify with the
  S1 test: hero position at pre-game start must be within 400 units of the combat-log GOLD `loc` of the same hero.)

### 2.2 Entities and fields we can read every tick
| Source | Fields |
|---|---|
| `CDOTA_PlayerResource.m_vecPlayerTeamData.N` | `m_iKills/Deaths/Assists`, `m_iLevel`, `m_iStreak`, `m_iRespawnSeconds`, `m_flLastBuybackTime`, `m_hSelectedHero`, `m_nSelectedHeroID`, `m_nSelectedHeroFacet`, `m_flTeamFightParticipation`, `m_iFirstBloodClaimed/Given`, `m_unPickOrder`, `m_iBountyRunes/PowerRunes/WaterRunes`, `m_iOutpostsCaptured`, `m_bHasRandomed`, `m_eLaneSelectionFlags` |
| `CDOTA_PlayerResource.m_vecPlayerData.N` | `m_iszPlayerName`, `m_iPlayerSteamID`, `m_iPlayerTeam`, `m_nPlayerSlot`, `m_iConnectionState`, `m_iRankTier` |
| `CDOTA_DataRadiant/Dire.m_vecDataTeam.N` | gold sources: `m_iHeroKillGold`, `m_iCreepKillGold`, `m_iNeutralKillGold`, `m_iBountyGold`, `m_iRoshanGold`, `m_iBuildingGold`, `m_iCourierGold`, `m_iComebackGold`, `m_iIncomeGold`, `m_iSharedGold`, `m_iWardKillGold`, `m_iCreepDenyGold`, `m_iOtherGold`; state: `m_iNetWorth`, `m_iTotalEarnedGold`, `m_iTotalEarnedXP`, `m_iReliableGold`, `m_iUnreliableGold`; farming: `m_iLastHitCount`, `m_iDenyCount`, `m_iNearbyCreepDeathCount`, `m_iMissCount`, `m_iClaimedDenyCount`, `m_iClaimedMissCount`; support/objectives: `m_iObserverWardsPlaced`, `m_iSentryWardsPlaced`, `m_iWardsPurchased`, `m_iWardsDestroyed`, `m_iCreepsStacked`, `m_iCampsStacked`, `m_iRunePickups`, `m_iTowerKills`, `m_iRoshanKills`, `m_iTormentorKills`, `m_iCourierKills`, `m_iWatchersTaken`, `m_iLotusesTaken`, `m_iWisdomShrinesTaken`; combat: `m_flHeroDamage`, `m_flTowerDamage`, `m_fStuns`, `m_fHealing`, `m_flDamageByTypeReceivedPostReduction.N`, `m_flOutgoingDamageByTypePostReduction.N`, `m_iGoldLostToDeath`, `m_iGoldSpentOnBuybacks`, `m_flBuybackCooldownTime`, `m_flBuybackCostTime`, `m_iTPScrollsPurchased` (all cumulative counters: sample and diff) |
| hero entity | `CBodyComponent.m_cellX/Y`, `m_vecX/Y`, `m_iHealth`, `m_iMaxHealth`, `m_flMana`, `m_flMaxMana`, `m_lifeState`, `m_iCurrentLevel`, `m_iCurrentXP`, `m_flRespawnTime`, `m_iAttackRange`, `m_iMoveSpeed`, `m_flPhysicalArmorValue`, `m_flMagicalResistanceValue`, `m_flStrength/Agility/IntellectTotal`, `m_iBKBChargesUsed`, `m_flLastDealtDamageTime`, `m_iTeamNum`, `m_hOwnerEntity` |
| hero inventory | `m_hItems.NNNN` (handles): 0-5 main slots, 6-8 backpack, 15 teleport scroll slot, 16 neutral item, 17 enhancement; item entity has `m_fCooldown`, `m_iCurrentCharges` and its class name (`CDOTA_Item_TeleportScroll`, `CDOTA_Item_Black_King_Bar`, ...). `m_fCooldown` semantics (remaining vs end time) must be verified (S3) |
| hero abilities | `m_vecAbilities.NNNN` (handles), ability entity has `m_iLevel`, `m_fCooldown`, `m_iManaCost`, `m_bInAbilityPhase`, `m_flCastStartTime`, `m_pEntity.m_nameStringTableIndex` -> string table `EntityNames` gives the real ability name |
| buildings | `CDOTA_BaseNPC_Tower` (22), `CDOTA_BaseNPC_Barracks` (12), `CDOTA_BaseNPC_Fort` (2): `m_iHealth`, `m_iMaxHealth`, `m_lifeState`, `m_iTeamNum`, position, name via string table |
| other | `CDOTA_Unit_Courier` (10 created: respawns; `m_hOwnerEntity`), `CDOTA_NPC_Observer_Ward` and `CDOTA_NPC_Observer_Ward_TrueSight` (sentry; `m_hOwnerEntity`, `m_flCreateTime`, `m_iTeamNum`), `CDOTA_Item_Rune` (`m_iRuneType`, `m_flRuneTime`, `m_szLocation`), `CDOTA_Unit_Roshan` (`m_iHealth`), `CDOTA_RoshanSpawner`, `CDOTA_BaseNPC_Watch_Tower`, `CDOTA_BaseNPC_LotusPool`, `CDOTA_BaseNPC_Creep_Lane` (~2270 created per game), `CDOTA_BaseNPC_Creep_Neutral`, `CDOTA_NeutralSpawner` |
| game rules `m_pGameRules` | `m_nGameState`, `m_flGameStartTime`, `m_flPreGameStartTime`, `m_flStateTransitionTime`, `m_bGamePaused`, `m_nTotalPausedTicks`, `m_nGameWinner`, `m_NeutralSpawnBoxes.N` (`strCampName`, `nCampType`, `vSpawnBoxOrigin`, `m_vMinBounds/MaxBounds`: jungle camps with coordinates), `m_eTormentorLocation`, `m_AbilityDraftAbilities.NNNN` (draft, see the spike doc) |

### 2.3 Combat log (per match about 57k entries)
Counts seen in one 45-minute game: DAMAGE 24810, MODIFIER_ADD 11388, MODIFIER_REMOVE 11202, HEAL 7616, XP 3317,
DEATH 3170 (includes creeps and wards), GOLD 2533, ABILITY 1224, ITEM 812, PURCHASE 510, MODIFIER_STACK_EVENT 408,
PLAYERSTATS 383, TEAM_BUILDING_KILL 22, CRITICAL_DAMAGE 50, KILLSTREAK 26, MULTIKILL 17, ABILITY_TRIGGER 12,
GAME_STATE 10, FIRST_BLOOD 1. No BUYBACK entries occurred in this match (the enum has one; detect buybacks with the
resource counters too). Per-entry fields: attacker/target/inflictor names, `value`, `valueName`, `health`,
timestamp, `stunDuration`, `slowDuration`, `modifierDuration`, `goldReason`, `xpReason`, `locationX/Y` (GOLD and XP
only, so far), `assistPlayers`, `attackerTeam`, `targetTeam`, `isTargetHero`, `isAttackerIllusion`, `runeType`,
`neutralCampType`, `networth`, `gpm/xpm`, `damageType`, `isUltimateAbility`, `isHealSave`.
Useful modifier names seen: `modifier_black_king_bar_immune`, `modifier_teleporting`, `modifier_stunned`,
`modifier_silence`, `modifier_invisible`, `modifier_smoke_of_deceit`, `modifier_fountain_aura_buff`,
`modifier_knockback`, `modifier_illusion`, `modifier_invulnerable`.

Verify the field mapping per type with one printed sample each before relying on it (the spike printed some entries
whose `value`/`valueName` read oddly; the semantics differ by `type`). This is spike S2.

### 2.4 Cost and size (measured)
Full parse of a 76 MB replay: about 1.5 s, about 365 MB RSS at `-Xmx256m`. Downloads are 40-45 MB and are
**Zstandard** even though the URL says `.bz2`. Replays are downloadable for about 3 months.

---------------------------------------------------------------------------------------------------

## 3. Architecture

```
MatchParseCheckJob (parsed) / backfill job
        |
ReplayDownloadService   url from match cluster+salt, sniff magic bytes (28 B5 2F FD zstd, "BZh" bzip2),
        |               stream-decompress to a temp .dem, delete after parse; one at a time (semaphore)
ReplayParseService      one Clarity pass; feeds a ReplayContext (clock, identity, handle tables, snapshots)
        |
 ReplayExtractor beans  one per table slice (draft, tracks, minute stats, runes, wards, fights ...)
        |               L1 = facts straight from the replay (needs the file; cannot be recomputed after expiry)
ReplayAnalysisService   L2 = derived tables computed only from L1 + OpenDota tables
                        (lane results, fight analysis, game states, spikes ...). Re-runnable any time.
```

Principles:
1. **Two layers.** L1 facts are expensive to get (replay expires) so they are rich and stored forever; L2 conclusions
   change as the algorithms improve, so they are recomputed from L1 by `ReplayAnalysisService` without any replay.
   Every L2 row carries `analysisVersion`; an admin trigger recomputes a version over old matches.
2. **One parse pass, many extractors.** A single `ReplayContext` observer collects what all extractors need
   (per-second hero snapshots, combat log, entity create/delete) and extractors read from it; do not parse the file
   once per extractor.
3. **Never block ingestion, one replay at a time.** All replay work runs after the match is already stored, in its own
   transaction per extractor; failures set `ReplayParseRun.status` and `extractorStatus[extractor]` and are retried by
   the backfill. Memory is bounded by processing **one replay at a time**: the number in flight is configurable
   (`replay.processing.max-concurrent`, default 1, see 3b.3) and the only entry points (queue job and admin recompute)
   share that one semaphore.
4. **Version everything.** `ReplayParseRun.parserVersion` (our extractor-set int) and `clarityVersion`; when the game
   changes a netprop, extractors must fail soft (status `PARTIAL` with a reason), never write partial rows silently.
5. **Config, not constants.** All thresholds in `replay.analysis.*` properties bound by one `@ConfigurationProperties`
   class; the values used are stored on `ReplayParseRun.analysisConfig` (jsonb) so results stay explainable.
6. **Scope of modes.** Start with Ability Draft (18) and All Draft/"All Pick" ranked (22), the modes dbuff
   tracks; skip others as `SKIPPED_NOT_SUPPORTED`. Replays older than 90 days are `EXPIRED`.

Parse trigger and backfill: reuse `MatchDomain.replayCluster/replaySalt` (already stored). There is **one** entry point,
`ReplayProcessingJob` (3b.3): a Quartz job like `MatchDetailBackfillScheduler` that walks matches with
`detailsMappedAt` set, a salt, no successful run, and started within 90 days, newest first, honouring quiet hours,
one replay at a time. `MatchParseCheckJob` does not start parses; a freshly parsed match is simply picked up by the
next run. Nothing else may take a parse outside the shared semaphore.

Dependencies to add: Clarity `com.skadistats:clarity:5.0.1`; Zstandard decompression (`com.github.luben:zstd-jni`
ships aarch64; fall back to pure-Java `io.airlift:aircompressor` if the native load fails on the instance, verify
in S8); `org.apache.commons:commons-compress` for bzip2. Clarity needs a seekable file, so decompress to a temp file.

---------------------------------------------------------------------------------------------------

## 3a. Integration with the existing OpenDota data

PR #6 (on `origin/main`) already stores, per match: `TeamfightDomain`/`TeamfightPlayerDomain`, `DeathEventDomain`,
`PlayerTimelineDomain`, `TeamTimelineDomain`, `ObjectiveEventDomain`, `ItemPurchaseEventDomain`, `DraftEventDomain`,
`AbilityUpgradeDomain`, `HeroTargetStatDomain`. Replay data is an addition to these, never a second copy.

**3a.1 Join keys (identical to OpenDota's).**
- Match: `matchId`. Player: `playerSlot` (Radiant 0-4, Dire 128-132), the key of `PlayerTimelineDomain`,
  `DeathEventDomain` and `TeamfightPlayerDomain`. `ReplayRoster` maps the replay player id to `playerSlot` and checks
  every mapping against the stored OpenDota players (`account_id`, `hero_id`); on a mismatch the run ends
  `FAILED_IDENTITY` and writes no rows.
- Time: whole game seconds from 0:00, the unit of `TeamfightDomain.startTime`, `DeathEventDomain.deathTime` and the
  purchase times. Replay columns use the same unit (`startSec`, `gameSecond`). S1 gains a check: every replay hero
  death matches a `DeathEventDomain.deathTime` within 1 s.

**3a.2 Entry condition.** Only matches with `MatchDomain.detailsMappedAt != null` (OpenDota parsed it, rows stored,
`replayCluster`/`replaySalt` set by `MatchDetailService`) enter the replay queue. Replay analyses therefore always sit
on complete OpenDota rows.

**3a.3 Rule: OpenDota first, the replay only adds.** A replay table never stores a column an OpenDota table already
holds for the same key.

| Existing table | Replay side |
|---|---|
| `TeamfightDomain`/`TeamfightPlayerDomain` | `FightDomain` holds only **our** detectors (SKIRMISH, ENGAGEMENT, spatial split). The DEATHS_15S baseline is not copied: it is validated directly against `TeamfightDomain`; `openDotaFightIndex` links a fight to it. |
| `DeathEventDomain` (matchId, playerSlot, deathIndex; goldLost, goldFed, timeDead, killer) | `DeathAnalysisDomain`, same natural key, 1:1 extension with replay-only fields (position, region, assists, flags, BKB, `preDeathWindow`, reason). Replaces `HeroDeathDomain` of section 9. |
| `PlayerTimelineDomain` (gold, xp, lastHits, denies, networth, heroDamage, towerDamage, heroHealing, campsStacked per minute) | `ReplayPlayerMinuteDomain`, same key (matchId, playerSlot, gameMinute), **extras only**: gold by source, K/D/A, wards, runes, creeps stacked, misses, nearby creep deaths, buyback state, unspent gold. |
| `TeamTimelineDomain` (radiantGoldAdv/XpAdv per minute) | `TeamMinuteStateDomain` drops net worth and XP; keeps towers, barracks, heroes alive, wards, buybacks. Group 6 uses the 30 s replay net worth when present, else `TeamTimelineDomain`. |
| `ObjectiveEventDomain` (building kills, Roshan, aegis) | `BuildingKillContextDomain` (renamed from `BuildingKillDomain`) links to the objective row by building key and time (+-1 s) and stores only assists, siege, pressure, fight link. |
| `ItemPurchaseEventDomain` (includes pre-horn purchases) | Read from it; do not parse PURCHASE from the combat log. |
| `DraftEventDomain` | Add nullable `pickTick`, `secondsIntoDraft` (All Pick). Ability Draft tables stay new: OpenDota has no equivalent. |

**3a.4 One read side.** Analyzers read through narrow interfaces that merge both sources, so they never know which
table a value came from: `PlayerMinuteSeries` (`PlayerTimelineDomain` + replay extras) and `MatchDeaths`
(`DeathEventDomain` + `DeathAnalysisDomain`), and so on per overlap. Without a replay (missing or expired) they return
the OpenDota part only and the analyses degrade instead of failing.

**3a.5 Timing.** `MatchParseCheckJob` reports right after the OpenDota parse; replay data arrives later from the queue.
WP8 posts the replay findings as a follow-up message in the same thread when the run reaches `DONE`.

---------------------------------------------------------------------------------------------------

## 3b. Code structure (SOLID)

Package `service/replay/`, following the PR #6 pattern (beans discovered as a `List`, as `MatchDetailService` does with
`MatchDetailMapper`).

| Responsibility (SRP) | Type | Notes |
|---|---|---|
| Fetch the file | `ReplayDownloader` | streams to a temp file, never into memory |
| Decompress | `ReplayDecompressor` interface; `ZstdDecompressor`, `Bzip2Decompressor` | picked by magic bytes; a new format is a new class (OCP) |
| Parse | `ReplayParser` interface; `ClarityReplayParser` | **only** `service/replay/clarity/` imports `skadistats` (DIP); returns `ReplayFacts` |
| Facts in memory | `ReplayFacts` composed of narrow views: `ReplayClock`, `ReplayRoster`, `HeroTimeline`, `CombatLogView`, `EntityEvents`, `MapLayout` | ISP: an extractor depends only on the views it reads; tests feed the same views from fixtures |
| Write L1 rows | `ReplayExtractor` beans: `name()`, `extract(ReplayFacts, MatchDomain)`, `deleteByMatchId(long)` | one table slice each; switchable with `replay.<name>.enabled` |
| Compute L2 rows | `ReplayAnalyzer` beans: `name()`, `version()`, `analyze(long matchId)`, `deleteByMatchId(long)` | read only repositories and the 3a.4 interfaces, never `ReplayFacts`; ordered with `@Order` (activity pass 1, fights, activity pass 2, deaths, spikes) |
| Orchestrate one match | `ReplayRunService` | download, parse, run extractors, release `ReplayFacts`, run analyzers; records `ReplayParseRunDomain` and per-extractor status; one extractor failing gives `PARTIAL`, the rest continue (LSP: all extractors honour one contract) |
| Queue and limits | `ReplayProcessingJob` (Quartz) + `ReplayProcessingProperties` | see 3b.3 |
| Delete | `ReplayDataService.deleteByMatchId` | iterates extractors and analyzers; `MatchDeletionService` gets one call, as it does for `matchDetailService` |

`ReplayExtractor` deliberately does **not** implement `MatchDetailMapper`: its input is a parsed replay, not a
`MatchResponse`, and forcing it in would break LSP/ISP. `MatchDetailService` stays unaware of replays (OCP); the job
finds work by querying `MatchDomain`. A shared super-interface for `deleteByMatchId` would change existing code and is
left as an optional later refactor; ask the user first.

**3b.3 Memory: one replay at a time, configurable.**
```
replay.processing.enabled=false          # off until WP0 is verified on prod
replay.processing.max-concurrent=1       # parses in flight; Semaphore bean in ConcurrencyConfig
replay.processing.batch-size=5           # matches per job run, processed one after another
replay.processing.cron=0 3/10 * * * ?
replay.processing.max-age-days=90
replay.processing.max-replay-bytes=150MB # rejected before decompressing
replay.processing.temp-dir=${java.io.tmpdir}/dbuff-replays
replay.processing.max-attempts=5
replay.processing.max-duration-min=120
replay.processing.max-parse-seconds=120
replay.processing.reduced-sampling-after-min=80
replay.processing.min-duration-sec=300
```
- `ReplayProcessingJob` is the only automatic entry point: Quartz cron, `@DisallowConcurrentExecution`, honours
  `QuietHoursGuard`, selects matches with `detailsMappedAt` set, a salt, no `DONE` run and age <= `max-age-days`,
  newest first, and processes them sequentially. The admin recompute endpoint takes the same semaphore.
- Inside one replay: download and decompress stream to disk; `ReplayFacts` keeps primitive arrays per player (not an
  object per frame) and only the hero-relevant typed combat-log entries (not all ~57k); it is released in `finally`
  before the L2 analyzers run; the temp file is always deleted.
- One INFO line per replay: `parseMillis`, `replayBytes`, peak heap.
- If the in-app parse at `-Xmx512m` fails the WP0 exit check, the fallback is a separate short-lived JVM per replay
  (the repo already has `sidecars/`); do not build it before the check says it is needed.

---------------------------------------------------------------------------------------------------

## 3c. Failure modes and resilience

Replay work is optional enrichment. **Every failure ends in a stored status on `ReplayParseRunDomain`, never in an
exception that reaches ingestion, reports or the scheduler**; reports built from OpenDota data keep working with no
replay rows at all (3a.4).

**3c.1 Status model.** `ReplayParseRunDomain.status`, split into permanent (never retried automatically) and
transient (retried with a cap):

| Status | Kind | Cause | Handling |
|---|---|---|---|
| `DONE` | final | all extractors ok | - |
| `PARTIAL` | final | some extractors failed, reasons in `extractorStatus` | kept; failed extractors retried only when `parserVersion` changes |
| `SKIPPED_NO_REPLAY_INFO` | permanent | `replayCluster`/`replaySalt` null (OpenDota has no replay data, private or very old match) | never queued; re-evaluated if a later OpenDota refetch fills the salt |
| `SKIPPED_NOT_SUPPORTED` | permanent | game mode outside the supported set | - |
| `SKIPPED_NOT_PLAYED` | permanent | match never started or ended in pre-game (see 3c.3) | - |
| `EXPIRED` | permanent | download answers 404/410, or match older than `max-age-days` | never retried; L1 data stored earlier stays |
| `TOO_LARGE` | permanent | over a size or duration limit (see 3c.4) | - |
| `FAILED_IDENTITY` | permanent | replay does not belong to the match (see 3c.3) | no rows written; logged at WARN |
| `FAILED_PARSER` | retried on new `parserVersion` | Clarity throws, unknown entity or field layout (new game patch) | not retried with the same version |
| `FAILED_DOWNLOAD` | transient | timeout, 5xx, 403, connection reset, truncated or corrupt file | retry with backoff, capped by `max-attempts` (default 5, backoff 1 h doubling); then it stays failed |
| `FAILED_RESOURCE` | transient, capped | out of memory, disk full, parse timeout | retried at most `max-attempts`; the message names the limit that was hit |
| `RUNNING` | transient | in progress | lease (3c.5) |

**3c.2 Download and file integrity.**
- No cluster or salt: `SKIPPED_NO_REPLAY_INFO` before any network call. Build the URL only from numeric fields.
- The 404/410 answers mean Valve has deleted the file: `EXPIRED`, not an error. Timeouts (connect and read, config),
  5xx, 429 and connection errors are transient. A 403 is treated as transient too, because it also appears when the
  CDN throttles.
- Check `Content-Length` against `max-replay-bytes` before downloading; stop streaming when the byte count passes the
  limit even if the header lies.
- Sniff the magic bytes. Anything that is neither zstd nor bzip2 (an HTML error page returned with 200, an empty
  body) is `FAILED_DOWNLOAD`, not a parser error. A decompression error or a file that ends early is
  `FAILED_DOWNLOAD` and the partial temp file is deleted.
- Circuit breaker: three consecutive transient download failures stop the current job run (as
  `MatchDetailBackfillService` does on a rate limit or outage), so an outage costs three requests, not a full batch.

**3c.3 Replays that are not a normal game.**
- **Never started:** OpenDota duration 0 or missing, or the replay has no game state 5 (GAME_IN_PROGRESS):
  `SKIPPED_NOT_PLAYED`. A match abandoned in the draft or strategy phase yields exactly this.
- **Very short game** (under `min-duration-sec`, default 300 s, an abandon or early surrender): parse only the draft
  and the roster, skip the analyses that need a lane phase, status `PARTIAL` with reason `SHORT_GAME`.
- **Replay of a different match:** the replay's own id (`m_pGameRules.m_unMatchID64`) is a lobby id (448790562 for
  match 9038725154) and cannot be compared. Check the roster instead: the same accounts on the same slots, falling
  back to the hero for anonymous players (`ReplayRosterVerifier`). Any mismatch: `FAILED_IDENTITY`, no rows. This also covers a
  salt reused wrongly or a cached response.
- **Leavers, disconnects, bots, anonymous players (`steamId = 0`):** the roster maps those by team and index. An
  unmapped slot is stored with a null `accountId` and its analyses are skipped; the rest of the match is unaffected.
- **Pauses:** the clock subtracts `m_nTotalPausedTicks`. If the pause counters are inconsistent (clock later than the
  combat-log time by more than 2 s), flag `CLOCK_UNCERTAIN` on the run and skip the analyses that compare replay time
  with OpenDota times (3a.1), instead of writing shifted data.
- **Game ended by ancient or by abandon:** the last frames may be incomplete; extractors must tolerate a final missing
  sample and never index past the last tick.

**3c.4 Long matches and resource limits (for example a 90-minute game).**
- Memory use grows with duration (about 2x a 45-minute game for per-second data). Limits in
  `ReplayProcessingProperties`: `max-duration-min` (default 120) and `max-replay-bytes`; over either:
  `TOO_LARGE`, not an attempt to parse.
- The sizing in 4.3 is per second, so a 90-minute game costs about 10 x 5400 x 60 B = 3.2 MB for frames and a
  proportionally larger combat-log view. Both are inside the one-replay budget; spike S8 and the WP0 exit check
  measure a 90-minute replay explicitly and record the peak heap.
- Sampling degrades before failing: past `reduced-sampling-after-min` (default 80), position tracks drop from 5 s to
  10 s and the step is stored in `ReplayPlayerTrackDomain.stepSec`, so readers do not assume 5 s.
- Parse timeout `max-parse-seconds` (default 120): the parse runs on its own thread and the Clarity run checks
  a cancel flag every tick batch, so a stuck parse ends as `FAILED_RESOURCE` and frees the semaphore.
- `OutOfMemoryError` is caught at the run boundary only, the facts are released, `FAILED_RESOURCE` is stored, and the
  job stops the current run (a following replay would likely fail the same way). Free disk space is checked before
  the download; `temp-dir` is cleaned at startup and after every run (`finally`).

**3c.5 Interrupted work and consistency.**
- A crash or restart while `RUNNING` leaves a stale row: `RUNNING` carries a `leaseUntil`; the queue treats an
  expired lease as failed and counts it as one attempt. No process holds a replay run across restarts.
- Each extractor writes in its own transaction with delete-then-insert for the match, so a retry replaces rather
  than duplicates. A failed extractor leaves no half-written rows.
- Extractors and analyzers are idempotent and keyed by `matchId` (+ `analysisVersion` for L2), so reprocessing is
  always safe.
- A **poison match** (fails repeatedly) is bounded by `max-attempts`; it never blocks the queue, because the job moves
  on to the next match after each failure.
- Match deletion removes replay rows through `ReplayDataService`, and a replay run for a deleted match ends quietly.

**3c.6 Observability.** One INFO line per replay run (status, attempts, parseMillis, replayBytes, peak heap), WARN for
`FAILED_*` with the reason, and a count of runs per status exposed as a metric. No per-tick logging.

**3c.7 Tests.** Unit tests per row of 3c.1 with fakes (`ReplayDownloader` returning 404, a truncated stream, a wrong
magic, a mismatching roster, a cancelled parse, a thrown `OutOfMemoryError`), plus a test that a failing replay run
leaves the match, its OpenDota rows and the report untouched. The real-replay integration test (section 12) adds a
paused match and a 90-minute match when such replays are available locally.

---------------------------------------------------------------------------------------------------

## 4. Shared building blocks (build these first; every group uses them)

### 4.1 `ReplayClock`
`gameSecond(tick)` = `(tick - startTick - pausedTicks(tick)) / 30.0`, `startTick` = first tick where game state is 5.
Combat log entries convert with `timestamp - gameStartTime`. Both must agree within 1 s (test S1).

### 4.2 `ReplayRoster` (identity)
Built once after the draft: per player `playerSlot`, `replayPlayerId`, `teamNum`, `steamId`/`accountId`, `heroEntityName`,
`heroId` (match to OpenDota), `heroHandle`, entity handle sets for the player's hero(es) (Meepo clones, illusions and
summons must be excluded: only entities with the controller's `m_hAssignedHero` or `m_iPlayerID` and not
`m_bIsIllusion`/clone). Players with `steamId = 0` are matched by team + player index only.
Role inference (`rolePosition` 1-5 + `roleSource`): use OpenDota `lane_role`/`is_roaming`/`lane` when present, else
net-worth rank within the team at minute 10 and lane assignment (4.5); store it, never recompute silently.

### 4.3 `ReplaySampler` (the L1 time series)
Every 30 ticks (1 s) keep a `Frame` per player: x, y, health, maxHealth, mana, maxMana, level, alive, respawn time,
plus team data counters and net worth. Persist at **5 s resolution** for the whole game (from -90 s) as arrays
(`ReplayPlayerTrack`), and at **1 minute** resolution as rows (`ReplayPlayerMinute`). Keep 1 s frames in memory
for the analysis passes of one game (10 players x 3600 s x ~60 B = ~2 MB), then drop them.
Also sampled at 1 s in memory (not persisted raw): inventory (item names + cooldowns), the hero ability list
(level + cooldown + mana cost), `m_flLastDealtDamageTime`, active whitelisted modifiers (from the combat log),
teleport state, creep lane positions (every 5 s for lane creeps).

### 4.4 `CombatLogIndex`
Typed, time-sorted views over the combat log so analyses do not re-scan 57k entries: hero deaths, hero damage events
(attacker hero, target hero, inflictor, value), ability casts, item uses, purchases, gold/xp events, building kills,
modifier add/remove for the **whitelist** below. Store only whitelisted modifiers.

Modifier whitelist (config): disables (`modifier_stunned`, `modifier_silence`, `modifier_hexxed`, root/ensnare/
sleep/cyclone/fear/taunt names: build the list from OpenDota constants `abilities` where `dispellable`/`behavior`
flags or, simpler, the ability constants' `target_type` and a curated list), spell immunity
(`modifier_black_king_bar_immune`, `modifier_item_*` immunity names), teleport (`modifier_teleporting`), invisibility
(`modifier_invisible`, `modifier_smoke_of_deceit`), fountain (`modifier_fountain_aura_buff`), major item/ability buffs
(Aghanim's/blink are not modifiers; use the item inventory instead), respawn/aegis, buyback.

### 4.5 Map model (`MapLayout`)
Built from entities, not hard-coded numbers, so it survives map patches: lane axes from the tower positions per lane
(`npc_dota_<team>_tower<tier>_<top|mid|bot>` plus barracks), river line, base zones (radius around the fort),
jungle camps from `m_NeutralSpawnBoxes` (name, type, box), Roshan pit (RoshanSpawner), tormentor locations,
Watch Towers/outposts, lotus pools, shrines. A frozen copy keyed by `mapVersion` (derived from the tower position
hash) is stored as a JSON resource; at parse time compute the hash and warn when it is unknown.
`MapLayout.region(x, y)` returns: `LANE_TOP|MID|BOT` (+ own/enemy side and tier segment), `RIVER`, `OWN_JUNGLE`,
`ENEMY_JUNGLE` (+ camp id), `OWN_BASE`, `ENEMY_BASE`, `ROSHAN`, `OTHER`. Calibrate in spike S7 with the two replays:
plot heroes during minutes 1-8 and check the lane classification matches OpenDota `lane` for >= 90% of players.

### 4.6 `ActivitySegmenter` (shared by Groups 2, 3, 4, 5)
Classifies each player's every 5 s into one activity, then merges into segments:
`LANE_FARM`, `JUNGLE_FARM`, `ROAM`, `PUSH` (near enemy tower/creeps in enemy lane half), `DEFEND`, `FIGHT`,
`OBJECTIVE` (Roshan/tormentor/lotus/shrine/watchtower), `TELEPORTING`, `FOUNTAIN`, `DEAD`, `IDLE` (stationary > 10 s
away from farm), `TRAVEL`. Inputs: region, speed, creep-kill events near the hero (combat log DEATH with a creep
target, attributed to the nearest hero of the killing team; lane creeps vs neutral), `modifier_teleporting`,
`m_lifeState`, and the fight windows from Group 3 (fight overrides farm). Rules and thresholds are config. Output rows:
`PlayerActivitySegment(matchId, slot, startSec, endSec, activity, region, detail)`. About 150 per player per game.
Because it needs the fight windows and the fights need pre-fight activity, run it twice: pass 1 without FIGHT,
fight detection, pass 2 with FIGHT overriding.

### 4.7 `TeamStateSegmenter`
Per team and 10 s, `leadPct = (ownNW - enemyNW) / (ownNW + enemyNW)` from `m_iNetWorth` sums. State with hysteresis:
`AHEAD` when `leadPct >= +aheadPct` (default 5%) for >= 120 s, `BEHIND` when `<= -aheadPct`, otherwise `EVEN`;
leave a state only after crossing back over half the threshold for 60 s. Segments are mirrored for the two teams.
Defaults are config; validate on the two sample games and on 10 more before freezing (S10).

---------------------------------------------------------------------------------------------------

## 5. Group 1 - Pick (draft)

### 5.1 Data (verified, see the spike doc)
Ability Draft: `m_pGameRules.m_AbilityDraftAbilities.NNNN` (48 entries): `m_nAbilityID` (= 2 x OpenDota ability id),
`m_unPlayerID` (= 2 x player index, 20 = not picked), `m_unAbilityPlayerSlot` (0-2 regular, 3 ultimate; final value).
Pick order = order of `m_unPlayerID` updates (exactly 40). Pick time = tick. Hero entity gives the player's hero;
names come from OpenDota `ability_ids` after halving the id (skipped skills included).
Also read `CDOTA_PlayerResource m_unPickOrder` and `m_nSelectedHeroID/Facet` per player (S6: what `m_unPickOrder`
means in Ability Draft).
All Pick (mode 22): the OpenDota `picks_bans` are already stored (`DraftEventDomain`, Phase 1). The replay adds
exact timing and what was available: during hero selection read `m_BannedHeroes.NNNN`, `m_SelectedHeroes.NNNN`,
`m_iActiveTeam`, `m_flHeroPickStateTransitionTime`, `m_iMiscHeroPickCounter` as they change (they are cleared by the
end of the game, so they must be captured while the draft runs) and store the hero pool = every hero id in the
OpenDota hero constants minus nothing (the pool is the whole roster; store bans/picks and the pick timestamps).
Spike S9 verifies this on an All Pick replay before any AP work.

### 5.2 Tables (L1)
- `AbilityDraftPickDomain` (40 rows): `matchId, pickOrder (1..40), playerSlot, replayPlayerId, team,
  teamPickIndex (1..20), pickTick, secondsIntoDraft, secondsSincePrevPick, abilityId (OpenDota id), abilityName,
  finalAbilitySlot (0-3), isUltimate, poolRemainingBeforePick`.
- `AbilityDraftPoolDomain` (48 rows): `matchId, poolIndex, abilityId, abilityName, isUltimate (nullable for skipped,
  fill from ability constants), pickedBySlot (null), pickOrder (null)`.
- `HeroDraftEventDomain` (All Pick only, enriches the existing `DraftEventDomain`): `matchId, seq, kind PICK|BAN, team,
  heroId, tick, secondsIntoDraft, activeTeamTimeRemaining`. Prefer adding the replay timing columns to
  `DraftEventDomain` (nullable `pickTick, secondsIntoDraft`) instead of a second table (DRY); decide when WP1 starts.

### 5.3 Derived (L2, SQL/Java, no new parse)
- Queue position of a player: `pickOrder` list of the player's 4 picks, `firstPickOrder`, rank within team, and "was
  among the first N picks" flags; turn latency (`secondsSincePrevPick`, timeout/auto-pick suspicion when the pick
  came within 1 s of the 7 s timer end).
- What was available at each pick: pool minus earlier picks. `AbilityDraftTurnView` (a query, not a table) returns for a
  pick the skipped alternatives and the best known win rate among them (from aggregates below).
- Counter/synergy inputs: for each focus pick, the enemy skills already picked, the enemy skills picked afterwards,
  teammate skills; aggregated nightly into `AbilityPairStatDomain(abilityA, abilityB, relation SAME_TEAM|OPPONENT,
  patch, games, wins)` and `AbilityPickPositionStatDomain(abilityId, pickOrderBucket, patch, games, wins)` so
  "is skill X worth a first pick" and "X vs Y" become index lookups.
- Hero pool for All Pick: bans/picks sequence per team, first/last pick side, and the focus player's pick relative to
  the enemy picks already made (counter-pick context).

### 5.4 Acceptance
- On the two spiked replays: 40 picks, 48 pool, per-player drafted skills equal OpenDota's levelled skills (already true).
- Unit test with a captured event stream (record the `CDOTAGamerulesProxy` updates once to a small JSON fixture,
  replay them through the extractor); no replay file in the repo.

---------------------------------------------------------------------------------------------------

## 6. Group 2 - Line staging and early game

### 6.1 Which early signals matter (research summary)
There is no single source of truth; rank by effect and by availability. Tracker precedent: D2PT calls a lane won when
the lane's relative net worth at 10:00 differs by more than 8%. Plan: use that as the default rule (config) and keep the
raw numbers so the rule can change.

| Priority | Signal | Why | Source |
|---|---|---|---|
| P0 | net worth, XP, level, last hits, denies at 3/5/7/10/12/15 min, and the difference to the lane opponent(s) | the main lane outcome measure; 10-15 min gold/XP lead is the strongest early predictor | `ReplayPlayerMinute` (team data counters) |
| P0 | kills/deaths/assists before 10 min, first blood, gold lost to death | tempo and snowballing | `m_iKills/Deaths`, combat log DEATH, `m_iGoldLostToDeath` |
| P0 | lane assignment, partners, roaming, lane swaps, who is 2v1 | explains the numbers above | tracks + `MapLayout` |
| P0 | creep equilibrium and creep wave position per lane | pushed/frozen lanes, tower pressure | lane creep positions every 5 s |
| P0 | runes: bounty at 0:00/3:00/..., power/water runes: who took them, contests | free gold/tempo, support rotation | rune entities + counters |
| P0 | starting items and early purchases (pre-horn), TP scroll timing | decisions before 0:00 | purchases with negative times from `ItemPurchaseEventDomain` only (3a.3), no combat-log PURCHASE |
| P0 | observer/sentry placement: count, time, place, lifetime, killed by whom | vision control and its trade | ward entities, `m_iObserverWardsPlaced`, DEATH |
| P1 | camps stacked, creeps stacked, pulls | support farm economy | counters `m_iCampsStacked/CreepsStacked` |
| P1 | courier deaths, courier kills | tempo and gold swing (courier gold) | DEATH target `npc_dota_courier`, `m_iCourierKills` |
| P1 | first tower fall time and who/when pressure started | map control | TEAM_BUILDING_KILL, tower health |
| P1 | missed last hits / claimed misses, nearby creep deaths share | farm efficiency without relying on OpenDota's formula | `m_iMissCount`, `m_iNearbyCreepDeathCount` |
| P1 | neutral item tier-1 timing and choice | affects fights ~5-7 min (already in OpenDota `neutral_item_history`) | OpenDota |
| P2 | smoke usage, TP scroll use during the lane, regen/consumables, fountain trips | context for "why was X absent" | ITEM combat log, `modifier_fountain_aura_buff` |
| P2 | wisdom shrine, watcher/outpost captures | minor early objectives | team data counters |

### 6.2 "Before and around 0:00"
Window `[-90 s, +120 s]`. Extract and store: positions every 5 s (tracks), purchases with their gold (from `ItemPurchaseEventDomain`), ward items
used/placed pre-horn, courier initial actions (who owns it, first deliveries), who walked to which lane by +30/+60 s,
bounty runes at 0:00 (who got which and from where), smoke usage, TP scrolls bought, the first fight/kill and where.
Summary row: `PreGameSummaryDomain(matchId, playerSlot, startingItemsJson, goldSpentPreHorn, laneAtPlus60, firstMoveRegion,
bountyRuneAt0, scrollsBought, wardsPlacedPreHorn, smokeBought)`.

### 6.3 Extractors and tables (L1)
- `ReplayPlayerTrackDomain` (1 row/player, arrays at 5 s, from -90 s): `x[], y[], hp%[], mana%[], aliveMask, flags[]
  (alive|teleporting|invisible|disabled|spellImmune bits), level[], gold unspent[]`, `sampleStartSec=-90`, `stepSec=5`.
  Postgres `smallint[]` via hypersistence; query with `unnest ... with ordinality`.
- `ReplayPlayerMinuteDomain` (1 row/player/minute from -1): level, xp (`m_iTotalEarnedXP`), `totalEarnedGold`,
  `netWorth`, unspent gold, `lastHits`, `denies`, `nearbyCreepDeaths`, `missCount`, `claimedMiss`, kills, deaths,
  assists, `heroDamage`, `towerDamage`, `stuns`, `healing`, `damageReceivedPostReduction`, `obsPlaced`, `sentPlaced`,
  `wardsDestroyed`, `campsStacked`, `creepsStacked`, `bountyRunes`, `powerRunes`, `waterRunes`, `courierKills`,
  `goldLostToDeath`, `buybackSpent`, `buybackReadyAtSec`, and the cumulative gold by source (`heroKillGold,
  creepKillGold, neutralKillGold, bountyGold, roshanGold, buildingGold, courierGold, comebackGold, incomeGold,
  wardKillGold`). This table is the backbone for Group 6.
- `RuneEventDomain`: `matchId, eventIndex, runeType (BOUNTY|POWER_x|WATER|WISDOM), location, spawnSec, spawnX/Y,
  pickedBySlot, pickedAtSec, contested (an enemy hero within R of the rune at spawn+pickup window), source`.
  Spawns from `CDOTA_Item_Rune` create/delete (`m_iRuneType`, `m_flRuneTime`, `m_szLocation`); pickup attribution =
  the per-player rune counter increment (`m_iBountyRunes/PowerRunes/WaterRunes`) in the same second the entity is
  deleted. When several runes are taken in the same second (bounty runes at 0:00), attribute each deleted rune to the
  nearest alive hero of a team whose counter increased, one rune per counter increment. Verify in S5.
- `WardDomain`: `matchId, wardEntityId, kind OBS|SENTRY, team, ownerSlot, x, y, region, placedSec, endSec,
  lifetimeSec, endReason EXPIRED|KILLED|UNKNOWN, killedBySlot`. From entity create/delete of
  `CDOTA_NPC_Observer_Ward` (`m_hOwnerEntity` -> hero -> slot) and `..._TrueSight`; kill attribution from the DEATH
  combat log entry targeting `npc_dota_observer_wards` / `npc_dota_sentry_wards`.
- `CourierEventDomain`: `matchId, team, eventSec, kind DEATH|RESPAWN|SHIELD|BURST, killerSlot, carriedGoldHint`.
- `LaneEquilibriumDomain` (P1): `matchId, lane, gameSecond (30 s steps to 20:00), equilibriumPos (-1 own tower ..
  +1 enemy tower), ownCreeps, enemyCreeps`. From `CDOTA_BaseNPC_Creep_Lane` positions projected on the lane axis.
- `PreGameSummaryDomain` (above).

### 6.4 Derived (L2)
- `LaneAssignmentDomain` (1 row/player): `lane TOP|MID|BOT|JUNGLE|ROAM`, `laneRole SAFE|MID|OFF|JUNGLE|ROAM`,
  `laneSharePct0to5/0to10`, `partnerSlots[]`, `opponentSlots[]`, `laneGroupId`, `nwDiff5/8/10`, `xpDiff5/10`,
  `lhShare10` (his last hits / `nearbyCreepDeaths` delta), `killsInLane`, `deathsInLane`, `result WIN|LOSS|EVEN` (8% rule),
  `swappedLane` (changed lane after minute 3), `isRoamer`.
  Rule: lane = region with most seconds in `[0:30, 5:00]` in the player's track; roaming when < 50% in any lane and
  > 25% travelling between lanes; jungle when > 50% in jungle.
- `LaneMatchupDomain` (3 rows/game): per lane the Radiant vs Dire slot sets, combined nw/xp at 5/10 min, outcome,
  creep equilibrium shift, first tower time, who died where.
- `EarlyCheckpointDomain` is a **view** over `ReplayPlayerMinute` at minutes 3/5/7/10/12/15 plus lane opponent deltas
  (no extra table).

### 6.5 Acceptance
- Lane assignment agrees with OpenDota `lane/lane_role` for >= 90% of players on 20 replays (document the misses).
- Rune pickups per team equal the end-of-game `m_iBountyRunes+PowerRunes+WaterRunes` totals.
- Ward counts per player equal `m_iObserverWardsPlaced/SentryWardsPlaced`.

---------------------------------------------------------------------------------------------------

## 7. Group 3 - Fights

### 7.1 How to find fights (research result)
OpenDota's rule, read from `odota/parser` `processors/processTeamfights.mjs`: iterate events; a hero death (not an
illusion) opens a fight with `start = firstDeath - 15 s`; each later death within 15 s extends it
(`last_death`); the fight closes when 15 s pass without a death (`end` is the first interval at or after that);
fights with fewer than 3 deaths are discarded. Per player it then sums hero damage, healing, gold/xp delta, ability
and item uses, deaths (with death position) and buybacks inside `[start, end]`. Limits: kill-based only (misses
engagements without 3 deaths and fights where nobody dies), uses one time window for the whole map (two simultaneous
fights merge), and it does not tell who was nearby but inactive.

Our detector keeps that rule as the baseline and adds layers; store the detector id so results stay comparable.
1. `DEATHS_15S` (baseline, identical to OpenDota): deaths >= 3, cooldown 15 s. Use it to validate our implementation
   against OpenDota's `teamfights` (the match payload already holds them; target >= 95% of fights matched by overlap).
2. `SKIRMISH` (new): clusters with 1-2 hero deaths, or >= 2 heroes of each team trading damage, so pickoffs and small
   fights are included (flag `type`).
3. `ENGAGEMENT` (new, "fights without anything"): from the damage log, slide a 10 s window over hero-to-hero damage;
   a window is an engagement when damage dealt by both teams exceeds `engagementMinDamage` (default 600 each way,
   config) and >= 2 heroes per side participate; merge windows closer than 8 s. Catches fights where someone
   escaped, a BKB/blink turned it, or the fight ended by a retreat.
4. **Spatial split**: cluster death/damage points by distance (default 2000 units); concurrent fights in different
   places become separate rows.
5. Tags: `ROSHAN` (Roshan or his pit within radius at the time), `TOWER` (tower damaged in the window),
   `HIGHGROUND`, `SIEGE`, `OBJECTIVE_NONE`.
6. A fight may include **participants** = every hero (alive or not) within `participationRadius` (default 1500 units)
   of the fight centroid at any second in `[start, end]`, or who dealt/received hero damage in it, or died in it.
Calibrate thresholds in S10 on 20 replays: precision/recall against OpenDota deaths-based fights, plus a manual
review of 10 engagements that the baseline missed.

### 7.2 Per-fight tables (L1+L2)
- `FightDomain`: `matchId, fightIndex, detector, type (TEAMFIGHT|SKIRMISH|ENGAGEMENT), startSec, endSec,
  firstDeathSec, centerX, centerY, region, tags, radiantDeaths, direDeaths, radiantParticipants, direParticipants,
  radiantHeroDamage, direHeroDamage, radiantGoldDelta, direGoldDelta, radiantXpDelta, direXpDelta,
  winnerTeam (RADIANT|DIRE|DRAW by the rule below), towersTakenWithin60s, roshanTakenWithin60s, aegisTaken,
  teamStateAtStart (AHEAD|EVEN|BEHIND for Radiant), openDotaFightIndex (nullable link)`.
  Winner rule: `score(team) = sum(net worth of enemy heroes that died) - sum(net worth of own heroes that died)`,
  taken at death time; the team with the higher score wins when `|score difference| >= fightWinMarginPct` (default
  10%) of the combined net worth of the heroes involved. Below that, the team that held the ground (centroid of
  survivors 10 s after `end` closer to the fight centre) wins; `DRAW` when neither applies. Record the margin.
- `FightPlayerDomain` (10 rows per fight): everything needed to answer "why was X absent / quiet":
  `presence` enum `ACTIVE | PRESENT_PASSIVE | ABSENT_ALIVE_FAR | ABSENT_DEAD | ABSENT_TELEPORTING | ABSENT_FARMING |
  ABSENT_PUSHING | ABSENT_OBJECTIVE | ABSENT_FOUNTAIN | ABSENT_SPLIT_FIGHT`, `absenceReason` (`NO_TP`,
  `TP_ON_COOLDOWN`, `TP_ARRIVED_LATE`, `FARMING_JUNGLE`, `FARMING_LANE`, `PUSHING`, `AT_OBJECTIVE`, `DEAD`, `NONE`),
  `aliveAtStart`, `hpPctAtStart`, `manaPctAtStart`, `distanceToFightAtStart`, `arrivalDelaySec`, `leftEarlySec`,
  `activityBefore60`, `activityBefore120` (from the segmenter), `unspentGoldAtStart`, `hasTpItem`, `tpCooldownLeft`,
  `teleportingAtStart` (+ destination region if the teleport finished inside the window),
  `frontlineRank` (1 = closest to the enemy, among own 5), `expectedFrontlineRank`, `frontlineDelta`,
  `damageToHeroes`, `damageTaken` (post reduction), `healing`, `stunSecApplied`, `disabledSecReceived`, `kills`,
  `deaths`, `assists`, `goldDelta`, `xpDelta`, `castCount`, `readyAbilityCount`, `unusedReadyAbilityCount`,
  `bkbOwned`, `bkbReady`, `bkbUsed`, `canBuyback`.
- `FightAbilityUseDomain` (per fight, player, ability or item; ~900 rows/game): `abilityName, kind ABILITY|ITEM,
  level, readyAtStart, cooldownLeftAtStart, manaCost, manaAtStart, manaEnoughAtStart, casts, firstCastSec,
  damage, stunSec, lastCastBeforeFightSec, lastCastWasOnCreeps, nonCastReason`.
- `FightDamageFlowDomain` (optional P1): `fightIndex, attackerSlot, targetSlot, damage, stunSec` aggregated.
- `PlayerActivitySegmentDomain` (from 4.6) gives the 1-2 minutes of context before each fight.

### 7.3 Algorithms for the "why" questions
**Absent from a fight.** At `startSec` (and again at the first damage event) for each player not `ACTIVE`:
1. dead? (`m_lifeState`, respawn timer) -> `ABSENT_DEAD`, with seconds left to respawn and `canBuyback`.
2. teleporting? (`modifier_teleporting` active) -> `ABSENT_TELEPORTING`; if the modifier ended inside the window and the
   hero then stands within radius, `TP_ARRIVED_LATE` and `arrivalDelaySec`.
3. far and alive: compute `hasTpItem` (scroll/boots/Travel Boots/Boots of Travel in `m_hItems`) and the item cooldown
   (S3). No item -> `NO_TP`; cooldown > time-to-fight -> `TP_ON_COOLDOWN`; available but unused -> `HAD_TP_NOT_USED`.
4. what was he doing: last activity before the fight from the segmenter (`FARMING_JUNGLE` when the last
   neutral-creep kills were within 20 s and he was in a camp box, `FARMING_LANE`, `PUSHING`, `AT_OBJECTIVE`).
**Ability not used.** For each ability the hero had at level >= 1 at fight start, classify why not cast if `casts = 0`
(or fewer than the number the cooldown allowed):
- `ON_COOLDOWN`: cooldown left > 0 at `startSec`; also record when it was last cast and whether that cast hit only
  creeps (damage events with that inflictor to non-hero targets in the 20 s before the fight) -> "used on creeps".
- `NO_MANA`: mana at start < cost, or mana fell below cost inside the fight; also record mana spent in the previous
  60 s (casts and items) and whether a mana item was bought/used in the previous 2 min (purchase + ITEM use:
  clarity, mango, arcane boots, soul ring) or a fountain visit (`modifier_fountain_aura_buff`) happened, so a report
  can say "had no mana because he used Q on creeps 30 s earlier".
- `DISABLED`: silenced/stunned/hexed for > 50% of the window (modifier whitelist).
- `DEAD_EARLY`: died within the first `earlyDeathSec` (default 5 s) of the fight.
- `NO_TARGET`: no enemy hero within cast range (ability range from OpenDota ability constants) while alive.
- `HELD_BACK`: ready, mana OK, alive, in range, not disabled, yet no cast -> the strongest "missed opportunity" flag.
- `NOT_APPLICABLE`: passive, toggle, or innate.
**Front-line check.** Project each hero's position at `startSec` and at the first damage event onto the axis from the
own-team centroid to the enemy-team centroid; `frontlineRank` is the rank of that projection among the 5 allies.
Role-free expectation: `expectedFrontlineRank` is the rank by `m_iMaxHealth` (descending) blended with attack range
(ascending) so tanks go first and ranged damage dealers last, then adjusted by `rolePosition` when known (position 1
should not be in rank <= 2, position 4/5 should not out-tank the position 1/2 on hero damage taken). Store
`frontlineDelta`; reports flag `|delta| >= 2` and, for a carry, taking more than `carryFrontDamageShare` of team damage.
Use only the engage moment, not the whole fight (positions mix during the brawl).

### 7.4 Acceptance
- Baseline detector overlaps >= 95% of OpenDota's `teamfights` on 20 replays (kills equal, bounds within 15 s).
- Every fight has 10 `FightPlayerDomain` rows; `presence` is never null; reason codes are documented with 5 hand-checked
  examples from the sample replays (record the replay time so a human can verify in the client).
- The "mana/cooldown" classifier is validated by hand on 3 fights: pick abilities that were not cast, confirm in the
  combat log that the cooldown/mana state matches.

---------------------------------------------------------------------------------------------------

## 8. Group 4 - Team and player movement, objectives, game state

### 8.1 Towers and buildings
- `BuildingKillContextDomain` (time, building and killer stay in `ObjectiveEventDomain`; this row adds context only): `matchId, buildingName, tier (1-4), lane, kind TOWER|BARRACKS_MELEE|BARRACKS_RANGED|
  FORT|OUTPOST, team (owner), killerSlot, denied (killer team == owner), assistSlots[] (own heroes within 1500 units of
  the building in the 10 s before), fightIndex (nullable), siegeCreepsPresent, pushStartSec`.
  From `DOTA_COMBATLOG_TEAM_BUILDING_KILL` (the target name carries tier/lane; the killer hero is in `valueName`).
- `BuildingPressureDomain` (P1): tower health every 10 s (`m_iHealth`) and the damage events by source (hero/creeps/
  siege), so "tower killed after a 90 s siege vs. one burst" is visible.
- `TeamMinuteStateDomain`: `matchId, gameMinute, team, netWorth, totalXp, towersAlive by tier, barracksAlive,
  heroesAlive, unspentGold, wardsActive, buybacksReady, leadPct, state (AHEAD|EVEN|BEHIND)`.
- `GameStateSegmentDomain`: from the segmenter (4.7): `team, startSec, endSec, state, peakLeadPct`.

### 8.2 Player map behaviour
From the activity segments and tracks:
- `PlayerPhaseSummaryDomain` (1 row per player and phase): phases `LANE (0-12 min)`, `MID (12-25 min)`, `LATE (25+)`
  (config). Columns: seconds in each region (jsonb), `laneSharePct`, `jungleSharePct`, `roamSharePct`, `inBaseSharePct`,
  `deadSharePct`, `farmingSharePct`, `pushingSharePct`, `idleSharePct`, GPM/XPM in the phase, kills/deaths in the phase,
  `fightPresencePct` (fights where `presence` was ACTIVE or PRESENT_PASSIVE / all fights in the phase),
  `wardsPlaced`, `enemyJungleSec`, `rotations` (lane changes), `towerDamage`, and the phase result vs. the lane
  opponent (for LANE) or vs. the enemy position with the same `rolePosition` (for later phases).
- Compare movement with fights: `PlayerFightAvailabilityView`: for each phase, fights the player could have joined
  (alive) vs joined, by absence reason. This is the join of `FightPlayerDomain` with the phase table.

### 8.3 Three game states and what the team does
For every `GameStateSegment` of the focus team compute `TeamBehaviorDomain` (1 row per team per segment, with a
`metrics` jsonb so new metrics need no migration; promote stable ones to columns):

| State | Question | Metrics |
|---|---|---|
| AHEAD | does the team convert the lead? | enemy-jungle camp kills and seconds, towers killed and time per tower, observer wards placed in the enemy half / near enemy towers (offensive vision), Roshan attempts/kills, high-ground attempts, kills made while ahead and which hero made them, lead trend (growing/shrinking per minute), carry farming time **without friendly vision** (carry within the 1600-unit vision radius of an own ward) and farming alone, seconds the team was grouped (all 5 within 3000 units) vs split |
| BEHIND | does it defend sensibly? | defensive wards (in own half/jungle), dewards (enemy wards killed), TP-defence count (teleport ending near an own building under attack), own towers lost and time between loss and re-entry, buyback readiness at each lost tower, carry safe-farm vs fighting, avoidance of fights (fights offered vs fights taken), gold unspent |
| EVEN | what are they doing? | farm vs fight vs objective time shares, whether the team is waiting for an item (top unspent-gold holder and the minutes until his next purchase of a core item, from OpenDota `purchase_log`), Roshan/tormentor contest, ward battles, tempo (kills per minute) |

"Why is the winning team still killing?" is answered by the AHEAD row: `killsMadeWhileAhead`, `fightsStartedBy`,
who made the kills, and whether the carry farmed without vision during the same minutes (so a pickoff followed by a
lone farmer is flagged).

### 8.4 Acceptance
- Phase shares sum to 100% per player; lane share agrees with `LaneAssignmentDomain`.
- Building kills equal `m_iTowerKills` totals and OpenDota `objectives` `building_kill` (22 in the sample game).
- State segments reviewed manually on 5 games for plausibility (no flapping: no state shorter than the hysteresis).

---------------------------------------------------------------------------------------------------

## 9. Group 5 - End game

### 9.1 What counts as end game
Config `endGameStartSec` default: the later of 30:00 and the first time either team attacked a barracks/high ground;
all analyses below apply to all deaths, and the report prioritises those in the end game window and in fights tagged
`HIGHGROUND` or with `winnerTeam` deciding the game.

### 9.2 Death analysis (L1+L2): `DeathAnalysisDomain` (1:1 extension of `DeathEventDomain`, see 3a.3)
One row per hero death (about 50 per game): `matchId, deathIndex, deathSec, victimSlot, killerSlot, killerKind
HERO|CREEP|TOWER|NEUTRAL|ROSHAN|SELF, assistSlots[], x, y, region, fightIndex, respawnSec, goldLost, buybackUsed,
canBuyback, reason (enum), flags (jsonb), preDeathWindow (jsonb)`.
`preDeathWindow`: the last 8 s: damage taken by attacker and inflictor with amounts, disables/modifiers with start,
end, source and `pierceBkb`, healing received, items/abilities the victim used, teammates' save items used within range.
Flags and the rule for each (all inputs from 4.3/4.4):
- `isolated`: no ally within 1500 units while >= 2 enemies within 1500 (caught out); `distanceToNearestAlly`.
- `bkbOwned` (item in `m_hItems`), `bkbReadyAtSecMinus8` (cooldown), `bkbActiveAtDeath`
  (`modifier_black_king_bar_immune` active), `bkbExpiredWithinSec` (ended within 3 s before death),
  `bkbUsedThisFight` (ITEM use), `bkbUsedTooLate` (cast within 1 s of the first disable).
- `disableChainSec`: seconds under disables in the last 8 s; list of disables with `pierceBkb`. The pierce table comes from
  the ability constants (`bkbpierce` field in the OpenDota `abilities` constants; confirm it exists in S4, else a
  curated list in `resources/`). A disable applied while the victim had spell immunity and `pierceBkb = false` would
  be a bug: log and skip.
- `burstPct`: damage taken in the last 3 s / max health; `damageBySource`.
- `outOfPosition`: `frontlineDelta` from the fight row; for a carry (`rolePosition = 1`) a rank <= 2 in a fight
  where tanks were available.
- `noSaveItem`: no Glimmer/Lotus/Force/Eul/ghost scepter in the inventory while one was useful; `hadTpToEscape`.
- `reason` = first matching rule in the order `NO_BKB_OWNED, BKB_ON_COOLDOWN, BKB_USED_LATE, BKB_EXPIRED, PIERCING_DISABLE,
  DISABLE_CHAIN, CAUGHT_OUT, OUT_OF_POSITION, BURST, TOWER_DIVE, OTHER`, with all matching flags retained.
### 9.3 Focus group position
For the focus players the report shows (at each end-game fight start): `frontlineRank` vs expected, distance to the
team centroid, `hpPctAtStart`, `bkbReady`, `canBuyback`. These are `FightPlayerDomain` columns, no extra table.
### 9.4 Buybacks: `BuybackEventDomain`
`matchId, playerSlot, eventSec, goldBefore, goldAfter (cost), deathSec, secondsDeadSaved, fightIndex, gameState of the
team, fightWonAfter (the next fight within 60 s), usedWhileAhead`. Detection: increase of `m_iGoldSpentOnBuybacks`
and/or the combat log BUYBACK entry (S2) and a change of `m_flLastBuybackTime`.
Availability model for `canBuyback`: dead hero with `unspentGold >= buybackCost` and cooldown over
(`m_flBuybackCooldownTime`); the cost formula depends on level and net worth (S11: derive from observed
buybacks in the sample games before using a formula). `missedBuyback` = a player died in the last fight before the
ancient fell with `canBuyback = true` and did not use it; `BuybackReadinessDomain` is not needed, the flag lives on
`FightPlayerDomain`/`DeathAnalysisDomain`.
### 9.5 `EndGameAnalysisDomain` (1 row per game)
`matchId, endGameStartSec, decisiveFightIndex, losingTeamBuybacksAvailable, winningTeamBuybacksUsed, ancientFallSec,
lastCarryDeathIndex, primaryReason` plus refs to the supporting death/fight ids.

### 9.6 Acceptance
- Death count equals OpenDota `deaths` per player; each death has exactly one `reason`.
- BKB timeline validated by hand on 3 carry deaths (item in inventory, cooldown, immunity modifier window).

---------------------------------------------------------------------------------------------------

## 10. Group 6 - Post-game: find the spikes and their causes

### 10.1 Inputs
`ReplayPlayerMinute` (gold by source, net worth per player, XP), `TeamMinuteState`, `FightDomain/FightPlayerDomain`,
`DeathAnalysisDomain`, `BuildingKillContextDomain`, OpenDota `purchase_log` / `ItemPurchaseEventDomain` (key item timings),
`ObjectiveEventDomain` (Roshan, aegis), `BuybackEventDomain`, `LaneAssignmentDomain`. No replay needed (L2 only).

### 10.2 Spike detection
`D(t) = netWorth_radiant - netWorth_dire` and `X(t)` the XP difference at 30 s steps (use per-second team data NW, fall
back to the OpenDota `radiant_gold_adv/radiant_xp_adv` per minute for matches without replay).
A **spike** is a window `[t0, t1]` (60-180 s) where `|D(t1) - D(t0)| >= max(spikeMinGold (default 1500), spikePct
(default 3%) * (NW_r + NW_d))`, found with a sliding window plus merging of overlapping hits. A **direction change**
is a sign change of `D` with `|D|` exceeding `flipMinGold` (default 1000) on both sides within `flipWindowSec`
(default 240 s), or `leadPct` crossing from AHEAD to BEHIND (a state change from 4.7). Record both.

### 10.3 Causes of each spike (evidence ranking)
For the window compute the gold change of each team split by **source** (difference of the cumulative
`...Gold` columns: hero kills, creeps, neutrals, bounty, Roshan, buildings, comeback gold, courier, wards) and by
**player** (delta net worth), then attach events:
1. Fights in the window (their `winnerTeam`, kills, who died, `FightPlayerDomain.presence` anomalies such as a missing
   core with `ABSENT_FARMING`, `NO_TP`).
2. Deaths: single `DeathEventDomain` joined with `DeathAnalysisDomain` rows with high `goldLost` + the feed value (a core dying twice in 2 minutes).
3. Objectives: tower/barracks kills, Roshan/aegis, tormentor, with their gold share.
4. Item power spikes: a key item bought in the previous 3 min by a participant of the swing. A **key item** is
   config-driven (default: net-worth cost >= 2000 or one of Blink, BKB, Aghanim's Scepter/Shard, Black King Bar,
   Radiance, Butterfly, Satanic, Manta, Bloodstone, Refresher, Aeon Disk, Shiva's, Guinsoo; the list is a resource file).
   Distinguish "the item changed the next fight" (that fight's winner / damage jump) from "the item merely finished".
5. Farm spikes: one hero's creep/neutral gold rising with no fights, incl. a stacked ancient/lane farm without
   vision (join with the activity segments: where he was, for how long, was he alone).
6. Economy effects: `comebackGold`, `bountyGold` (rune windows), buyback spending, `goldLostToDeath`.
7. The other team's failure: their absences in the preceding fights (`ABSENT_*`), their deaths while ahead, no BKB on a
   carry that died, unspent gold held, a Roshan/aegis lost.
Cause attribution: normalise each factor to its share of the swing `|ΔD|`, keep the top 3 with at least `minShare`
(10%), flag `primaryCause`; shares may overlap, so store them as evidence not as an exact decomposition.
`primaryCause` enum: `FIGHT_WIN, PICKOFF, FEED, OBJECTIVE, ROSHAN_AEGIS, BUYBACK_SWING, CARRY_FARM, KEY_ITEM,
COMEBACK_GOLD, OTHER`.

### 10.4 Outliers: "one hero has a big net worth but no great fights"
For each player compute `nwShareOfTeam(t)` and the cumulative gold by source. Flag `NW_OUTLIER` when the share exceeds
`outlierShare` (default 35%) for >= 3 minutes and the player's `FightPlayerDomain` participation / damage rank is
below the team median. Explain it: the source with the largest contribution vs the team median, the change point of
his income slope (CUSUM on gold/min) and what happened there (item purchase, camp type, a kill streak, farming with no
fights, enemies feeding that hero), and the opponent side: enemy deaths to that hero, enemy absences. Also flag
`FEEDER` (net worth share of the team below `feederShare`, default 10%, for >= 5 minutes, and deaths above twice the team median) and `SHARED_GOLD` effects if
`m_iSharedGold` is large.

### 10.5 Tables
- `GameSpikeDomain`: `matchId, spikeIndex, startSec, endSec, benefitingTeam, deltaGold, deltaXp, deltaPct,
  directionChange (bool), leadBefore, leadAfter, primaryCause, confidence (0-1), analysisVersion`.
- `GameSpikeFactorDomain`: `spikeId, factorType (SOURCE|PLAYER|FIGHT|DEATH|OBJECTIVE|ITEM|FARM|ECONOMY|OPPONENT),
  slot (nullable), refType, refId, goldValue, sharePct, detail (jsonb)`.
- `PlayerOutlierDomain`: `matchId, playerSlot, kind, startSec, endSec, share, rank, detail (jsonb)`.
- Narrative text is generated at report time from these rows (deterministic templates; the OpenAI summary may consume
  the rows later); do not store prose.

### 10.6 Acceptance
- On the 2 sample games every game has >= 1 spike or a documented "even game" result; every spike has >= 1 factor with
  `sharePct >= 10`.
- Hand review of 5 spikes against the replay: the stated primary cause is defensible; record the reviewer's notes.

---------------------------------------------------------------------------------------------------

## 11. Storage design (consolidated)

Naming: `<Name>Domain` entities in `dao/model`, `<Name>Repo` in `dao/repo`, surrogate sequence id + unique natural key,
all keyed by `matchId` (+ `playerSlot`). jsonb and arrays through hypersistence. No foreign keys (existing style);
deletion goes through one service (`ReplayDataService.deleteByMatchId`) called from `MatchDeletionService`, mirroring
`MatchDetailService`.

| Table | Layer | Rows/game | Group | Notes |
|---|---|---|---|---|
| `ReplayParseRunDomain` | control | 1 | all | `matchId (unique per parserVersion), status, attempts, parserVersion, clarityVersion, startedAt, finishedAt, replayBytes, parseMillis, clockStartTick, pausedTicks, mapVersion, analysisConfig (jsonb), extractorStatus (jsonb), error` |
| `ReplayPlayerDomain` | L1 | 10 | all | identity and roster (4.2) |
| `AbilityDraftPickDomain` | L1 | 40 | 1 | |
| `AbilityDraftPoolDomain` | L1 | 48 | 1 | |
| `ReplayPlayerTrackDomain` | L1 | 10 | 2-5 | arrays, 5 s |
| `ReplayPlayerMinuteDomain` | L1 | ~450 | 2,6 | per player per minute; **extras only**, same key as `PlayerTimelineDomain` (3a.3) |
| `ReplayCombatLogDomain` | L1 | 10-20 per match as chunks | 3,5,6 | zstd-compressed `bytea` of the hero-relevant combat-log entries (damage, death, ability, item, whitelisted modifiers, gold, XP; ~15-20k entries, a few hundred KB). Makes the L2 tables recomputable after the replay expires |
| `ReplayStateChangeDomain` | L1 | ~3000 | 3,5 | inventory and ability change events (item gained/lost, cooldown started, level up, charges) per player; lets TP/BKB/cooldown/mana questions be answered without the replay |
| `RuneEventDomain` | L1 | ~25 | 2 | |
| `WardDomain` | L1 | ~40 | 2,4 | |
| `CourierEventDomain` | L1 | ~5 | 2 | |
| `LaneEquilibriumDomain` | L1 | ~120 | 2 | P1 |
| `PreGameSummaryDomain` | L2 | 10 | 2 | |
| `LaneAssignmentDomain` / `LaneMatchupDomain` | L2 | 10 / 3 | 2 | |
| `PlayerActivitySegmentDomain` | L2 | ~1500 | 3,4 | |
| `ModifierEventDomain` (whitelist) | L1 | ~1500 | 3,5 | `matchId, targetSlot, sourceSlot, modifierName, kind DISABLE|IMMUNE|TELEPORT|INVIS|OTHER, startSec, endSec, durationSec, ability` |
| `FightDomain` | L2 | ~40 | 3 | our detectors only; baseline is `TeamfightDomain` (3a.3). ~40 with skirmishes and engagements |
| `FightPlayerDomain` | L2 | ~400 | 3 | |
| `FightAbilityUseDomain` | L2 | ~2500 | 3 | |
| `FightDamageFlowDomain` | L2 | ~1600 | 3 | P1 |
| `BuildingKillContextDomain` | L1 | ~22 | 4 | links to `ObjectiveEventDomain` (3a.3) |
| `BuildingPressureDomain` | L1 | ~200 | 4 | P1 |
| `TeamMinuteStateDomain` | L2 | ~90 | 4 | |
| `GameStateSegmentDomain` | L2 | ~8 | 4 | |
| `PlayerPhaseSummaryDomain` | L2 | 30 | 4 | |
| `TeamBehaviorDomain` | L2 | ~6 | 4 | metrics jsonb |
| `DeathAnalysisDomain` | L2 | ~50 | 5 | 1:1 with `DeathEventDomain`, replay-only fields; `preDeathWindow` jsonb (the name `HeroDeathDomain` used in sections 7-10 means this table) |
| `BuybackEventDomain` | L1 | ~3 | 5 | |
| `EndGameAnalysisDomain` | L2 | 1 | 5 | |
| `GameSpikeDomain` / `GameSpikeFactorDomain` / `PlayerOutlierDomain` | L2 | ~8 / ~40 / ~3 | 6 | |
| `AbilityPairStatDomain`, `AbilityPickPositionStatDomain` | aggregate | global | 1 | nightly job |

Estimated volume: about 9,000 rows and 1.5-2 MB per game (arrays, jsonb and the combat-log blob dominate; Postgres
TOAST-compresses them). 1,000 games is about 2 GB, well inside the 40 GB disk; keep L1 forever, recompute L2 on demand.
**Recompute rule:** an L2 analyzer may only read L1 tables, OpenDota tables and the 3a.4 read interfaces. Anything
that needs a value that exists only during the parse (TP/BKB cooldown, per-second mana, the 8 s pre-death window,
creep kills, 1 s positions) must first be captured in an L1 table (`ReplayCombatLogDomain`, `ReplayStateChangeDomain`,
or the tracks); otherwise that table is frozen at parse time and says so in its `analysisVersion` note.
Insert with `saveAll` in batches of 25 (already configured); sequence generators with `allocationSize = 50`.
Raw combat-log events are **not** stored (57k per game); only the whitelisted modifier events, deaths with their
pre-death window, and per-fight aggregates are.
Indexes: unique natural keys as above; extra single-column index on `matchId` is redundant (leading column of the
unique key); add `(playerSlot-owner accountId, matchId)` indexes only when a query needs them (focus player lookups go
through `ReplayPlayerDomain.accountId`).
Queries for focus players join `ReplayPlayerDomain` on `accountId`, so the focus group stays a query parameter.

---------------------------------------------------------------------------------------------------

## 12. Work packages (execution order)

| WP | Scope | Depends on | Size | Done when |
|---|---|---|---|---|
| WP0 | Foundation per 3b: branch from fetched `origin/main`; dependencies; `ReplayDownloader`, `ReplayDecompressor`s, `ReplayParser`/`ClarityReplayParser`, `ReplayFacts` views, `ReplayRoster` with the OpenDota identity check, `ReplayParseRunDomain`, `ReplayProcessingProperties`, `ReplayProcessingJob` (3b.3: sequential, `max-concurrent` semaphore, quiet hours, `detailsMappedAt` entry condition), `ReplayDataService` + deletion hook. Spikes S1, S2, S3, S8 | - | M | one real replay is downloaded, parsed **inside the app at `-Xmx512m`** (INFO line with parseMillis, replayBytes, peak heap), and a `ReplayParseRun` row says `DONE` with identity rows; CI green |
| WP1 | Group 1 extractor + derived queries + aggregate job; AP draft capture (after S9) | WP0 | M | section 5.4 |
| WP2 | `ReplaySampler`, `CombatLogIndex`, `MapLayout`, `ActivitySegmenter`, `TeamStateSegmenter`, tracks and minute tables. Spikes S5, S7 | WP0 | L | track arrays and minute rows verified against counters; lane classification >= 90% vs OpenDota |
| WP3 | Group 2: runes, wards, couriers, lane equilibrium, pre-game summary, lane assignment/matchups | WP2 | M | section 6.5 |
| WP4 | Group 3: fight detectors, per-fight/player/ability analysis, front line, mana/cooldown/TP reasons. Spike S10 | WP2, WP3 | L | section 7.4 |
| WP5 | Group 4: building kills/pressure, phase summaries, state segments, team behaviour | WP2, WP4 | M | section 8.4 |
| WP6 | Group 5: deaths, BKB logic, buybacks, end game row. Spikes S4, S11 | WP4 | M | section 9.6 |
| WP7 | Group 6: spikes, factors, outliers. Spike S10 thresholds | WP3-WP6 | M | section 10.6 |
| WP8 | Reports: analyzers under `service/match/report/analyzer/` (draft, lane, fight, death, spike), `/stats draft`, `/match` replay detail, AI prompt inputs | WP1-WP7 | M | a Discord report shows draft + lane + fight findings for a focus player |
| WP9 | Operations: admin recompute of an `analysisVersion` (shares the one-replay semaphore), retention checks, disk/temp cleanup, load test with 20 replays one after another on the prod-sized heap (the single-replay memory check is already a WP0 exit criterion) | WP0-WP7 | S | 20 replays at `-Xmx512m` without GC thrash; temp files always deleted |

Order and parallelism: WP0 first. WP1 and WP2 can run in parallel after WP0. WP3 and WP4 depend on WP2 (WP4 also on
the lane results). WP5, WP6 follow WP4; WP7 last. Each WP is its own branch/PR; ask the user before merging.
Each WP lands behind `replay.<extractor>.enabled` so it can be turned off in production without a rollback.

**Test strategy.** Do not commit replays (40-75 MB). Extractors are written against small interfaces (`TickSource`,
`CombatLogSource`) so tests feed hand-built or recorded JSON fixtures. One opt-in integration test tagged `replay`
runs the real parser **locally only**: guard it with `@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")`
and exclude the `replay` tag from the Gradle `test` task, so CI never needs a replay file. Record golden outputs (a few KB JSON) for
the two spiked matches and assert on them. Run only targeted tests; ask before the full suite.

---------------------------------------------------------------------------------------------------

## 13. Spike backlog (do before or inside the WP that needs it)

| # | Question | Method | Blocks |
|---|---|---|---|
| S1 | Clock/coordinates: does `(tick - startTick - paused)/30` equal combat-log time minus `m_flGameStartTime`; is the position formula right; pauses | compare at 10 points on both replays; find a paused match via OpenDota `pauses` | WP0, WP2 |
| S2 | Combat-log field semantics per type (DAMAGE, DEATH, GOLD, XP, PURCHASE, ITEM, BUYBACK, MODIFIER_*): which of `value/valueName/health/targetSourceName` carry what; does an ABILITY entry carry a target/location | print 3 entries of each type and compare with the client/OpenDota | WP0, WP4 |
| S3 | Item/ability cooldown semantics (`m_fCooldown` remaining vs end time), mana cost field, TP scroll vs boots, charges | sample one hero every second around a known cast | WP4 |
| S4 | Does OpenDota `abilities` constants expose `bkbpierce`, `dispellable`, cast range; list of spell-immunity modifiers | read `/constants/abilities` | WP6 |
| S5 | Rune pickup and ward lifetime attribution (entity delete vs counters vs DEATH entries) | line up events on both replays | WP3 |
| S6 | Meaning of `m_unPickOrder`, `m_eLaneSelectionFlags` and `m_bHasRandomed` in Ability Draft | read values at draft end on 5 replays | WP1 |
| S7 | Map calibration: lane axes, jungle boxes, river, Roshan pit from tower/camp/spawner entities; `mapVersion` hash | plot positions of all heroes minutes 1-8 | WP2 |
| S8 | Zstd/bzip2 sniffing, arm64 native load of `zstd-jni`, temp file size/peak memory on the prod-size heap | run on the instance or a docker arm64 image | WP0 |
| S9 | All Pick replay: where the hero draft is in `CDOTAGamerulesProxy` during selection | parse one mode-22 replay (e.g. 8949600443 is too old; find a recent one) with the Timeline tool | WP1 |
| S10 | Fight and state thresholds: detector precision/recall vs OpenDota, hysteresis, engagement thresholds | 20 replays, scripted comparison | WP4, WP7 |
| S11 | Buyback cost model and availability; anonymous players (`steamId = 0`); Meepo/illusion/clone handling in the roster | find matches with buybacks and anonymous players via OpenDota | WP6 |

The throwaway spike programs used so far (Dump, Fields, AbilityFields, Heroes, Timeline, DraftSnapshot, Probe,
CombatProbe, ItemProbe) are kept in `scripts/replay-spikes/` with their `build.gradle` (not part of the build; replays
are not committed). Each takes the replay path as `args[0]`: `gradle run -Pmc=spike.Timeline --args=<file.dem>`.
Useful Clarity 5.0.1 facts: `OnTickStart` is in `skadistats.clarity.processor.reader`; use
`Entities.stream()` with `Entities.byDtName`, `getByHandle`, `EntityState.fieldPathIterator()`;
`@OnCombatLogEntry` in `processor.gameevents`; string table names via `StringTables.forName("EntityNames")`.
Entities are cleaned up at the end of the replay, so snapshot mid-game or while events stream.

---------------------------------------------------------------------------------------------------

## 14. Decisions the user may change (defaults chosen)

| Decision | Default |
|---|---|
| Resolution of stored tracks | 5 s arrays for the whole game; 1 minute rows for stats |
| What is stored | all 10 players; focus group applied at query time |
| Retention | L1 kept forever, L2 recomputable; raw combat log never stored |
| Modes | Ability Draft (18) and All Draft/"All Pick" (22) only |
| Replay age limit | 90 days; older matches get `EXPIRED` |
| Concurrency | one replay parse at a time, configurable `replay.processing.max-concurrent` (default 1); queue settings in 3b.3; quiet hours respected for downloads |
| Table overlap with PR #6 | extend the OpenDota tables with replay-only columns/tables, never copy (3a.3) |
| Thresholds | all in `replay.analysis.*`, snapshot in `ReplayParseRun.analysisConfig` |
| Lane result rule | relative lane net worth at 10:00 differing by > 8% = win |
| Fight baseline | OpenDota rule (>= 3 deaths, 15 s cooldown) plus skirmish and engagement layers |
| Team state | +/-5% net worth lead with 120 s hysteresis |

## 15. Reporting examples this enables (for WP8)
- "You picked 4th overall and 2nd on your team; skills still available at your turn: ... (win rate when taken: ...);
  the enemy answered with ..."
- "Lane: you lost the lane (-14% net worth at 10:00) as a 1v2 offlaner; the enemy took both bounty runes at 3:00;
  first tower fell at 11:20."
- "Fight at 23:10: you were 3000 units away farming the jungle with no TP scroll (last scroll bought at 6:40); the team
  lost 4-1 and the enemy took the tower within 40 s."
- "In 3 of 5 fights you had Ravage ready with enough mana but did not cast it (HELD_BACK); in the 24:30 fight you had
  used it on creeps 30 s before."
- "Carry death at 41:10: no BKB owned; Sleep and Chain Frost chained for 4.2 s; teammates 2600 units away."
- "Swing at 28:00-31:00 (+7.4k for Dire): a won fight (62%) and the Roshan/aegis (21%); the Dire core had bought
  Blink 90 s earlier and his next three fights were the best of the game."
