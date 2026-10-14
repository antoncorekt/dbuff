# Replay fixtures

Real replays are needed by the tests tagged `replay` (`./gradlew :server:replayTest`) and by the review loops. They are never committed: they live in a directory outside the repository, named by `REPLAY_FIXTURE_DIR`.

## Files per match

| File | What it is |
|---|---|
| `<id>.json` | OpenDota's `GET /api/matches/<id>` answer (gold series, players, `cluster`, `replay_salt`) |
| `<id>.dem.zst` | the replay as Valve serves it; the tests serve this file from a local HTTP server that stands in for Valve |
| `<id>.dem` | the decompressed replay (for tools that want a plain file) |
| `constants-*.json` | OpenDota `/constants` answers (heroes, abilities, ability ids, items) |

`server/src/test/resources/replay/load-test/matches.txt` lists the 20 matches of the load test (`ReplayLoadTest`).

## Fetching a replay

For each match id, with `curl` and `jq`, and a pause of about two seconds between calls (OpenDota allows about 60 requests per minute without a key and 3000 per day; an API key in `OPENDOTA_API_KEY` raises that and is added as `?api_key=`):

1. `curl https://api.opendota.com/api/matches/<id> -o <id>.json`. Read `cluster` and `replay_salt`. A match that finished a few minutes ago can still lack them; pick another match or wait.
2. `curl -L http://replay<cluster>.valve.net/570/<id>_<replay_salt>.dem.bz2 -o <id>.replay`. The URL says `.bz2`, but Valve now answers `application/octet-stream` with zstd data (magic bytes `28 b5 2f fd`). Check the first four bytes: `28b52ffd` means zstd, store it as `<id>.dem.zst`; `425a6839` ("BZh9") means bzip2, store it as `<id>.dem.bz2`.
3. Decompress to `<id>.dem` (`zstd -d` or `bzip2 -dc`).
4. Skip every file that already exists, so the steps can be repeated safely.

A replay is available for about eight days after the match for most matches, and Valve deletes it for good after that, so download early and keep the files.

## Choosing matches

OpenDota's explorer answers a recent-matches query without a key, for example `GET /api/explorer?sql=select match_id, game_mode, duration, cluster from public_matches where game_mode = 18 and start_time > extract(epoch from now()) - 2 * 86400 and duration between 3000 and 3600 order by start_time desc limit 6`. Game mode 18 is Ability Draft, 22 is All Pick. A mix of 30 to 60 minutes plus a few over 50 and one over 60 minutes covers the memory range that matters.

## Running the load test

```
docker run -d --name dbuff-load-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=dbuff -p 5666:5432 postgres:16-alpine
SMOKE_PG_URL=jdbc:postgresql://localhost:5666/dbuff REPLAY_FIXTURE_DIR=<dir> ./gradlew :server:replayTest --tests '*ReplayLoadTest*'
```

The table is printed and written to `send/load-test.md` next to `REPLAY_FIXTURE_DIR`. `:server:replayTest` runs at `-Xmx512m`, the production heap.
