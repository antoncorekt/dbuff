# Discord reports: stages, display, settings, icons, graphs, AI top-5 reasons

Plan of 2026-10-14. Nothing here is built yet. Every new report or feature ships off in production; replay processing and the replay report stay off until the owner says otherwise. Each step below is a PR with its own worktree, `code-review`, CI on its head and the owner's word to merge.

## 1. What exists today

- **Discord library:** JDA 6.3.0. The bot uses plain text, buttons and a few embeds. JDA 6.3 also has Components V2 (`Container`, `Section`, `TextDisplay`, `Separator`, `Thumbnail`, `MediaGallery`) and application emoji (checked in the jar, see section 6).
- **Match lifecycle in the channel** (current code):
  - New match found (`LastMatchesProcessorService`): OpenDota parse request submitted, a plain-text header "Match N - waiting for parsed data..." is posted, a thread `Match N` is created with the partial-data analyzers, and a Quartz job (`MatchParseCheckJob`) polls OpenDota.
  - OpenDota parsed (`MatchParseCheckJob`): the header is edited with result and score and the full OpenDota-based report is posted into the thread (`processAndReportFull`).
  - Replay run DONE or PARTIAL (`ReplayReportService`, off in production): the replay sections are posted, each section gated by `replay.report.sections.<name>.enabled`.
- **Settings:** per channel `/dbuff settings` (`ai_summary`, `replay_report`, `detail`, quiet hours, timezone). `/match rerun` and `/match retry` work inside a `Match <id>` thread; the match id comes from the thread name.
- **AI feature:** `OpenAiMatchAnalysisService` with `AiPromptBuilder`; prompt hard-coded to Ability Draft, built from OpenDota match, player, ability and item fields, truncated by characters (4 per token) against a 120k context; not connected to the replay analysis.
- **Data sizes (match 9038725154):** all rendered replay sections together about 4.4 KB (about 1.2k tokens); the full `/analysis` JSON about 21 KB (about 6k tokens).

## 2. Three stages of a match thread

| Stage | Trigger | Header title | Thread content |
|---|---|---|---|
| 1 | New match seen; result known from the recent-matches list | Result, score, mode, duration; "OpenDota parse requested" | One basic card, always: kills, deaths, assists per tracked player, hero, chosen abilities |
| 2 | OpenDota parse done, match stored, replay job queued | Same card; "replay processing" | Nothing new |
| 3 | Replay run DONE or PARTIAL | "replay analysis ready" | The sections chosen by the global and channel settings (none by default) |

- The old OpenDota "full" report stops being the default. Its analyzers (items, abilities, damage, lanes) become on-demand sections that work from stage 2 data, so a failed stage 3 can still be asked for.
- Failure and gaps, every report is safe for missing data (a section with no data says "no data" or is skipped):
  - Replay run FAILED or SKIPPED: the title says the replay analysis is not available and offers `/match report`.
  - OpenDota never parses (timeout): the title says so; the basic card stays.
  - Replay processing switched off (the situation in production today): the lifecycle ends at stage 2 and the title reads "OpenDota data ready", never promising a replay analysis.
- One status model: `MatchReportStage` (REQUESTED, PARSED, REPLAY_PENDING, REPLAY_DONE, REPLAY_UNAVAILABLE, PARSE_TIMED_OUT) stored with the thread row. One `HeaderRenderer` builds the title from it; today the header text is built in three places in `MatchReportOrchestrator`.
- Open check: a plain-text message may not be editable into a Components V2 message (the flag cannot be removed and `content` is disabled). Safe route: create the header as V2 from the start; verify with a real send, including creating the thread from a V2 message.
- Owner decision (default): while replay processing is off, the old OpenDota report is still posted automatically in stage 2 so channels do not lose it; once replay is on it becomes on-demand only.

## 3. How reports are shown

| Option | Use | Limits |
|---|---|---|
| Plain markdown text (now) | Fallback | Flat, 2000 characters per message |
| Embeds | Cards | 6000 characters, 10 embeds per message, 1024 per field |
| Components V2 container | Main format: one message per section, accent colour, text blocks, separators, thumbnail | About 4000 characters total and 40 components per message (third-party docs disagree on a per-block cap: test with a real send); a `Section` holds at most 3 text blocks plus one accessory; `MediaGallery` up to 10 items; the V2 flag cannot be removed and `content` and embeds are disabled |
| Buttons, select menus | Choose a section, page through fights | Interaction handler; self-describing button ids survive restarts |
| Slash command, ephemeral reply | Report only for the asker | Others do not see it |

- Seam: a `SectionView` model (title, verdict, accent, bullets, optional detail, optional image) rendered by a `DiscordRenderer` (Components V2) with a plain-text renderer as fallback. Sections stop returning raw strings. `important-fights` moves first so the owner can judge it in a real channel before the rest follow.

## 4. Settings and commands

