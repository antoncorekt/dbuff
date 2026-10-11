# Ability Draft replay spike (Phase 2) - findings

Date: 2026-10-11. Matches parsed: 9038725154 (2026-10-10) and 9037038717 (2026-10-09), both Ability Draft.
Library: Clarity 5.0.1 on JDK 21. Throwaway code lived outside the repo; the reading logic is summarised below.

## Result
Everything needed for Ability Draft analytics is in the replay, in one game-rules array. In both matches the
reconstructed draft matched OpenDota exactly: for 10 of 10 players in each match, the 4 skills the replay says were
drafted are exactly the 4 non-talent skills OpenDota recorded as levelled.

## Where the data is
Entity `CDOTAGamerulesProxy`, array `m_pGameRules.m_AbilityDraftAbilities.NNNN` (48 entries in both matches):

| Field | Meaning |
|---|---|
| `m_nAbilityID` | ability, **2 x the OpenDota/dotaconstants ability id** (2210 -> 1105, 10096 -> 5048); verified on 96 entries, 0 mismatches |
| `m_unPlayerID` | picker, **2 x player index**: 0,2,4,6,8 Radiant, 10..18 Dire; `20` = not picked |
| `m_unAbilityPlayerSlot` | position of the skill on the hero: 0,1,2 = regular skills, 3 = ultimate |

Related: `m_pGameRules.m_nHeroPickState`, `m_flHeroPickStateTransitionTime` (advances about every 7 s per pick),
`m_nGameState`.

## How to read it
- **Pool**: the array is created once with all 48 entries unassigned (`m_unPlayerID = 20`, slot 0). Pool size is 48 in
  both matches, not 60: 40 are picked and 8 are never picked. (The 48 + 12 figure quoted on Liquipedia does not hold in
  the current patch.)
- **Pick order**: each pick is an update of one entry's `m_unPlayerID` from 20 to a player id. There were exactly 40
  such updates per match, none reassigned. Order = order of the updates; time = the tick (30 ticks/s). The sequence
  starts R, D, R, D, ... (Radiant first) and is not strictly alternating afterwards.
- **Skill position**: `m_unAbilityPlayerSlot` is updated after the pick when a player moves a skill, so read its final
  value, not its value at pick time.
- **Skipped skills**: entries still at `m_unPlayerID = 20` after the draft. Names come from halving the id and looking
  it up in the existing ability-id dictionary (all 16 unpicked skills in the two matches resolved).
- **Player identity**: `CDOTAPlayerController.m_nPlayerID` (same x2 encoding) and `m_steamID`
  (account id = steamID - 76561197960265728), so each pick maps straight to an account without slot guesswork.
- **Hero body**: `CDOTAPlayerController.m_hAssignedHero` -> hero entity; its skills sit in `m_vecAbilities.NNNN`
  (note: not `m_hAbilities`): slots 0-2 regular, 5 = ultimate. The entity name comes from string table
  `EntityNames` via `m_pEntity.m_nameStringTableIndex`. This is only needed as a cross-check; the id mapping above
  already gives names.
- Entities are gone by the end of the replay: snapshots must be taken mid-game (e.g. tick >= 20000), and pick
  events must be captured while the draft runs (`@OnEntityUpdated(classPattern = "CDOTAGamerulesProxy")`).

## Things that surprised us
- `*.dem.bz2` URLs now serve **Zstandard**, not bzip2. The downloader must sniff the magic bytes (`28 B5 2F FD` =
  zstd, `BZh` = bzip2) instead of trusting the extension. The result is a Source 2 demo (`PBDEMS2`).
- Replay download windows: about 3 months (see the main research doc).
- Clarity 5.0.1 API: `OnTickStart` lives in `skadistats.clarity.processor.reader`; entity streams come from
  `Entities.stream()` with `Entities.byDtName(..)`; `EntityState.fieldPathIterator()` enumerates fields.
- The OpenDota ability-id dictionary stops at id 9999, which covers every replay id once halved (max seen 15420 / 2).

## Cost
Full parse of a 76 MB replay: about 1.5 s wall time, about 365 MB RSS with `-Xmx256m`. Downloads are 40-45 MB.
Parsing is not the constraint on the 2 GiB instance; keeping one parse at a time is enough.

## Not covered / limits
- A pick made by the draft timeout (random assignment) cannot be told apart from a chosen one in this data.
- The spike did not read per-second positions, combat log or modifiers; those are Phase 3 extractors and use known
  Clarity APIs (see OpenDota's `Parse.java`).
- Two matches is a small sample; an extractor should log and skip a match whose draft does not have 48 entries and
  40 picks instead of storing partial data.

## What this changes in the plan
- `draft_event` for Ability Draft: 40 rows per match (seq, tick, team, player slot/account, ability id, final skill
  position).
- `ability_draft_pool`: 48 rows per match with `picked_by_slot`/`pick_seq` NULL for the 8 skipped skills.
- The Stratz fallback is not needed.
- Phase 3 should keep the draft extractor independent of the heavier extractors: it can stop reading the replay once
  the game state reaches PLAYING, so it only needs the first part of the file.
