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

# The CI identity lives in its own stack so that changing CI permissions never
# means running update-stack against the stack that owns the database instance.
# Not configurable: scripts/sync-github-secrets.sh looks the role ARN up by
# this exact name.
CICD_STACK_NAME="dbuff-cicd"
CICD_TEMPLATE="$SCRIPT_DIR/cicd.yaml"

usage() {
  cat <<EOF
Usage: $0 <command> [options]

Commands:
  build       Build the server JAR
  upload      Upload JAR to S3 (creates bucket if needed via stack)
  deploy      Create or update the CloudFormation stack
  all         build + upload + deploy
  cicd        Create or update the $CICD_STACK_NAME stack (GitHub OIDC + deploy role)
  backup-now  Trigger an immediate database backup to S3
  restore     Restore a dump from s3://<bucket>/db-backups/<key> (stops the app)

Note: 'build'/'upload'/'deploy' are the manual path, kept for infrastructure
changes and emergencies. Application releases normally go out through GitHub
Actions on a push to main - see docs/superpowers/specs/2026-08-19-app-cicd-design.md.

Required environment variables for deploy:
  KEY_PAIR_NAME       EC2 key pair name
  DB_PASSWORD         Local Postgres password for role 'dbuffuser' (min 8 chars,
                      must NOT contain a single quote - it is interpolated into
                      a SQL literal in the instance UserData)
  DOTA_API_KEY        OpenDota API key
  SCRAPPER_API_KEY    ScraperAPI key
  OPENAI_API_KEY      OpenAI API key
  DISCORD_BOT_TOKEN   Discord bot token

Optional:
  GOOGLE_VISION_CREDENTIALS_PATH  Path to Google Vision service-account JSON (enables OCR)
  STACK_NAME          CloudFormation stack name (default: dbuff)
  AWS_REGION          AWS region (default: eu-north-1)
  INSTANCE_TYPE       EC2 instance type, arm64 only (default: t4g.small)
  ALLOWED_SSH_CIDR    SSH CIDR (default: 0.0.0.0/0 - narrow this to your own IP)
EOF
  exit 1
}

cmd_build() {
  echo "==> Building server JAR..."
  cd "$PROJECT_ROOT"
  ./gradlew :server:bootJar -x spotlessCheck -x spotlessApply
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

  INSTANCE_TYPE="${INSTANCE_TYPE:-t4g.small}"
  ALLOWED_SSH_CIDR="${ALLOWED_SSH_CIDR:-0.0.0.0/0}"

  # Optionally base64-encode the Google Vision service-account key so it can be passed
  # through CloudFormation/SSM (the JSON contains newlines). Resolved relative to the repo root.
  GOOGLE_VISION_CREDENTIALS_B64=""
  if [ -n "${GOOGLE_VISION_CREDENTIALS_PATH:-}" ]; then
    CRED_PATH="$GOOGLE_VISION_CREDENTIALS_PATH"
    [[ "$CRED_PATH" != /* ]] && CRED_PATH="$PROJECT_ROOT/$CRED_PATH"
    if [ -f "$CRED_PATH" ]; then
      echo "==> Encoding Google Vision credentials from $CRED_PATH"
      GOOGLE_VISION_CREDENTIALS_B64=$(base64 < "$CRED_PATH" | tr -d '\n')
    else
      echo "WARN: GOOGLE_VISION_CREDENTIALS_PATH set but file not found: $CRED_PATH (Vision disabled)"
    fi
  else
    echo "WARN: GOOGLE_VISION_CREDENTIALS_PATH not set; deploying without Google Vision credentials"
  fi

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
    --parameters \
      ParameterKey=KeyPairName,ParameterValue="$KEY_PAIR_NAME" \
      ParameterKey=DbPassword,ParameterValue="$DB_PASSWORD" \
      ParameterKey=DotaApiKey,ParameterValue="$DOTA_API_KEY" \
      ParameterKey=ScrapperApiKey,ParameterValue="$SCRAPPER_API_KEY" \
      ParameterKey=OpenAiApiKey,ParameterValue="$OPENAI_API_KEY" \
      ParameterKey=DiscordBotToken,ParameterValue="$DISCORD_BOT_TOKEN" \
      ParameterKey=GoogleVisionCredentialsB64,ParameterValue="$GOOGLE_VISION_CREDENTIALS_B64" \
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

cmd_cicd() {
  # Org/repo are derived from the 'origin' remote so this stack cannot drift from
  # the repository it is meant to trust. The template's own defaults are used if
  # derivation fails.
  local url path org repo params=()
  url="$(git -C "$PROJECT_ROOT" remote get-url origin 2>/dev/null || true)"
  case "$url" in
    *://*) path="${url#*://}"; path="${path#*/}" ;;   # https://host/owner/repo
    *:*)   path="${url##*:}" ;;                       # git@host:owner/repo
    *)     path="" ;;
  esac
  path="${path%.git}"
  org="${path%%/*}"
  repo="${path##*/}"
  if [ -n "$path" ] && [ "$org" != "$path" ]; then
    echo "==> Trusting GitHub repo $org/$repo (branch main) from the origin remote"
    params+=(ParameterKey=GitHubOrg,ParameterValue="$org")
    params+=(ParameterKey=GitHubRepo,ParameterValue="$repo")
  else
    echo "WARN: could not derive owner/repo from origin ($url); using template defaults"
  fi
  params+=(ParameterKey=AppStackName,ParameterValue="$STACK_NAME")

  echo "==> Deploying CloudFormation stack: $CICD_STACK_NAME"

  local action
  if aws cloudformation describe-stacks --stack-name "$CICD_STACK_NAME" --region "$REGION" >/dev/null 2>&1; then
    action="update-stack"
    echo "    Stack exists, updating..."
  else
    action="create-stack"
    echo "    Creating new stack..."
    # The OIDC provider is account-global, so a provider created by hand or by
    # another project makes create-stack fail with EntityAlreadyExists. Say so
    # up front rather than leaving a confusing ROLLBACK_COMPLETE behind.
    if aws iam list-open-id-connect-providers \
         --query "OpenIDConnectProviderList[?ends_with(Arn, ':oidc-provider/token.actions.githubusercontent.com')].Arn" \
         --output text | grep -q .; then
      echo "ERROR: an OIDC provider for token.actions.githubusercontent.com already exists in" >&2
      echo "       this account. Import it into the stack, or delete the GitHubOidcProvider" >&2
      echo "       resource from cicd.yaml and reference the existing ARN instead." >&2
      return 1
    fi
  fi

  # update-stack exits non-zero when there is genuinely nothing to change, which
  # is a successful outcome here.
  local out
  if ! out="$(aws cloudformation $action \
      --stack-name "$CICD_STACK_NAME" \
      --template-body "file://$CICD_TEMPLATE" \
      --region "$REGION" \
      --parameters "${params[@]}" \
      --capabilities CAPABILITY_NAMED_IAM 2>&1)"; then
    if echo "$out" | grep -q 'No updates are to be performed'; then
      echo "    No changes to apply"
    else
      echo "$out" >&2
      return 1
    fi
  else
    echo "==> Waiting for stack to complete..."
    aws cloudformation wait "stack-${action%%-stack}-complete" \
      --stack-name "$CICD_STACK_NAME" --region "$REGION"
  fi

  echo "==> Stack outputs:"
  aws cloudformation describe-stacks --stack-name "$CICD_STACK_NAME" --region "$REGION" \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' --output table

  echo "==> Next: ./scripts/sync-github-secrets.sh push"
}

