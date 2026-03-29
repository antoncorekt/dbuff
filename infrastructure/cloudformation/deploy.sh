#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env file if it exists (skip comments and blank lines)
ENV_FILE="$PROJECT_ROOT/.env"
if [ -f "$ENV_FILE" ]; then
  echo "==> Loading environment from $ENV_FILE"
  while IFS= read -r line || [[ -n "$line" ]]; do
    # Skip comments and blank lines
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    export "$key=$value"
  done < "$ENV_FILE"
fi

STACK_NAME="${STACK_NAME:-dbuff}"
REGION="${AWS_REGION:-eu-north-1}"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="dbuff-deploy-${ACCOUNT_ID}"
JAR_PATH="$PROJECT_ROOT/server/build/libs/server-0.0.1-SNAPSHOT.jar"
TEMPLATE="$SCRIPT_DIR/template.yaml"

usage() {
  cat <<EOF
Usage: $0 <command> [options]

Commands:
  build     Build the server JAR
  upload    Upload JAR to S3 (creates bucket if needed via stack)
  deploy    Create or update the CloudFormation stack
  all       build + upload + deploy

Required environment variables for deploy:
  KEY_PAIR_NAME       EC2 key pair name
  DB_PASSWORD         RDS password (min 8 chars)
  DOTA_API_KEY        OpenDota API key
  SCRAPPER_API_KEY    ScraperAPI key
  OPENAI_API_KEY      OpenAI API key
  DISCORD_BOT_TOKEN   Discord bot token

Optional:
  STACK_NAME          CloudFormation stack name (default: dbuff)
  AWS_REGION          AWS region (default: us-east-1)
  INSTANCE_TYPE       EC2 instance type (default: t3.small)
  ALLOWED_SSH_CIDR    SSH CIDR (default: 0.0.0.0/0)
EOF
  exit 1
}

cmd_build() {
  echo "==> Building server JAR..."
  cd "$PROJECT_ROOT"
  ./gradlew :server:bootJar
  echo "==> JAR built: $JAR_PATH"
}

cmd_upload() {
  # Create bucket if it doesn't exist
  if ! aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
    echo "==> Creating S3 bucket: $BUCKET"
    aws s3 mb "s3://${BUCKET}" --region "$REGION"
  fi
  echo "==> Uploading JAR to s3://${BUCKET}/server.jar..."
  aws s3 cp "$JAR_PATH" "s3://${BUCKET}/server.jar" --region "$REGION"
  echo "==> Upload complete"
}

cmd_deploy() {
  : "${KEY_PAIR_NAME:?Set KEY_PAIR_NAME}"
  : "${DB_PASSWORD:?Set DB_PASSWORD}"
  : "${DOTA_API_KEY:?Set DOTA_API_KEY}"
  : "${SCRAPPER_API_KEY:?Set SCRAPPER_API_KEY}"
  : "${OPENAI_API_KEY:?Set OPENAI_API_KEY}"
  : "${DISCORD_BOT_TOKEN:?Set DISCORD_BOT_TOKEN}"

  INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
  ALLOWED_SSH_CIDR="${ALLOWED_SSH_CIDR:-0.0.0.0/0}"

  echo "==> Deploying CloudFormation stack: $STACK_NAME"

  # Check if stack exists
  if aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" >/dev/null 2>&1; then
    ACTION="update-stack"
    echo "    Stack exists, updating..."
  else
    ACTION="create-stack"
    echo "    Creating new stack..."
  fi

  aws cloudformation $ACTION \
    --stack-name "$STACK_NAME" \
    --template-body "file://$TEMPLATE" \
    --region "$REGION" \
    --disable-rollback \
    --parameters \
      ParameterKey=KeyPairName,ParameterValue="$KEY_PAIR_NAME" \
      ParameterKey=DbPassword,ParameterValue="$DB_PASSWORD" \
      ParameterKey=DotaApiKey,ParameterValue="$DOTA_API_KEY" \
      ParameterKey=ScrapperApiKey,ParameterValue="$SCRAPPER_API_KEY" \
      ParameterKey=OpenAiApiKey,ParameterValue="$OPENAI_API_KEY" \
      ParameterKey=DiscordBotToken,ParameterValue="$DISCORD_BOT_TOKEN" \
      ParameterKey=InstanceType,ParameterValue="$INSTANCE_TYPE" \
      ParameterKey=AllowedSshCidr,ParameterValue="$ALLOWED_SSH_CIDR" \
    --capabilities CAPABILITY_NAMED_IAM

  echo "==> Waiting for stack to complete..."
  aws cloudformation wait stack-${ACTION%%-stack}-complete \
    --stack-name "$STACK_NAME" --region "$REGION"

  echo "==> Stack outputs:"
  aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' --output table
}

case "${1:-}" in
  build)  cmd_build ;;
  upload) cmd_upload ;;
  deploy) cmd_deploy ;;
  all)    cmd_build && cmd_upload && cmd_deploy ;;
  *)      usage ;;
esac
