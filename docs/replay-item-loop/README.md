# Items learning loop

How well the whitelisted items paid for each hero, shaped by review rounds on a small fixed list of matches. The method and the shared runner are in `docs/replay-review-loop.md`; the plan is `docs/superpowers/plans/2026-10-13-replay-next-analyses-plan.md` (section 11, A5). This file is what is specific to the `items` topic. It is separate from the OpenDota based purchase report of the match report (`ItemEfficiencyConfig`, `dbuff.report.item-efficiency.*`), which is not touched.

## What is stored

`ReplayItemEfficiencyAnalyzer` (`item-efficiency`, `@Order(85)`; requires `players`, `tracks`, `state-changes`, `combat-log`, `activity`, `creep-kills`; all game modes) reads the whitelist `server/src/main/resources/replay/item-efficiency.json` and writes `item_efficiency`, one row per whitelist entry and hero who held one of its items: entry key, item (the first held), kind, purchase second (first `ITEM_ADDED`), count, `gain` (gold, experience at the entry's weight), `cost` (from OpenDota's item constants, never in code), `ratio` (gain divided by cost), payback second, `figures` (totals particular to the kind, JSON text) and `details` (flags and the per-use rows, JSON text). One table with two JSON columns was chosen over a child table: the rows are only read back whole, by the report, never queried by a column. The OpenDota purchase log (`ItemPurchaseEventDomain`) is not read: it is filled by the match import, not by the replay run, so the replay's own `ITEM_ADDED` time is used (accurate to the 5 s sampling grid).

All numbers are first guesses in the entry's `parameters`, tuned in the rounds.

Every parameter is read as a decimal and never rounded. An item the constants lack, or give no cost, leaves its rows with `COST_UNKNOWN` and no ratio or payback (one WARN line per entry and item); an unknown kind or parameter name fails the run once with every offending entry named. Bump the analyzer's `version()` whenever a default of an evaluator changes.

## Magic Stick (kind `magic-stick`, items `magic_stick`, `magic_wand`)

The stick and the wand are one chain (the stick becomes the wand), costed as the wand when the hero held it. Each use is an `ITEM` event of the hero in the combat log (exact second). The charges are the last `ITEM_CHARGES` row at or before the use (E3). Health restored is `charges * restorePerCharge` (15), capped by what he was missing; mana likewise.

