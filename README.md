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

## AWS Deployment

Deploy DBuff to a single EC2 instance with RDS PostgreSQL. Estimated cost: ~$28/mo.

```
Internet → EC2 t3.small (:8080) → RDS PostgreSQL (private subnet)
                   ↓
           SSM Parameter Store (API keys)
           S3 bucket (JAR artifact)
```

### Prerequisites

- AWS CLI installed and configured (`brew install awscli && aws configure`)
- An AWS account with admin access

### First-Time Setup

**1. Create an EC2 key pair** (for SSH access):

```bash
aws ec2 create-key-pair --key-name dbuff-key \
  --query 'KeyMaterial' --output text > ~/.ssh/dbuff-key.pem
chmod 400 ~/.ssh/dbuff-key.pem
```

**2. Configure `.env`** with your API keys and deployment settings:

```bash
cp .env.example .env
```

Edit `.env` and fill in all values:

```properties
# API keys (you likely already have these)
DOTA_API_KEY=...
SCRAPPER_API_KEY=...
OPENAI_API_KEY=...
DISCORD_BOT_TOKEN=...

# AWS deployment
KEY_PAIR_NAME=dbuff-key
DB_PASSWORD=YourStrongPassword123
```

**3. Deploy everything:**

```bash
./infrastructure/cloudformation/deploy.sh all
```

This builds the JAR, uploads it to S3, and creates the full CloudFormation stack (VPC, EC2, RDS, security groups, etc.). Takes ~15 minutes on first run (mostly RDS creation).

**4. Verify:**

```bash
# Get the public IP from stack outputs
aws cloudformation describe-stacks --stack-name dbuff \
  --query 'Stacks[0].Outputs[?OutputKey==`PublicIP`].OutputValue' --output text

# Health check
curl http://<EC2-IP>:8080/actuator/health

# Test an endpoint
curl "http://<EC2-IP>:8080/find/player?search=dendi"
```

### Redeploying (Code Updates)

After making code changes:

```bash
# 1. Build and upload new JAR
./infrastructure/cloudformation/deploy.sh build
./infrastructure/cloudformation/deploy.sh upload

# 2. SSH in and restart the service
ssh -i ~/.ssh/dbuff-key.pem ec2-user@<EC2-IP>
sudo aws s3 cp s3://dbuff-deploy-$(aws sts get-caller-identity --query Account --output text)/server.jar /opt/dbuff/server.jar
sudo systemctl restart dbuff
```

### Redeploying (Infrastructure Changes)

If you modify `template.yaml`:

```bash
./infrastructure/cloudformation/deploy.sh deploy
```

### Troubleshooting

```bash
# SSH into the instance
ssh -i ~/.ssh/dbuff-key.pem ec2-user@<EC2-IP>

# App logs
sudo tail -f /var/log/dbuff/dbuff.log

# Service status
sudo systemctl status dbuff

# Bootstrap log (user-data script)
cat /var/log/user-data.log
```

### Tearing Down

```bash
# Disable deletion protection on RDS first (via AWS Console or CLI)
aws rds modify-db-instance --db-instance-identifier dbuff-postgres \
  --no-deletion-protection --apply-immediately

# Delete the stack
aws cloudformation delete-stack --stack-name dbuff
aws cloudformation wait stack-delete-complete --stack-name dbuff

# Optionally delete the S3 bucket
aws s3 rb s3://dbuff-deploy-$(aws sts get-caller-identity --query Account --output text) --force
```

## License

MIT
