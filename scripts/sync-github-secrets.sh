#!/usr/bin/env bash
#
# Sync GitHub Actions secrets for this repository.
#
# GitHub secrets are write-only: the API never returns a stored value, so there
# is no way to export secrets *out of* GitHub. This script only pushes values
# up, from the local .env plus values derived from AWS.
#
# Values are passed to `gh` over stdin, never as an argv `--body`, so they do not
# appear in `ps` output or in your shell history.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$PROJECT_ROOT/.env"
REPO=""
DRY_RUN=0
PUSH_ALL=0
EXPLICIT=()

# Secrets the CI/CD pipeline actually needs. Neither lives in .env; both are
# derived from AWS. See docs/superpowers/specs/2026-08-19-app-cicd-design.md.
CI_SECRETS="AWS_ACCOUNT_ID AWS_DEPLOY_ROLE_ARN"

# Present in .env but not sensitive - plain configuration. Skipped by --all so
# that "all secrets" does not silently turn config into secrets.
NOT_SECRETS="AWS_REGION DB_URL DB_USERNAME INSTANCE_TYPE ALLOWED_SSH_CIDR"

# IAM role name fixed by cicd.yaml.
DEPLOY_ROLE_NAME="dbuff-github-deploy"

die()  { echo "ERROR: $*" >&2; exit 1; }
warn() { echo "WARN:  $*" >&2; }
info() { echo "==> $*"; }

usage() {
  cat <<EOF
Usage: $(basename "$0") [command] [options]

Commands:
  push       Set the secrets CI/CD needs (default)
  list       Show which secrets currently exist on the repo
  help       This message

Options:
  --repo OWNER/NAME  Target repo (default: derived from the 'origin' remote)
  --env FILE         Env file to read (default: <repo root>/.env)
  --set KEY=VALUE    Set one secret explicitly; repeatable. Bypasses .env.
  --all              Also push every secret-looking key found in the env file
  --dry-run          Report what would change without changing anything
  -h, --help         This message

Notes:
  GitHub secrets cannot be read back. There is no 'export' direction.

  'push' with no --all sets only:
      $CI_SECRETS
  AWS_ACCOUNT_ID comes from 'aws sts get-caller-identity'. AWS_DEPLOY_ROLE_ARN is
  read from the dbuff-cicd stack if it exists, else assembled by convention as
  arn:aws:iam::<account>:role/$DEPLOY_ROLE_NAME.

  '--all' additionally pushes the API keys from .env. The CI/CD workflow needs
  none of them - it only ships a JAR - so they would sit unused on GitHub. Use it
  only if a future workflow genuinely needs them.

  GOOGLE_VISION_CREDENTIALS_PATH is a local filesystem path, so pushing it
  verbatim would be useless. Under --all the referenced file is base64-encoded
  and pushed as GOOGLE_VISION_CREDENTIALS_B64, matching what deploy.sh does.

Examples:
  $(basename "$0") push --dry-run
  $(basename "$0") push
  $(basename "$0") push --set AWS_DEPLOY_ROLE_ARN=arn:aws:iam::123456789012:role/$DEPLOY_ROLE_NAME
EOF
}

# ---------- env parsing ----------

# Read one key from an env file. Values may contain '=', spaces, or '#', so only
# the first '=' is treated as the separator and inline comments are NOT stripped.
env_get() {
  local key="$1" file="$2" line value
  line="$(grep -E "^${key}=" "$file" 2>/dev/null | tail -1 || true)"
  [ -n "$line" ] || return 1
  value="${line#*=}"
  value="${value%$'\r'}"        # tolerate CRLF files
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  [ -n "$value" ] || return 1
  printf '%s' "$value"
}

env_keys() {
  grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$1" 2>/dev/null | tr -d '=' || true
}

in_list() {
  local needle="$1"; shift
  local item
  for item in $*; do [ "$item" = "$needle" ] && return 0; done
  return 1
}

# ---------- preflight ----------

