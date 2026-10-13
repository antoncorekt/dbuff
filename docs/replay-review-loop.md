# Review loop for replay analysis

A way to improve any analysis rule on real matches without a labelled data set: run the pipeline on a short, fixed list of matches, read a generated report of what the analyzers stored, and change the rules until the owner accepts the output. It was built for roles (`docs/replay-role-loop/README.md` has the commands and the round format); this page is how to use the same method for other work: fight reasons, deaths, swings, outliers, buyback, report text, a new mode, a new analyzer.

## When to use it

Use it when the question is "does this output look right to someone who knows Dota?" and there is no number to check against: a rule or threshold changed, a new analyzer was added, a reason or verdict is suspected to be wrong, or a report section is rewritten. Do not use it for plumbing that unit tests can prove (parsing, storage, queue order).

## The method

1. **Fix the match list.** A small list of match ids in a text file next to the topic (roles: `server/src/test/resources/replay/role-loop/matches.txt`). Pick matches that show the cases you care about, and add a match only when a case is missing. Replays expire after about 3 months, so keep the `.dem.zst` and OpenDota `.json` of each in the local fixture directory (never in git).
2. **Produce stored data, once.** The first round parses each replay against a local throwaway PostgreSQL and stores everything the real pipeline would store. Later rounds re-analyse from the stored data (the same path as `POST /api/v1/replay/{matchId}/reanalyze`), so a round takes seconds and needs no replay. Keep the container for the whole loop.
3. **Render a report from the stored tables only.** The report reads what the analyzers wrote and OpenDota's own data for comparison. It never runs analysis logic, so what you read is what production would hold. One file per match, plus a summary with a diff against the previous round.
4. **Review in rounds.** The assistant reads the report first and writes a verdict per row in `docs/<topic>-loop/round-NN.md` (`OK` or `REJECT` with the expected value), then changes the rules, bumps the analyzer version and runs the next round. The owner reviews the new round, the assistant changes again, and so on until the owner accepts. Each round is committed so the history of decisions is kept.
5. **Never regress.** Rows the owner accepted stay accepted. The diff against the previous round lists every changed row, so a change that moves an accepted row is visible at once.
6. **Accepted** means the owner said so. A suggested bar for a rule set is: nothing important rejected and at most one near miss per match.

## Rules of the loop

- Local only: it runs against a PostgreSQL on localhost (the task refuses any other host before it writes anything), never touches production or AWS, and is excluded from CI and from `./gradlew test`.
- The task never changes the analysis. A rule change is a normal code change with tests, reviewed like any other; the loop only shows its effect.
- Numbers in rules are hypotheses until a round accepts them. Keep them in one properties class, not scattered in code.
- Unknown is not zero. A function with no data stays unknown, the report says so, and rules must not raise a verdict from unknown data.
- Prefer a data fix over a threshold tweak: a missing override, a missing data source or a wrong input beats a new weight (round 1 of the roles showed a drafted attack-damage skill scored as no damage; the fix is an override entry, not a new weight).

## Making a report easy to read

Every topic's report is written for a person, with one shared formatter, `LoopWording` (same package as the runner), so all topics read the same:

- Plain words, not codes: the hero's name (from `ReportHeroNames`; "Dire player 2" when unknown, never an id), a reason as a sentence from the production `ReasonWording` ("Mirana stood too far in front"), composition tokens and enum names in words, roles as "1 carry" to "5 hard support". No stored code, raw enum name or `npc_dota_hero_` id in a table cell; a code that someone may need to trace goes in a notes list under the tables, never in a cell.
- Time as `m:ss` (`LoopWording.clock`), gold as `850` or `1.2k` (`gold`), teams as Radiant and Dire (`team`).
- After the title line, `ReviewLoopReport.introduction(howToRead, summary)`: a "how to read this" line and a one-line summary (counts, the main finding).
- One short table per question with few columns: the verdict or result first (it is also the diff key, so it must be unique per row), the numbers behind it, then the empty `assistant` and `owner` columns last.
- The diff already shows only what changed between rounds; a round in a changed layout is reported once as "format changed", then compares normally.

When a topic needs a new kind of value, add it to `LoopWording` with a test instead of formatting it in the topic.

## Adding a new topic

The topic-free parts live in `server/src/test/java/com/ako/dbuff/service/replay/looprunner` (round numbering `ReviewLoopRound`, match list `ReviewLoopMatchList`, diff `ReviewLoopDiff`, the "not reported" stand-in and report introduction `ReviewLoopReport`, the shared wording `LoopWording`, the round itself `ReviewLoopTest`), the local database guard is `LocalDatabase` in the smoke package, and the Gradle task is `:server:reviewLoop` (JUnit tag `reviewloop`, topic picked with `-Ploop.topic=<name>`, default `roles`; `:server:roleLoop` is kept as an alias of the roles topic). Topics so far: `roles` (`docs/replay-role-loop/README.md`) and `fights` (`docs/replay-fight-loop/README.md`, `FightLoopTopic` in the `fightloop` package). A topic is one implementation of `ReviewLoopTopic`, a Spring `@Component` next to its renderer (roles: `RoleLoopTopic` and `RoleLoopReport` in the `roleloop` package). To add a topic:

1. Implement `ReviewLoopTopic`: its `name()` (the `-Ploop.topic` value), match list, output directory, the tables the diff compares (`ReviewLoopDiff.Section`: heading, key label, key columns) and `render(matchId)`, a renderer that reads its stored tables (for example fights: `fight`, `fight_player`, `fight_ability_use`; deaths: `death_analysis`; swings: `game_spike`, `game_spike_factor`) and prints, per row, the verdict next to the evidence behind it and the OpenDota fact to compare with, written with `LoopWording` as above. Do not copy the runner parts (DRY).
2. Add a match list (or a section of the same list) under `server/src/test/resources/replay/<topic>-loop/`.
3. Add `docs/<topic>-loop/README.md` (short: what to look at) and a round template.
4. Make the rule's analyzer version bump part of every round that changes it, so the re-analysis replaces the old rows.

## Checklist for a round

- the container is up and the fixture directory has every listed match;
- the previous round's report exists (the diff needs it) and the last accepted round is known;
- the code under review is built from the branch you mean (the task builds the checkout it is run in);
- every row with a verdict has an expected value when it is rejected;
- the round file is committed with the change that follows from it.