- Max health is not stored. It is estimated as health after a hit (the `DAMAGE` row's `health`) divided by the track's hp percentage when the hit is within 1 s of a 5 s sample with no other hit between (percentage 15 to 99), the median within `maxHpWindowSeconds` (120) of the use. Health at the use is the last hit's health when it was at most `hpLookbackSeconds` (3) before, else the sample before times the estimate. Mana and its maximum are the track sample at or before the use. When either is unknown the use counts every charge as restored and the row carries `HP_UNCAPPED` or `MANA_UNCAPPED`.
- Gain is health at `goldPerHp` (0.3) plus mana at `goldPerMana` (0.3) plus saves at `saveGold` (300). The two prices are the gold per point of salve and clarity, rounded.
- A **save** is a use at low health (at most `lowHpFraction` (0.35) of max health, or `lowHpAbsolute` (300) when max health is unknown) with heavy damage from others in the window from `damageBeforeSeconds` (3) before to `damageAfterSeconds` (3) after (at least `heavyDamageFraction` (0.3) of max health, or `heavyDamageAbsolute` (300)), after which the hero has no death within `surviveSeconds` (10). A death in that time is `diedSoon`, never a save. A use before `laningEndSeconds` (600) is a laning use.

## Hand of Midas (kind `hand-of-midas`, item `hand_of_midas`)

A use is an `ITEM` event of the hero with the creep as target. On the fixtures every use pays exactly 160 gold (the constants' `bonus_gold`) as a `GOLD` entry at the same second, in place of the creep's normal bounty, and the creep's experience as an `XP` entry (not doubled in this patch, and not shared). The evaluator pairs each use with the nearest unclaimed positive `GOLD` entry of that value (none is claimed, and the row flagged `BONUS_GOLD_UNKNOWN`, when the constants give no bonus) and the nearest unclaimed `XP` entry with the creep-kill reason, both within 0.3 s. The gain is gold plus `xpWeight` (1) times experience; payback is the use at which the running total passed the cost; efficiency is gain divided by cost at the end of the game. The hero's whole-game gold and experience are summed from the combat log for comparison, and the gold above a normal kill of the same creep is estimated from `replay/creep-values.json` (`EXTRA_ESTIMATED`).

The replay shows no `COOLDOWN` row for the Midas (the constants give it no cooldown either; it is a charged item), so the ready time after a use is the use plus `cooldownSeconds` (90, the measured gaps are about 89 s) and the row carries `COOLDOWN_ESTIMATED`; a cooldown the replay does show within 6 s of the use is used instead. Idle time is the time ready but unused while the activity segments say lane or jungle farming.

## Farming group (kind `farming-group`, items `bfury`, `maelstrom`, `mjollnir`)

For a hero who held one of the items: creep kills, creep gold (from `creep_kill`) and camps cleared per minute in the `windowMinutes` (5) before and after his first group item. A camp is a run of neutral kills in one camp each within `campGapSeconds` (30) of the last. Net gain per minute is the gold rate after less the rate before; gain is that times the minutes to the end of the game; the gain counts only the minutes the hero owned the item (until he sold or lost it), payback is when that rate would have paid the cost, and the cost is that of the most expensive group item he held (the final item of an upgrade chain such as Maelstrom to Mjollnir; two unrelated group items count as the dearer one). A window with no length (an item bought before the horn has no before window) has no figures at all, not zeros. A window shorter than `minBeforeSeconds` (120) or `minAfterSeconds` (60) is not compared (`NO_BEFORE_WINDOW`, `SHORT_AFTER_WINDOW`). It is a before and after comparison, not a proof: a hero's farm changes with the game.

With `includeDamage` (1 in the file) the worth gets a second part, damage to enemy heroes, because Mjollnir, Maelstrom and Battle Fury also hit harder. Both pieces use only damage to heroes (what the item does to creeps is already in the creep gold):

- **The item's own damage**: `DAMAGE` entries of the combat log whose inflictor is a group item (`item_maelstrom` and `item_mjollnir` chain lightning and static charge, `item_bfury` cleave), summed from the purchase to the end of ownership. Exact. Radiance has no entry yet; its burn would be an `item_radiance`-less aura inflictor to check first.
- **The uplift of its stats** (attack damage and speed, named in the detail row from the constants' attributes): the hero's damage to heroes per minute in `damageWindowMinutes` (5) after the purchase less the window before, less the item's own damage per minute, kept at 0 or more, carried over the minutes owned. The hero's damage is summed from the combat log (exact seconds, present in every replay run) rather than OpenDota's per-minute `heroDamage`, which a replay-only run does not have. The stage of the game confounds this part: a hero deals more damage later whatever he holds, so it is the soft part of the number. It needs both windows to be as long as `minBeforeSeconds` and `minAfterSeconds`, else `NO_DAMAGE_COMPARISON` and only the item's own damage counts.
- **In gold**: `damagePrice` (100) gold per 1000 damage, a first guess. The ratio is then (creep gain + damage value) divided by the cost; the result sentence gives the creeps part, the damage part and the total ("creeps +0.4x its cost, damage +0.3x, 0.7x in total"), and a `DAMAGE` detail row gives the damage, the price and the rates before and after. Payback uses the creep gold rate plus the damage value rate.

Hand of Midas gets only a note: `includeAttackSpeed` puts the constants' `bonus_attack_speed` (35) beside the result as an extra that is named and not counted in the gain.

## How to add an item

1. An item of a kind that exists: add an entry to `item-efficiency.json` (`key`, `kind`, `items` as OpenDota's constants keys without `item_`, optional `parameters`). A key that is already listed, an `item_` prefix, a missing kind or a negative parameter stops that run with the entry named; the application still starts, because the file is read on first use. A group is an entry with several items (the first group item bought starts the measurement).
2. An item whose efficiency means something else: one new class implementing `ItemEfficiencyEvaluator` (`supports(entry)`, `parameterNames()`, `evaluate(EntryContext)`), annotated `@Component`, and an entry of its new `kind`. The analyzer collects evaluators as a list and the entries as data, so it does not change; `EntryContext` gives the entry, the constants (`ItemKits.cost`) and every hero's stored facts (`HeroInputs`: state changes, combat log entries of or about him, track, creep kills, activity). A parameter name the evaluator does not list is rejected.
3. For the report, add the kind's wording to `Wording.FOR_KIND` in the `itemloop` test package (one small class); without it the rows are shown with their raw totals.
4. Bump `version()` of the analyzer when a rule changes, and add the entry to the smoke test's checks.

## What the data cannot tell

- Max health is never stored: it is an estimate from hits near track samples, and a use with no hit nearby in time gets none (then every charge counts).
- The 5 s track grid puts the health and mana for a use up to 5 s before it; charges are exact to the 5 s sampling grid as well.
- Items other than the whitelisted ones, and the stick's replenishing by enemy spells (it is the charge count that is read), are not judged.
- A save is judged from health, damage and survival only; a hero who would have lived anyway counts, and one saved by a team mate's heal does too.
- The Midas gold is 160 for every use on the fixtures; the experience is the creep's own. Whether the hero would have killed the creep anyway is not known, so the gain is what the use gave, and the "more than a normal kill" figure is an estimate.
- The farming comparison cannot tell the item from the stage of the game.

## Commands

The container, fixture directory and variables are those of `docs/replay-role-loop/README.md`.

```
docker run -d --name itemloop-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5544:5432 postgres:16-alpine
REPLAY_FIXTURE_DIR=/path/to/replays ./gradlew :server:reviewLoop -Ploop.topic=items
docker rm -f itemloop-pg
```

The match list is `server/src/test/resources/replay/item-loop/matches.txt` (9038725154 and 9037038717); reports go to `server/build/item-loop/round-NN/`.

## Output

One `<matchId>.md` per match and `summary.md` with the diff against the previous round, in the layout of `docs/replay-review-loop.md`: a "how to read this" line and a one-line summary, then two tables compared between rounds:

- `Items` (key: hero and item): bought, uses, the result in words ("18 uses, gave 2.9k gold and 1.2k XP, paid for itself at 25:59, worth 1.9x its cost") with the limits of the data in brackets, and the efficiency ratio.
- `Item details` (key: hero, item and use number or payback): time, on what (the creep), what it gave, and the evidence (health, damage, verdict for a stick use; idle time and gain so far for a Midas use; the measured window for the farming group).

The `assistant` and `owner` columns are empty, last.

## Reviewing

Copy `round-template.md` to `round-NN.md` and fill a row per hero and item. Look first at: saves (was the hero really about to die), the "died within 10 s" uses, the prices per health and mana point, the Midas idle time, and the farming comparisons (does the before window mean anything for a hero who bought the item at 10:00).
