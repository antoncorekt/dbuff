# Replay pipeline: production switch-on checklist (C7)

Status 2026-10-14. Processing and the report are off in production today (`replay.processing.enabled` and `replay.report.enabled` default to false). The order below is the one in `CLAUDE.md` ("Switching it on in production"); it only starts on the owner's word, one step per deploy.

## 1. What has been verified

| What | Evidence (2026-10-14 unless stated) |
|---|---|
| Whole pipeline on a real PostgreSQL 16 | `ReplayPostgresSmokeTest` on the 4 stored fixtures (2 Ability Draft, 2 All Pick), all DONE; also green inside a linux/arm64 container (see the zstd row). Needs an empty database: it counts runs in `replay_parse_run`. |
| 20 replays one after another at `-Xmx512m` | `ReplayLoadTest` (`./gradlew :server:replayTest --tests '*ReplayLoadTest*'`): 20 of 20 DONE, none PARTIAL, no out-of-memory, 87 s in total. Table: `~/dbuff-fixtures/send/load-test.md`. |
| Time per replay | 2.5 s (31 to 33 minute games) to 6.1 s (60 to 64 minutes), 5.7 s for a 76-minute game. This is the parse permit to the end, from a local file served by a stand-in for Valve. |
| Heap | Live heap after a forced GC stays at 96 to 100 MB after every run, against 88 MB before the first one: no growth over 20 runs. Peak of the old generation 114 to 161 MB (161 MB for the 76-minute game). Peak of all heap pools added together 279 to 369 MB (an upper bound: the pools do not peak together). All inside the 512 MB heap, with the test's Spring context included. The live figure is approximate (`System.gc()` is a hint; default collector, no `-XX:+DisableExplicitGC`) and the pool peaks include garbage left by the previous run. |
| Rows stored per match | 8.6 thousand (30-minute game) to 21.6 thousand (76 minutes) over 49 tables; the largest are `replay_state_change` (2.7 to 7.4 thousand), `player_activity_segment` (1.5 to 3.8 thousand), `creep_kill` (1.1 to 3.8 thousand), `fight_ability_use` (1.0 to 2.6 thousand). |
| A real download from Valve | 17 downloads from `http://replay<cluster>.valve.net/570/<id>_<salt>.dem.bz2`, cluster and salt from OpenDota's match JSON: all HTTP 200, `application/octet-stream`, and the body is zstd (`28 b5 2f fd`) although the URL says `.bz2`. Sizes 36.5 to 91.7 MB compressed, 1.8 to 10.9 s each except one of 96 s (61 MB; the limit `max-download-seconds` is 300). The matches were 1 to 2 hours old. Clusters 151 to 417. |
| Valve's zstd handled by the app's decompressor | `ReplayFileProvider` picks `ZstdDecompressor` from the magic bytes; every stored `.dem.zst` of the 20 went through it. |
| zstd on Linux arm64 | Production is a `t4g` (arm64). `zstd-jni` 1.5.6-4 ships `linux/aarch64/libzstd-jni-1.5.6-4.so`. Inside `amazoncorretto:21` (Amazon Linux 2023, `uname -m` aarch64, Java 21.0.12) a tiny main decoded a 76 MB and a 154 MB stored `.dem.zst` through `ZstdInputStream` with the SHA-256 of the output equal to the one of the `zstd -d` file, and `ReplayPostgresSmokeTest` (4 matches, all DONE, parse 0.8 to 1.5 s each) passed in the same image against a postgres container. |