- Global default: `replay.report.default-sections` (list of section names; empty by default). The existing per-section `enabled` property stays as an override.
- Per channel: `/dbuff settings sections:<list>` overrides the global list (new nullable column in `InstanceSettings`, created by `ddl-auto=update`).
- On demand inside a match thread: `/match report <section|all>` (autocomplete from section names) and `/match ai`; text aliases `!report <section>`. A request for a section whose data does not exist says so. A per-thread cooldown stops spam.
- Default behaviour: no section posts automatically; the thread shows the stage 1 card and the title updates. Existing section names are reused (`important-fights`, `lanes`, `rotations`, `smoke`, `objectives`, `vision`, `opening`, `items`, `outliers`, `endgame`); on-demand OpenDota sections are added beside them.

## 5. Hero, ability and item icons

### Source and data

- Image paths are in OpenDota constants (`img`), host `https://cdn.cloudflare.steamstatic.com`. Counted on the real constants: heroes 127 (all with `img`), abilities with `img` and `dname` and not a talent 1056 (1747 entries with a name but without `img` are talents, `special_bonus_*`, which have no icon), items 406 non-recipe (501 with recipes; 49 are neutral items).
- The stored constant classes (`HeroConstant`, `AbilityConstant`, `ItemConstant`) do not keep `img`. Adding the field means raising the constant shape version (`BaseApiConstantService` documents this), or the sync reads the OpenDota constants JSON directly.

### How to show icons in Components V2

- **Inline icons in a text line** only through emoji. Application emoji belong to the bot, work in every server it is in and in Components V2 text blocks (`<:name:id>`).
- One `Thumbnail` per `Section` (URL or attached file) shows a larger image beside the text; `MediaGallery` up to 10 images.
- Emoji count: heroes 127 + abilities 1056 + items 406 = 1589 of the 2000 allowed (`ApplicationEmoji.MAX_APPLICATION_EMOJIS` = 2000). It fits with about 410 spare, but little else may use that quota. Plan: sync heroes and items fully, abilities on demand for abilities that appear in tracked matches (so the usual count stays below the full 1056), with a configured hard limit (default 1900) after which no new emoji are created.

### Checked in the JDA 6.3.0 jar (not yet exercised against Discord)

- `JDA.createApplicationEmoji(String name, Icon icon)` returns a `RestAction<ApplicationEmoji>`; `JDA.retrieveApplicationEmojis()` and `retrieveApplicationEmojiById(long|String)` exist; `ApplicationEmoji.delete()`, `getManager().setName(...)` exist; there is no method to replace the image, so a changed image means delete and create (new id).
- `Icon.from(File|InputStream|byte[])` builds the upload; `CustomEmoji.getFormatted()` and `getAsMention()` give `<:name:id>`; `Emoji.fromCustom(name, id, animated)` and `Emoji.fromFormatted(String)` build one from stored values; `EMOJI_NAME_MAX_LENGTH` = 32 (Discord also requires 2 to 32 letters, digits and underscores; to confirm with a real create).
- `Thumbnail.fromUrl`, `Thumbnail.fromFile(FileUpload)`, `withDescription` (max 1024); `Section.of(accessory, contents...)` with at most 3 text blocks; `MediaGallery.of(...)` max 10; `Container.of(...)` with `withAccentColor`; `useComponentsV2(true)` on both create and edit builders.
- Image limits (about 256 KB, small PNG) come from Discord's documentation, not the jar; to confirm with a real upload. Plan: resize to 128 px PNG.

### Sync and storage

- An owner-triggered job, never at start-up: read the constants, download each image, resize to 128 px PNG, keep the file in a volume directory (so posting never depends on the CDN), create the app emoji, and record `kind, key, emoji_id, emoji_name, image_hash, status` in a table `discord_icon`. Idempotent: only missing or changed icons are created; a changed image deletes the old emoji and creates a new one. Rate-limited, with the Discord and CDN failures recorded per icon, not fatal.
- Emoji name rule: a stable safe mapping, for example `h_<key>`, `a_<key>`, `i_<key>` trimmed to 32 characters; a collision after trimming is resolved by a short hash suffix. The mapping lives in one class.
- At start-up (when icons are enabled) the table is checked against `retrieveApplicationEmojis()` once, so an emoji deleted by hand in the developer portal is recreated or marked missing instead of posting a broken `<:name:id>`.

### When there is no icon

Every icon goes through one `IconService` with a fixed fallback chain, so no section needs its own handling:

1. The synced emoji (`<:name:id>`).
2. A generic placeholder per kind (hero, ability, item): a plain Unicode emoji such as a sword, a book or a bag, or an optional synced placeholder emoji; never an empty box or a broken tag.
3. Nothing: the text name alone (names are always present, icons only decorate).