cmd_instance_id() {
  aws cloudformation describe-stack-resources --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
    --output text
}

cmd_backup_now() {
  local instance
  instance=$(cmd_instance_id)
  echo "==> Triggering backup on $instance"
  local cmd
  cmd=$(aws ssm send-command --instance-ids "$instance" \
    --document-name AWS-RunShellScript --region "$REGION" \
    --parameters 'commands=["systemctl start dbuff-backup.service","tail -5 /var/log/dbuff/backup.log"]' \
    --query 'Command.CommandId' --output text)
  sleep 30
  aws ssm get-command-invocation --command-id "$cmd" --instance-id "$instance" \
    --region "$REGION" --query '[Status,StandardOutputContent]' --output text
}

cmd_restore() {
  local key="${2:-}"
  : "${key:?Usage: $0 restore <s3-key-under-db-backups/>}"
  local instance
  instance=$(cmd_instance_id)
  echo "==> Restoring $key onto $instance (the app will be stopped)"
  # pg_restore gets '|| true' deliberately: it exits non-zero on benign warnings
  # such as an already-present extension, which would otherwise abort the SSM
  # command before the app is restarted. Verify by row count, not exit code.
  local cmd
  cmd=$(aws ssm send-command --instance-ids "$instance" \
    --document-name AWS-RunShellScript --region "$REGION" \
    --parameters "commands=[\
\"systemctl stop dbuff\",\
\"aws s3 cp s3://${BUCKET}/db-backups/${key} /tmp/restore.dump --region ${REGION}\",\
\"sudo -u postgres dropdb --if-exists dbuff\",\
\"sudo -u postgres createdb -O dbuffuser dbuff\",\
\"sudo -u postgres pg_restore -d dbuff --no-owner --role=dbuffuser /tmp/restore.dump || true\",\
\"rm -f /tmp/restore.dump\",\
\"systemctl start dbuff\"\
]" --query 'Command.CommandId' --output text)
  sleep 90
  aws ssm get-command-invocation --command-id "$cmd" --instance-id "$instance" \
    --region "$REGION" --query '[Status,StandardOutputContent,StandardErrorContent]' --output text
}

case "${1:-}" in
  build)      cmd_build ;;
  upload)     cmd_upload ;;
  deploy)     cmd_deploy ;;
  all)        cmd_build && cmd_upload && cmd_deploy ;;
  cicd)       cmd_cicd ;;
  backup-now) cmd_backup_now ;;
  restore)    cmd_restore "$@" ;;
  *)          usage ;;
esac
