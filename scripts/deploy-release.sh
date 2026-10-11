#!/usr/bin/env bash
#
# Install a release JAR on the dbuff instance and verify the application comes
# back up healthy.
#
# The CI workflow calls this for BOTH the forward deploy and the rollback, so
# both paths run identical code - a rollback is never a separately-written,
# never-executed implementation.
#
# Usage: deploy-release.sh <bucket> <instance-id> <sha> [region]
#
# Exits non-zero if the install command fails or if the app does not report
# {"status":"UP"} within HEALTH_ATTEMPTS * HEALTH_INTERVAL seconds.

set -euo pipefail

BUCKET="${1:?usage: $0 <bucket> <instance-id> <sha> [region]}"
INSTANCE="${2:?usage: $0 <bucket> <instance-id> <sha> [region]}"
SHA="${3:?usage: $0 <bucket> <instance-id> <sha> [region]}"
REGION="${4:-${AWS_REGION:-eu-north-1}}"

HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-30}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

source "$(dirname "${BASH_SOURCE[0]}")/lib/ssm.sh"

# Unescaped $VAR expands here (on the runner); \$VAR is escaped and evaluated on
# the instance.
install_script() {
  cat <<EOF
set -euo pipefail
aws s3 cp "s3://$BUCKET/releases/$SHA.jar" /opt/dbuff/server.jar --region "$REGION"
chown dbuff:dbuff /opt/dbuff/server.jar
systemctl restart dbuff
EOF
}

# Polls localhost rather than the Elastic IP: no dependency on security-group
# rules, no IP lookup, and journal output on failure.
health_script() {
  cat <<EOF
for i in \$(seq 1 $HEALTH_ATTEMPTS); do
  if curl -fsS localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "healthy after ~\$((i * $HEALTH_INTERVAL))s"
    exit 0
  fi
  sleep $HEALTH_INTERVAL
done
echo "NOT UP after $((HEALTH_ATTEMPTS * HEALTH_INTERVAL))s. Recent application log:"
journalctl -u dbuff -n 200 --no-pager
exit 1
EOF
}

command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required" >&2; exit 1; }

log "Deploying $SHA to $INSTANCE (region $REGION)"
run_ssm "install $SHA" "$(install_script)"
run_ssm "health check $SHA" "$(health_script)"
log "Release $SHA is live and healthy"
