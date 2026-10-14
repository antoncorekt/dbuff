#!/usr/bin/env bash
#
# Run something on the dbuff instance through SSM (the helper of docs/superpowers/plans/
# 2026-10-14-replay-switch-on-checklist.md). Failures of the command or of SSM exit non-zero.
#
# Usage:
#   replay-host.sh <instance-id> sh '<shell command>'   run a command as root on the host
#   replay-host.sh <instance-id> get '<path>'           GET localhost:8080<path> with the API key
#
# The API key is read from SSM on the host and handed to curl on stdin, so it never appears in a
# command line, in SSM's command text or on this machine. Region: AWS_REGION, default eu-north-1.

set -euo pipefail

INSTANCE="${1:?usage: $0 <instance-id> sh|get <argument>}"
MODE="${2:?usage: $0 <instance-id> sh|get <argument>}"
ARG="${3:?usage: $0 <instance-id> sh|get <argument>}"
REGION="${AWS_REGION:-eu-north-1}"

source "$(dirname "${BASH_SOURCE[0]}")/lib/ssm.sh"

command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required" >&2; exit 1; }

get_script() {
  cat <<EOF
set -euo pipefail
key="\$(aws ssm get-parameter --name /dbuff/DBUFF_API_KEY --with-decryption --region "$REGION" \\
  --query Parameter.Value --output text)"
printf 'header = "X-Api-Key: %s"\n' "\$key" | curl -fsS -K - 'localhost:8080$ARG'
EOF
}

case "$MODE" in
  sh)  run_ssm "host command" "$ARG" ;;
  get) run_ssm "GET $ARG" "$(get_script)" ;;
  *)   echo "mode must be sh or get" >&2; exit 2 ;;
esac