Cases covered:
- Talents and other entries without `img`: placeholder or text only, by design.
- Not yet synced icon, failed download, failed upload, quota reached, name collision: placeholder, and a count in a metric.
- Icons switched off (default): the renderer never calls the service and output is identical to text only.
- The emoji was deleted on the Discord side: detected by the start-up check, and a post that references a missing emoji shows as text on the client without failing the message.
- The same fallback logic is unit-tested with a fake service; the real upload is checked once in a test application.

## 6. Graphs (PNG attached to a section message)

Rendered server-side (Java2D or JFreeChart); no external chart service, so match data stays on the server. The container image needs headless AWT and fonts; check the deploy image before choosing. A size cap, a concurrency limit and a per-match cache of the PNG bytes keep cost low. Each graph is its own section (`graph-advantage`, `graph-income`, `graph-farm`), off by default and available on demand.

| Graph | Shows | Data |
|---|---|---|
| Team advantage | Radiant minus Dire gold and experience over time (30 s replay net worth when present, else per minute), important fights shaded, big swings labelled | Timelines, important-fight table |
| Hero income | Rolling GPM per tracked hero (for example a 3-minute window), deaths and buybacks as markers, purchases of key items (Midas and others) as vertical lines, a shaded dip where income fell with the stored reason (died, out of position, farm cut) | Gold per minute, death and item events, mid-game verdict and reasons |
| Farm effectiveness | Lane and neutral farm per minute for a hero, so an effective jungle or a Midas payback is visible | Activity segments, creep kills by type, item efficiency (Midas, farming items) |

The explanations (income drop reasons, Midas payback, key item behind a swing) already exist as stored data; a graph only draws them. The advantage graph is built first and shown to the owner before the other two.

## 7. AI: top 5 reasons the match was won or lost

- **Input:** not raw data. A new `AiMatchFactsBuilder` builds labelled facts from the analysis tables (the same data as `GET /api/v1/replay/{id}/analysis`) and OpenDota: result and duration, draft, per-hero GPM, XPM, KDA and net worth, lane results, important fights with their stored reasons, vision, runes and opening, rotations and group or split, objectives, key item swings, end game (throw or comeback), outliers. Each fact has a stable id (`F3`, `LANE_BOT`) so the model can cite it.
- **Context window:** not a problem for one match. Curated facts are about 1.5k to 7k tokens; a 128k model fits it many times. The real risks are cost, noise and invented patterns. A `facts-token-budget` property (default 6000) caps the input; when the facts exceed it a trimmer drops lowest-weight facts first (kept in order: result and duration, important fights, lane results, objectives, the rest). Summaries across many matches would hit the window; they are out of scope.
- **Output:** the model must return JSON, `[{rank, title, side, evidence: [factIds], explanation}]`, exactly five entries. The code validates it and drops any reason whose evidence ids do not exist in the input, so a reason with no real evidence is removed; fewer than five is reported as such. The Discord text is rendered by our code from the validated JSON.
- **Prompt:** mode-agnostic (the current one is Ability Draft only): analyse the tracked players' team, give the five most decisive reasons, cite fact ids, use only the given facts, say "not enough data" instead of guessing, answer in the channel's language setting (`responseLanguage`).
- **Delivery:** a new section `ai-reasons` in the same framework, off globally and per channel, available through `/match ai`; its own switch `replay.report.ai.enabled`, default off. The old AI summary stays until the owner has compared the two and says to remove it.
- **Cost and safety:** one call per match plus on-demand calls, with a per-match cap; the same facts (stored hash) reuse the stored answer at no cost; failures post nothing and log at WARN; `OPENAI_API_KEY` as today.

## 8. PR order

1. This plan (docs).
2. Stage model: `MatchReportStage`, `HeaderRenderer` in Components V2, stage 1 basic card, title changes at stages 2 and 3, replay-off and failure paths, old OpenDota report as on-demand sections (and automatic while replay is off). Includes the real-channel test of header editing and thread creation.
3. Rendering seam (`SectionView`, `DiscordRenderer`, plain fallback), `important-fights` moved over.
4. Settings and commands: global default list, per-channel `sections`, `/match report`, `!report`, cooldown.
5. Icons: constants `img` field, download and storage, emoji sync job, `discord_icon` table, `IconService` with the fallback chain, used by the card and the sections; heroes, abilities and items.
6. Move the remaining sections to `SectionView`, a few per PR.
7. AI top-5 reasons.
8. Graphs: advantage, then income, then farm.

Steps 2 and 3 can run in parallel; 5 and 8 need 3.

## 9. Not yet verified

- Components V2 per-block size limit and header edit or thread creation behaviour (a real send).
- Emoji name rules, image size limit and the quota behaviour (a real create in a test application).
- How the replay job is queued after stage 2 (read `ReplayRunService`).
- Headless AWT and fonts in the deploy image.
- Whether the old OpenDota report analyzers can run from stage 2 data alone for every mode.
