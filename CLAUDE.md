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

- **Virtual Threads (Java 21)**: Used throughout for high concurrency. HikariCP pool sized at 50 connections.
- **Semaphore-based concurrency control**: `ConcurrencyConfig` limits parallel match fetches (20) and page scrapes (5) to prevent resource exhaustion.
- **Rate limiting**: Guava `RateLimiter` on OpenDota API (60 req/min) and ScraperAPI calls.
- **Caching**: Caffeine + Spring Cache for hero/item/ability constants.
- **Database migrations**: Flyway, scripts in `server/src/main/resources/db/migration/`.

### Environment Variables

Configured via `.env` file (loaded by spring-dotenv). See `.env.example` for required keys:
- `DOTA_API_KEY`, `SCRAPPER_API_KEY`, `OPENAI_API_KEY`, `DISCORD_BOT_TOKEN`

### Dependencies & Infrastructure

- PostgreSQL 16 runs via `docker-compose.yml` on `localhost:5432`
- Lombok is used extensively — ensure annotation processing is enabled in your IDE
