# DBuff - Dota 2 Match Analytics Platform

A Spring Boot application for fetching, analyzing, and storing Dota 2 match data using the OpenDota API.

## Overview

DBuff is a Dota 2 analytics backend that:

- **Fetches match data** from the [OpenDota API](https://docs.opendota.com/)
- **Stores player statistics** including match history, hero performance, and item builds
- **Provides rankings** for items and abilities based on match data
- **Supports player search** and match history browsing

### Key Features

- 🎮 **Match Details** - Fetch and store detailed match information
- 👤 **Player Statistics** - Track player performance across matches
- 🏆 **Item Rankings** - Analyze item popularity and win rates
- ⚔️ **Ability Rankings** - Track ability usage patterns
- 🔍 **Player Search** - Find players and their match history
- 📊 **Hero Constants** - Access hero, item, and ability metadata

## Tech Stack

- **Java 21** with Virtual Threads
- **Spring Boot 3.x**
- **PostgreSQL 16** - Primary database
- **Hibernate/JPA** - ORM
- **Jersey Client** - REST client for OpenDota API
- **Gradle** - Build tool

## Prerequisites

- Java 21+
- Docker & Docker Compose
- OpenDota API Key (optional, but recommended for higher rate limits)

## Quick Start

### 1. Clone and Setup

```bash
git clone <repository-url>
cd dbuff
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
```

Edit `.env` and add your API keys:
```properties
DOTA_API_KEY=your_opendota_api_key
SCRAPPER_API_KEY=your_scrapper_api_key  # Optional
```

Get your OpenDota API key from: https://www.opendota.com/api-keys

### 3. Start PostgreSQL

```bash
docker-compose up -d
```

This starts PostgreSQL on `localhost:5432` with:
- Database: `dbuff`
- Username: `postgres`
- Password: `password`

### 4. Run the Application

```bash
./gradlew :server:bootRun
```

The server starts on `http://localhost:8080`

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /players/{accountId}` | Get player information |
| `GET /players/{accountId}/matches` | Get player match history |
| `GET /matches/{matchId}` | Get match details |
| `GET /history/{accountId}` | Fetch and process match history |
| `GET /items/ranking` | Get item rankings |
| `GET /abilities/ranking` | Get ability rankings |
| `GET /find/player` | Search for players |

## Project Structure

```
dbuff/
├── server/                    # Main Spring Boot application
│   └── src/main/java/com/ako/dbuff/
│       ├── config/           # Configuration classes
│       ├── dao/              # Data access layer
│       ├── resources/        # REST controllers
│       └── service/          # Business logic
├── clients/
│   └── dotapi/               # Generated OpenDota API client
├── sidecars/                 # Docker Sidekick configs
└── docker-compose.yml        # Local development setup
```

## Development

### Regenerate OpenDota API Client

If you modify the OpenAPI spec:

```bash
./gradlew cleanupodtaClient generateDotapiClient
```

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

## Configuration

Key configuration in `server/src/main/resources/application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `spring.datasource.url` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/dbuff` |
| `dota-api.api-key` | OpenDota API key | - |
| `dota-api.request-per-minute` | Rate limit for API calls | `60` |
| `app.concurrency.max-parallel-matches` | Max concurrent match fetches | `20` |

## Docker Commands

```bash
# Start PostgreSQL
docker-compose up -d

# View logs
docker-compose logs -f postgres

# Stop PostgreSQL
docker-compose down

# Stop and remove data
docker-compose down -v
```

## License

MIT
