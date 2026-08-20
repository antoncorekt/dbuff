# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DBuff is a Dota 2 match analytics backend built with Spring Boot 3.5, Java 21, and PostgreSQL 16. It fetches match data from the OpenDota API, stores player statistics, provides item/ability rankings, and integrates with Discord and OpenAI for match summaries.

## Build & Development Commands

```bash
# Build entire project
./gradlew build

# Run the server (requires PostgreSQL via docker-compose up -d)
./gradlew :server:bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew :server:test --tests "com.ako.dbuff.SomeTestClass"

# Code formatting (Google Java Format via Spotless)
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Apply formatting

# Regenerate OpenDota API client from OpenAPI spec
./gradlew cleanupodtaClient generateDotapiClient
```

## Architecture

### Multi-Module Gradle Project

- **`server/`** — Main Spring Boot application (`com.ako.dbuff.DbuffApplication`)
- **`clients/dotapi/`** — Auto-generated OpenDota API client (OpenAPI Generator). Do not edit generated code directly; modify `clients/dota_api_formatted.json` and regenerate.

### Server Module Package Layout (`com.ako.dbuff`)

- `config/` — Spring configuration beans (caching, API clients, concurrency, Discord, OpenAI)
- `dao/model/` — JPA entities, `dao/repo/` — Spring Data repositories
- `resources/` — REST controllers and response models
- `service/` — Business logic:
  - `ai/` — OpenAI-powered match analysis and summarization
  - `constant/` — Hero/item/ability constants (cached from OpenDota)
  - `discord/` — Discord bot (JDA). `discord/command/` holds the command layer; see below.
  - `list/` — Match history scraping (Playwright + JSoup) and parsing
  - `match/` — Match processing, analysis, and detail mapping
  - `ranking/` — Item/ability ranking calculations
  - `instance/` — Instance configuration management

### Key Architectural Patterns

- **Virtual Threads (Java 21)**: Used throughout for high concurrency. HikariCP pool is 50 for local dev; the `prod` profile lowers it to 10 because Postgres is co-located on the same 2 GiB instance.
- **Semaphore-based concurrency control**: `ConcurrencyConfig` limits parallel match fetches (20) and page scrapes (5) to prevent resource exhaustion.
- **Rate limiting**: Guava `RateLimiter` on OpenDota API (60 req/min) and ScraperAPI calls.
- **Caching**: Caffeine + Spring Cache for hero/item/ability constants.
- **Schema management**: Hibernate `ddl-auto=update` owns the schema. There are SQL scripts in `server/src/main/resources/db/migration/`, but **Flyway is not on the classpath** — it was never added as a dependency, so those scripts have never run. Treat a `pg_dump` as the only authoritative schema, and do not assume a migration has been applied because a file exists. Indexes are declared with `@Index` on the entities so Hibernate owns them too; `V2__item_ranking_indexes.sql` is retained only as documentation of the `INCLUDE`/partial variants JPA cannot express.
- **Preview features are enabled**: `--enable-preview` is passed at compile time, to the test JVM, and in production (`template.yaml`). All three must agree — a class that actually uses a preview feature is marked in its class file and will not load on a JVM without the flag.

### Discord Command Layer

Commands do not touch JDA. Each implements `DbuffCommand` (name, JDA definition, `execute`) and talks to Discord only through `CommandContext`, which is why they are all testable against `FakeCommandContext` with no Discord connection.

- **`CommandRegistry`** is the single source of truth: both adapters dispatch through it, guild registration reads its definitions, and `/dbuff help` is generated from it, so help cannot drift from the implemented command set.
- **Two adapters, one handler per command.** `SlashCommandAdapter` serves `/` commands and autocomplete; `TextCommandAdapter` keeps the legacy `!` aliases (`!dbuf`, `!vs`, `!rerun`, `!retry`) working by mapping them onto the same option names. Never register a second listener for an input an adapter already handles — both surfaces see `!` messages and the command would be answered twice.
- **Validate, then acknowledge, then work.** Discord discards an interaction not acknowledged within 3 s, and an ephemeral message cannot carry a thread. So every handler does its cheap validation first with `replyEphemeral`, calls `acknowledge` second (which creates the thread), and does the slow work third, writing into the returned `AsyncReply`. Getting this order wrong means a typo produces a thread full of an error instead of a private correction.
- **Autocomplete providers** are keyed `command:subcommand:option`, most-specific-first — needed because e.g. `/dbuff players add player:` searches all of OpenDota while `remove player:` must offer only tracked players. The OpenDota search provider has its **own** rate limiter and cache, deliberately separate from the 60 req/min match-fetch limiter, so typing in a picker cannot starve match ingestion.
- **Unknown names are reported, never dropped.** `ConstantNameResolver` returns unresolved names and the services throw `UnknownConstantNameException`; silently discarding one turns a filtered query into an unfiltered top-N and answers a different question than the one asked.

### Environment Variables

Configured via `.env` file (loaded by spring-dotenv). See `.env.example` for required keys:
- `DOTA_API_KEY`, `SCRAPPER_API_KEY`, `OPENAI_API_KEY`, `DISCORD_BOT_TOKEN`

### Dependencies & Infrastructure

- Locally, PostgreSQL 16 runs via `docker-compose.yml` on `localhost:5432`
- Lombok is used extensively — ensure annotation processing is enabled in your IDE

### Production Topology

A single `t4g.small` (Graviton/arm64) EC2 instance runs both the Spring Boot app
and PostgreSQL 16 on `localhost`, provisioned by
`infrastructure/cloudformation/template.yaml` in `eu-north-1`. There is no RDS
instance — it was removed to cut the bill roughly in half, since a dedicated
database and its second public IPv4 address together cost more than the compute.

Consequences worth knowing before changing anything here:

- **The instance is arm64.** Any native dependency must have an aarch64 build.
- **Backups are a nightly `pg_dump`** to `s3://dbuff-deploy-<account>/db-backups/`
  via the `dbuff-backup.timer` systemd timer (02:30 UTC). Trigger one on demand
  with `./infrastructure/cloudformation/deploy.sh backup-now`; restore with
  `... restore <key>`. Losing the instance without a backup loses the database —
  there is no longer a managed snapshot to fall back on.
- **RAM is the binding constraint** (~2 GiB shared between the JVM at `-Xmx512m`,
  Postgres at `shared_buffers=256MB`, and a 2 GiB swapfile as a safety net).

### Deploying

**Application code ships through CI.** A push to `main` runs
`.github/workflows/ci-cd.yml`: the `verify` job builds and tests, and the
`deploy` job installs the JAR that those tests passed, then gates on
`/actuator/health` and rolls back to the previous release if it does not come
up. Do not deploy application changes by hand — merge to `main` instead. CI
authenticates via GitHub OIDC (role from `infrastructure/cloudformation/cicd.yaml`,
a stack separate from `dbuff`), so there is no long-lived AWS key.

**Infrastructure stays manual, deliberately.** The `dbuff` stack owns the EC2
instance that hosts the database, so CI has no permission to touch it. Apply
template changes yourself:

```bash
./infrastructure/cloudformation/deploy.sh deploy   # apply template.yaml
./infrastructure/cloudformation/deploy.sh cicd     # apply cicd.yaml (CI identity)
./infrastructure/cloudformation/deploy.sh all      # build + upload + deploy, the pre-CI manual path
```

`build`/`upload`/`all` remain for emergencies and for bootstrapping a rebuilt
instance. Note that a stack update replacing the instance destroys the local
database — take a backup first.