Limits of that evidence: the arm64 run was a Docker container on an arm64 Mac (same CPU architecture and libc family as the host, not the host's kernel or Java build), and the JVM there was not started with `-Xmx512m --enable-preview` as the service is (the Gradle test task does set `-Xmx512m`).

## 2. What is still unverified

1. **OpenDota data present when the replay is analysed, and what happens when not.** The analyzers read OpenDota's gold and net worth series and statistics. Observed 2026-10-14: of 19 matches fetched 1 to 2 hours after they ended, 2 had no `cluster` and `replay_salt` yet (one got them about 20 minutes later, the other still had none after 20 minutes and no parsed data). That is the `SKIPPED_NO_REPLAY_INFO` path (retried later); the load test only used matches whose data were complete. A replay analysed while the parsed series are missing must end `PARTIAL` (extractor failed) and keep retrying `max-partial-attempts` (2) times. Not exercised against real data: check with one such match before step 3.
2. **The report text in a real Discord channel.** Rendering is tested and the sections were reviewed as text on 2 matches; nothing was ever posted by the bot into a real thread, so message length, formatting, and the thread follow-up are unverified.
3. **A real metrics read on the server** (`/actuator/metrics/...` behind the key on the production host, and any scraper). Locally the meters were asserted in the smoke test only.
4. **The temp directory on the host.** The default is `${java.io.tmpdir}/dbuff-replays`, i.e. `/tmp`. On Amazon Linux 2023 `/tmp` is normally a tmpfs, which lives in RAM: a parse holds the compressed file (up to about 92 MB) and the decompressed `.dem` (up to about 154 MB for a 76-minute game; the limit is 500 MB) at the same time, on a 2 GiB host shared with Postgres. Check `df -h /tmp` on the host in step 2 before the first parse; if it is tmpfs, set `REPLAY_PROCESSING_TEMP_DIR=/var/tmp/dbuff-replays` (the directory must be writable by the `dbuff` user).
5. **Valve reachable from the AWS host** (egress to `replay<cluster>.valve.net` on port 80) and the observed download time from `eu-north-1`. The 96 s download above came from a home connection; the host's can differ.
6. **A game of 90 minutes.** The longest tested is 76 minutes (peak old generation 161 MB). A longer one is linear in the replay size, so it is expected to fit, but it was not run.
7. **The whole service JVM on the host** (`-Xms256m -Xmx512m --enable-preview` next to Postgres with `shared_buffers=256MB`): the load test measured the heap only, not the resident memory of the process or the swap use.

## 3. The staged switch-on

Commands run from a machine with AWS credentials. The REST API key is read on the host from SSM and used there against `localhost:8080`, so it never travels; from outside the host, use HTTPS or an SSH tunnel, never plain http across the internet.

```
export AWS_REGION=eu-north-1
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" --output text)
host()    { scripts/replay-host.sh "$INSTANCE" sh "$1"; }   # a shell command as root on the host
hostget() { scripts/replay-host.sh "$INSTANCE" get "$1"; }  # an authenticated GET to localhost:8080 on the host
```

`scripts/replay-host.sh` sources `scripts/lib/ssm.sh` and calls its `run_ssm`, as `deploy-release.sh` and `set-api-key.sh` do: it waits for the command, prints its output, and exits non-zero (with the stderr) when the command or SSM fails. The API key is read from SSM on the host and handed to curl on stdin. After every restart, check the start: `host "journalctl -u dbuff -n 50 --no-pager"` and `hostget /actuator/health`.

### Step 1: backup and merge (creates the tables only)

1. `./infrastructure/cloudformation/deploy.sh backup-now`. The last lines of `/var/log/dbuff/backup.log` must show a finished dump; note the S3 key.
2. Merge to `main`; CI deploys and gates on `/actuator/health`. Nothing replay-related runs (`replay.processing.enabled` is false); Hibernate adds the tables on start.
3. Check: `host "sudo -u postgres psql dbuff -c '\dt'" | grep replay` lists `replay_parse_run_domain` and the analysis tables, and `host "free -m; mount | grep -E ' /tmp | /var/tmp '; df -h / /tmp /var/tmp"` for the memory baseline and the type of the temp directory (unverified item 4). `/var/tmp` is normally on the root volume, shared with `/` (the Postgres data and the logs), so compare the free space of both with the up to 250 MB a parse needs.
4. Temp directory (only if `/tmp` is a tmpfs): `host "install -d -o dbuff -g dbuff -m 700 /var/tmp/dbuff-replays && sudo -u dbuff touch /var/tmp/dbuff-replays/.w && rm /var/tmp/dbuff-replays/.w && echo writable"` must print `writable` before `REPLAY_PROCESSING_TEMP_DIR` points at it. The unit (`template.yaml`, `dbuff.service`) sets `User=dbuff` and has no `PrivateTmp`, `ProtectSystem` or `ReadWritePaths`, so the service sees the real `/tmp` and `/var/tmp` and may write to any path its user owns; no unit change is needed for that directory.
5. Rollback: nothing to turn off; restore the dump only if the app does not start (`deploy.sh restore <key>`).

### Step 2: processing on, one match per run

1. Write a drop-in (the same mechanism as `scripts/set-api-key.sh`, which survives JAR deploys) and restart:
   ```
   host "install -d /etc/systemd/system/dbuff.service.d; printf '[Service]\nEnvironment=REPLAY_PROCESSING_ENABLED=true\nEnvironment=REPLAY_PROCESSING_BATCH_SIZE=1\n' > /etc/systemd/system/dbuff.service.d/replay.conf; systemctl daemon-reload; systemctl restart dbuff"
   ```
   Add `Environment=REPLAY_PROCESSING_TEMP_DIR=/var/tmp/dbuff-replays` only after the write test of step 1 passed. Then `journalctl -u dbuff -n 50`.
2. Watch the log: `host "grep -i replay /var/log/dbuff/dbuff.log | tail -30"`. The line to expect for each run:
   `Replay run for match <id>: DONE (attempt 1, <bytes> bytes, <ms> ms)`, then `Replay batch: 1 of 1 matches stored`. Bad signs (grep for them explicitly: `host "grep -E 'FAILED_|PARTIAL|cut short|cannot be loaded|failed|stopped' /var/log/dbuff/dbuff.log | grep -i replay | tail"`): `Replay run for match <id>: FAILED_*` or `PARTIAL`, `Replay libraries cannot be loaded` (zstd native), `Replay extractor <name> failed` (the run ends PARTIAL), `Replay analyzer <name> failed for match <id>`, `Replay batch stopped after`, `Replay of the game was cut short`, `Replay report ... failed`, `Replay metric not recorded`, `Replay run counts could not be read`.
3. One run's outcome: `hostget /api/v1/replay/<matchId>` (status, attempts, error), and the stored analysis: `.../api/v1/replay/<matchId>/analysis?sections=run,roles,fights`.
4. Metrics (`hostget '/actuator/metrics/<name>'`, for the tagged ones `'/actuator/metrics/dbuff.replay.runs?tag=status:DONE'`):
   - `dbuff.replay.runs?tag=status:DONE`, `status:PARTIAL`, `status:FAILED_RESOURCE`, and the other `FAILED_*` (counts for the current parser version, so a version bump makes them fall);
   - `dbuff.replay.run.duration?tag=status:DONE`: compare with the load test, 2.5 to 6.1 s per replay locally; a host several times slower is worth a look, one over `max-parse-seconds` (120) ends `FAILED_TIMEOUT`;
   - `jvm.memory.used?tag=area:heap` read during a parse and `jvm.gc.live.data.size` after it: live data should return to roughly the level before the parse. Expected range seen in the 20-replay run (a test context, not the service, so only a rough guide, and the figures are approximate: `System.gc()` is a hint and the pool peaks include garbage of the previous run): live heap about 100 MB, old generation peak 114 to 161 MB, all pools together up to 369 MB. These are observations, not limits; what matters is that live data does not climb run after run;
   - on the host `free -m`: swap use must not grow during a parse (the heap is 512 MB of a 2 GiB host with Postgres).
5. Let several runs finish, including one long game and one PARTIAL if it comes. Read at least one `/analysis` document against the match.
6. Rollback: `host "rm /etc/systemd/system/dbuff.service.d/replay.conf; systemctl daemon-reload; systemctl restart dbuff"` (or set `REPLAY_PROCESSING_ENABLED=false`). The stored rows stay and are harmless; a stuck `RUNNING` run is cleaned by the janitor.

### Step 3: raise the batch size

1. Change `REPLAY_PROCESSING_BATCH_SIZE` in the same drop-in (for example 5, the code default), restart.
2. Re-check the counters of step 2 after a full cron cycle (every 10 minutes at the default `0 3/10 * * * ?`); `replay.processing.backlog.enabled` stays false until the owner decides on old matches (open decision 3).
3. Rollback: put the batch size back to 1, or the step 2 rollback.

### Step 4: the report (Discord), last

1. `REPLAY_REPORT_ENABLED=true` in the drop-in, restart. This alone already posts the six sections that are on by default (below). Every other section needs its own switch `REPLAY_REPORT_SECTIONS_<NAME>_ENABLED=true` with exactly one of these names (checked against the sections' `name()` in the code): `SWING`, `DEATHS`, `FIGHTS`, `MOVEMENT`, `DRAFT`, `COMPOSITION`, `IMPORTANTFIGHTS` (the section `important-fights`; no underscore inside the name, `..._IMPORTANT_FIGHTS_...` does not bind), `LANES`, `ROTATIONS`, `SMOKE`, `OBJECTIVES`, `OPENING`, `VISION`, `ITEMS`, `OUTLIERS`, `ENDGAME`. **A key for a name that is not a section makes the application refuse to start while `REPLAY_REPORT_ENABLED=true`** (`SectionSwitch` lists the known sections in the error). Start with only the default ones, in one test channel, and check `journalctl -u dbuff -n 50` and `hostget /actuator/health` after every restart.
2. Log lines (`host "grep -i 'replay report' /var/log/dbuff/dbuff.log | tail"`): `Replay report of match <id> posted in <n> messages` and `Replay report of match <id> not posted: every section is switched off`; bad: `Replay report of match <id> for instance <n> failed`, `Replay report delivery for match <id> failed`, `Replay report delivery failed`. Metric `dbuff.replay.report.posted`.
3. Read the real thread in Discord: message length and count (`max-messages` 4 at full detail, 2 at standard), wording, a player who was not in the channel. This is unverified item 2.
4. Turn on the accepted sections one at a time as separate config changes.
5. Rollback: `REPLAY_REPORT_ENABLED=false` (posted messages stay in Discord; the posted marker stops a repost). If the app does not start after a wrong section key, remove that bad key from `replay.conf` first (or set `REPLAY_REPORT_ENABLED=false`), then `systemctl daemon-reload; systemctl restart dbuff`, then read `journalctl -u dbuff -n 50`.

## 4. Report sections and their switches

`replay.report.sections.<name>.enabled` decides; without it the section's own default applies. A new section ships off.

| Section | Default | Why |
|---|---|---|
| `swing` | on | the original swing line (net worth swings with reasons) |
| `deaths` | on | the original death summary |
| `fights` | on | original teamfight lines (full detail only) |
| `movement` | on | original movement lines (full detail only) |
| `composition` | on | original team composition verdict |
| `draft` | on | original Ability Draft pick lines |
| `important-fights` | off | table accepted by the owner, switched on later by config, not by its PR |
| `lanes` | off | table accepted; same rule |
| `vision` | off | table accepted; same rule |
| `items` | off | table accepted; same rule |
| `rotations` | off | table accepted for the good, wasted and died rows; same rule |
| `smoke` | off | not yet accepted by the owner (B2 to B4 reports) |
| `objectives` | off | not yet accepted by the owner |
| `opening` | off | runes and opening: no acceptance recorded in the plan |
| `outliers` | off | no acceptance recorded; explanations are built (PR #99), neither fixture match has an outlier at the default thresholds |
| `endgame` | off | new (C6); the throw flag is a guess, no loop round accepted it |

## 5. Open decisions for the owner

1. **Temp directory**: move it off `/tmp` (RAM) to `/var/tmp/dbuff-replays` if the host's `/tmp` is a tmpfs? Default: yes, decided from `df -h /tmp` in step 1.
2. **Which sections first** when the report is turned on: only the six default ones, or already the five accepted tables? Default: the six first, the accepted five one by one after reading real threads.
3. **Backlog**: keep `replay.processing.backlog.enabled=false` (only new matches), or work through old matches too? Replays older than Valve's retention cannot be fetched anyway. Default: false until step 3 has run for a few days.
4. **Batch size** after step 2: 5 (the code default) or lower? A replay takes 3 to 6 seconds of CPU in the test, so 5 per run every 10 minutes is far under the host's capacity; the 2 GiB memory is the limit, and `max-concurrent` stays 1.
5. **Metrics reading**: keep reading by `curl` through SSM, or wire a scraper? The meters exist; nothing scrapes them yet.
6. **Keep fixtures**: Valve will delete the 20 stored replays; keep the `.dem.zst` files in `~/dbuff-fixtures/replays` as the only copy for load tests and loops.
7. **A 90-minute game** as a separate test: wait for one and add it to `matches.txt`, or accept the 76-minute figures? Default: accept for now.
