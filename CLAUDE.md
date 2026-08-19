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
  - `discord/` — Discord bot listeners (JDA)
  - `list/` — Match history scraping (Playwright + JSoup) and parsing
  - `match/` — Match processing, analysis, and detail mapping
  - `ranking/` — Item/ability ranking calculations
  - `instance/` — Instance configuration management

### Key Architectural Patterns

- **Virtual Threads (Java 21)**: Used throughout for high concurrency. HikariCP pool is 50 for local dev; the `prod` profile lowers it to 10 because Postgres is co-located on the same 2 GiB instance.
- **Semaphore-based concurrency control**: `ConcurrencyConfig` limits parallel match fetches (20) and page scrapes (5) to prevent resource exhaustion.
- **Rate limiting**: Guava `RateLimiter` on OpenDota API (60 req/min) and ScraperAPI calls.
- **Caching**: Caffeine + Spring Cache for hero/item/ability constants.
- **Schema management**: Hibernate `ddl-auto=update` owns the schema. There are SQL scripts in `server/src/main/resources/db/migration/`, but **Flyway is not on the classpath** — it was never added as a dependency, so those scripts have never run and the indexes they define (e.g. `V2__item_ranking_indexes.sql`) do not exist in any deployed database. Treat a `pg_dump` as the only authoritative schema, and do not assume a migration has been applied because a file exists.

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
