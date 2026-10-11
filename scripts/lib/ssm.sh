# Shared helpers for scripts that run commands on the dbuff instance via SSM.
# Source it after setting INSTANCE and REGION; it defines log and run_ssm.

log() { echo "==> $*"; }

# Run a script on the instance via SSM, wait for a terminal state, echo its
# output, and propagate failure. `aws ssm send-command` alone is fire-and-forget;
# without the wait a crashing application looks like a successful deploy.
run_ssm() {
  local label="$1" script="$2" cmd status

  cmd="$(aws ssm send-command \
    --instance-ids "$INSTANCE" \
    --document-name AWS-RunShellScript \
    --region "$REGION" \
    --comment "$label" \
    --parameters "$(jq -n --arg s "$script" '{commands: [$s]}')" \
    --query 'Command.CommandId' --output text)"
  log "$label -> command $cmd"

  # Non-zero means a terminal non-Success state; the status query below reports
  # which one, so the waiter's own exit code is not needed.
  aws ssm wait command-executed \
    --command-id "$cmd" --instance-id "$INSTANCE" --region "$REGION" || true

  status="$(aws ssm get-command-invocation \
    --command-id "$cmd" --instance-id "$INSTANCE" --region "$REGION" \
    --query Status --output text)"

  aws ssm get-command-invocation \
    --command-id "$cmd" --instance-id "$INSTANCE" --region "$REGION" \
    --query StandardOutputContent --output text

  if [ "$status" != "Success" ]; then
    echo "--- stderr ---" >&2
    aws ssm get-command-invocation \
      --command-id "$cmd" --instance-id "$INSTANCE" --region "$REGION" \
      --query StandardErrorContent --output text >&2 || true
    log "$label FAILED (status=$status)"
    return 1
  fi

  log "$label OK"
}
