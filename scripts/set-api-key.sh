#!/usr/bin/env bash
#
# Install or rotate the REST API key (DBUFF_API_KEY) on the dbuff instance.
#
# The key is generated here and stored as an SSM SecureString. The instance fetches it itself and
# writes a systemd drop-in, so the key never appears in an SSM command's text or history. A drop-in
# survives JAR deploys; the instance UserData runs only on first boot, so it cannot deliver this.
#
# Usage: set-api-key.sh <instance-id> [region]
#   ROTATE=1 set-api-key.sh <instance-id>   replace an existing key
#
# Does not print the key, and a re-run without ROTATE=1 never replaces an existing one.
# Read it back with:
#   aws ssm get-parameter --name /dbuff/DBUFF_API_KEY --with-decryption \
#     --query Parameter.Value --output text

set -euo pipefail

INSTANCE="${1:?usage: $0 <instance-id> [region]}"
REGION="${2:-${AWS_REGION:-eu-north-1}}"
PARAM="/dbuff/DBUFF_API_KEY"
DROPIN_DIR="/etc/systemd/system/dbuff.service.d"

source "$(dirname "${BASH_SOURCE[0]}")/lib/ssm.sh"

# Without ROTATE, put-parameter runs with no --overwrite, so an existing key can never be replaced
# by accident: any failure other than "already exists" (throttling, expired credentials, access
# denied) stops the script instead of being mistaken for "no key yet".
store_key() {
  local out
  keyfile="$(mktemp)"
  trap 'rm -f "$keyfile"' EXIT
  # Passed as a file so the key never appears in this machine's process list.
  printf '%s' "$(openssl rand -hex 32)" > "$keyfile"

  if [ "${ROTATE:-}" = "1" ]; then
    aws ssm put-parameter --name "$PARAM" --type SecureString --overwrite --region "$REGION" \
      --value "file://$keyfile" >/dev/null
    log "replaced the key at $PARAM"
    return
  fi

  if out="$(aws ssm put-parameter --name "$PARAM" --type SecureString --region "$REGION" \
    --value "file://$keyfile" 2>&1)"; then
    log "stored a new key at $PARAM"
  elif grep -q ParameterAlreadyExists <<<"$out"; then
    log "$PARAM already exists, keeping it (ROTATE=1 replaces it)"
  else
    echo "$out" >&2
    return 1
  fi
}

# Written to a temp file and moved into place so a failed fetch never leaves a half-written
# drop-in; umask keeps the key readable by root only.
install_script() {
  cat <<EOS
set -euo pipefail
umask 077
key="\$(aws ssm get-parameter --name "$PARAM" --with-decryption --region "$REGION" \
  --query Parameter.Value --output text)"
# Only plain token characters: a newline would inject unit directives, and "%", spaces or quotes
# would be mangled by systemd so the app would see a different key from the stored one.
[[ "\$key" =~ ^[A-Za-z0-9._~-]{32,}\$ ]] || { echo "stored key is not 32+ plain characters" >&2; exit 1; }
install -d -m 755 "$DROPIN_DIR"
tmp="\$(mktemp "$DROPIN_DIR/.api-key.XXXXXX")"
printf '[Service]\nEnvironment=DBUFF_API_KEY=%s\n' "\$key" > "\$tmp"
chmod 600 "\$tmp"
mv "\$tmp" "$DROPIN_DIR/api-key.conf"
systemctl daemon-reload
systemctl restart dbuff
EOS
}

# Health must come back; a caller with no key must be refused; and the stored key must actually be
# accepted, which is the only thing that tells "key loaded" from "API closed because it never was".
# The key goes to curl on stdin so it is never on a command line.
verify_script() {
  cat <<EOS
for i in \$(seq 1 30); do
  if curl -fsS localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    key="\$(aws ssm get-parameter --name "$PARAM" --with-decryption --region "$REGION" \
      --query Parameter.Value --output text)"
    without="\$(curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/v1/register)"
    with="\$(printf 'header = "X-Api-Key: %s"\n' "\$key" \
      | curl -s -K - -o /dev/null -w '%{http_code}' localhost:8080/api/v1/register)"
    echo "health UP; no key -> \$without (expected 401), stored key -> \$with (expected not 401)"
    [ "\$without" = "401" ] && [ "\$with" != "401" ] && [ "\$with" != "000" ]
    exit
  fi
  sleep 5
done
echo "NOT UP after 150s"
journalctl -u dbuff -n 40 --no-pager
exit 1
EOS
}

store_key
run_ssm "install api key" "$(install_script)"
run_ssm "verify api key" "$(verify_script)"
log "API key active. Callers send it as the X-Api-Key header."