derive_repo() {
  local url path
  url="$(git -C "$PROJECT_ROOT" remote get-url origin 2>/dev/null || true)"
  [ -n "$url" ] || return 1
  case "$url" in
    *://*) path="${url#*://}"; path="${path#*/}" ;;   # https://host/owner/repo
    *:*)   path="${url##*:}" ;;                       # git@host:owner/repo (incl. SSH aliases)
    *)     return 1 ;;
  esac
  path="${path%.git}"
  case "$path" in */*) printf '%s' "$path" ;; *) return 1 ;; esac
}

require_gh() {
  command -v gh >/dev/null 2>&1 \
    || die "gh not found. Install with: brew install gh"

  # This repo is on public GitHub. Being logged into an enterprise host such as
  # an internal GHE host does not grant access here.
  gh auth status --hostname github.com >/dev/null 2>&1 \
    || die "gh is not authenticated to github.com. Run:
         gh auth login --hostname github.com --git-protocol ssh --web
       An existing enterprise-host login is kept alongside it."
}

require_env_file() {
  [ -f "$ENV_FILE" ] || die "env file not found: $ENV_FILE"
  # A tracked env file would mean secrets are committed.
  if git -C "$PROJECT_ROOT" ls-files --error-unmatch "$ENV_FILE" >/dev/null 2>&1; then
    die "$ENV_FILE is tracked by git. Untrack it before syncing secrets."
  fi
}

# ---------- aws-derived values ----------

aws_account_id() {
  command -v aws >/dev/null 2>&1 || return 1
  aws sts get-caller-identity --query Account --output text 2>/dev/null || return 1
}

deploy_role_arn() {
  local acct="$1" region arn
  region="$(env_get AWS_REGION "$ENV_FILE" 2>/dev/null || echo eu-north-1)"

  # Prefer the real stack output; fall back to the conventional name so this
  # works before dbuff-cicd has been created.
  arn="$(aws cloudformation describe-stacks --stack-name dbuff-cicd --region "$region" \
          --query "Stacks[0].Outputs[?OutputKey=='DeployRoleArn'].OutputValue" \
          --output text 2>/dev/null || true)"
  if [ -n "$arn" ] && [ "$arn" != "None" ]; then
    printf '%s' "$arn"
  else
    printf 'arn:aws:iam::%s:role/%s' "$acct" "$DEPLOY_ROLE_NAME"
  fi
}

# ---------- actions ----------

# Never echo a value; report only its name and length.
set_secret() {
  local key="$1" value="$2"
  if [ -z "$value" ]; then
    warn "$key: empty value, skipped"
    return 0
  fi
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "    would set $key (${#value} chars)"
    return 0
  fi
  printf '%s' "$value" | gh secret set "$key" --repo "$REPO"
  echo "    set $key (${#value} chars)"
}

cmd_push() {
  local key value acct pushed=0

  info "Target repo: $REPO"
  [ "$DRY_RUN" -eq 1 ] && info "DRY RUN - nothing will be changed"

  # 1. Explicit --set values win and are never read from .env.
  for kv in ${EXPLICIT+"${EXPLICIT[@]}"}; do
    key="${kv%%=*}"; value="${kv#*=}"
    set_secret "$key" "$value"; pushed=$((pushed + 1))
  done

  # 2. CI secrets, derived from AWS unless already given via --set.
  if ! in_list AWS_ACCOUNT_ID ${EXPLICIT+"${EXPLICIT[@]%%=*}"}; then
    acct="$(aws_account_id || true)"
    if [ -n "$acct" ]; then
      set_secret AWS_ACCOUNT_ID "$acct"; pushed=$((pushed + 1))
      if ! in_list AWS_DEPLOY_ROLE_ARN ${EXPLICIT+"${EXPLICIT[@]%%=*}"}; then
        set_secret AWS_DEPLOY_ROLE_ARN "$(deploy_role_arn "$acct")"
        pushed=$((pushed + 1))
      fi
    else
      warn "Could not reach AWS (is the CLI configured?). Skipping $CI_SECRETS."
      warn "Pass them by hand: --set AWS_ACCOUNT_ID=... --set AWS_DEPLOY_ROLE_ARN=..."
    fi
  fi

  # 3. Everything else from .env, only on request.
  if [ "$PUSH_ALL" -eq 1 ]; then
    warn "--all pushes API keys the current workflow does not use; they will sit"
    warn "unused on GitHub and stay valid until deleted."
    for key in $(env_keys "$ENV_FILE"); do
      if in_list "$key" $NOT_SECRETS; then
        echo "    skip $key (configuration, not a secret)"
        continue
      fi
      if [ "$key" = "GOOGLE_VISION_CREDENTIALS_PATH" ]; then
        local cred
        cred="$(env_get "$key" "$ENV_FILE" || true)"
        [ -n "$cred" ] || continue
        case "$cred" in /*) ;; *) cred="$PROJECT_ROOT/$cred" ;; esac
        if [ -f "$cred" ]; then
          set_secret GOOGLE_VISION_CREDENTIALS_B64 "$(base64 < "$cred" | tr -d '\n')"
          pushed=$((pushed + 1))
        else
          warn "$key points at a missing file, skipped: $cred"
        fi
        continue
      fi
      value="$(env_get "$key" "$ENV_FILE" || true)"
      set_secret "$key" "$value"; pushed=$((pushed + 1))
    done
  fi

  info "$pushed secret(s) processed"
  [ "$PUSH_ALL" -eq 1 ] || info "Run with --all to also push the API keys from .env"
}

cmd_list() {
  info "Secrets on $REPO (names and timestamps only - values are unreadable)"
  gh secret list --repo "$REPO"
}

# ---------- arg parsing ----------

COMMAND="push"
case "${1:-}" in
  push|list)       COMMAND="$1"; shift ;;
  help|-h|--help)  usage; exit 0 ;;
  "")              ;;
  --*)             ;;
  *)               die "Unknown command: $1 (try --help)" ;;
esac

while [ $# -gt 0 ]; do
  case "$1" in
    --repo)     REPO="${2:?--repo needs a value}"; shift 2 ;;
    --env)      ENV_FILE="${2:?--env needs a value}"; shift 2 ;;
    --set)
      case "${2:-}" in
        *=*) EXPLICIT+=("$2") ;;
        *)   die "--set expects KEY=VALUE" ;;
      esac
      shift 2 ;;
    --all)      PUSH_ALL=1; shift ;;
    --dry-run)  DRY_RUN=1; shift ;;
    -h|--help)  usage; exit 0 ;;
    *)          die "Unknown option: $1 (try --help)" ;;
  esac
done

require_gh
[ -n "$REPO" ] || REPO="$(derive_repo)" \
  || die "Could not derive the repo from the 'origin' remote. Pass --repo OWNER/NAME."

case "$COMMAND" in
  push)  require_env_file; cmd_push ;;
  list)  cmd_list ;;
esac
