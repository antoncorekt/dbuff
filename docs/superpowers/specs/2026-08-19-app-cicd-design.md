# Application CI/CD — Design

**Date:** 2026-08-19
**Status:** Approved, pending implementation plan
**Scope:** Application build/test/deploy only. Infrastructure deployment is explicitly out of scope.

## Problem

`.github/workflows/deploy.yml` deploys on every push to `main`, but it is not a
CI/CD pipeline in any meaningful sense:

1. **Nothing is verified.** The job runs `:server:bootJar` and nothing else. No
   tests, no `spotlessCheck`, and no CI runs on pull requests or feature branches.
2. **Failures are swallowed.** The CloudFormation step ends in `|| true`, which
   was intended to tolerate "No updates are to be performed" but in practice
   masks every error, including a malformed template.
3. **Success is not verified.** `aws ssm send-command` is fire-and-forget. The
   workflow never waits for the command, never checks its exit status, and never
   polls `/actuator/health`. A JAR that dies on startup produces a green run.
4. **There is nothing to roll back to.** Every build overwrites
   `s3://dbuff-deploy-<acct>/server.jar`, and the upload path hardcodes
   `server-0.0.1-SNAPSHOT.jar`, which silently breaks the moment `version` in
   `gradle.properties` changes.
5. **Long-lived AWS credentials.** `secrets.AWS_ACCESS_KEY_ID` /
   `AWS_SECRET_ACCESS_KEY` never expire and stay valid if leaked.
6. **`update-stack` runs on every application push.** Since the RDS removal,
   PostgreSQL lives *on* the EC2 instance. Any template change that triggers an
   instance replacement destroys the database, and the nightly `pg_dump` is the
   only copy. Coupling that to routine app deploys is the single largest risk in
   the current setup.

## Decisions

| Decision | Choice |
|---|---|
| Deploy trigger | Push to `main` (plus `workflow_dispatch` for manual redeploy) |
| Branch protection | None — direct pushes to `main` are allowed; CI gates the *deploy*, not the merge |
| Environments | Production only. No staging, no second instance. |
| AWS authentication | GitHub OIDC role assumption. No static keys. |
| Infrastructure changes | Remain manual via `infrastructure/cloudformation/deploy.sh`. The workflow never calls CloudFormation mutating APIs. |
| Test selection | Run the entire suite. See "Test selection" below. |
| Rollback | Redeploy the previous JAR. Database is **not** rolled back. |

### Test selection

The option of excluding integration tests from the CI build was considered and
rejected on measurement. The full suite is **88 tests in 4.0 seconds** and no
test requires external infrastructure:

| Kind | Classes | Requires |
|---|---|---|
| `@DataJpaTest` | `PlayerStatisticRepositoryTest`, `AbilityRankingRepositoryTest`, `ItemRankingRepositoryTest`, `FindPlayerMatchesRepositoryTest` | H2 in-memory, configured by `server/src/test/resources/application-test.properties` |
| `@ExtendWith(MockitoExtension.class)` | `ImageProcessorTest`, `ScoreboardStatisticServiceTest`, `ExternalPlayerStatisticServiceTest` | Nothing — `ImageAnnotatorClient` is mocked |
| Plain JUnit | `DiscordStatisticFormatterTest`, `QuietHoursGuardTest`, `HeroesAbilityConstantTest`, `DbuffApplicationTests` | Nothing |

Nothing touches PostgreSQL, OpenDota, ScraperAPI, Discord, OpenAI, or Google
Vision. `./gradlew clean build` is green in 32 s on a cold cache. There is also
no existing mechanism to split the suite — no `integrationTest` source set and
no JUnit `@Tag`s — so excluding tests would mean building and maintaining a
tagging scheme to save roughly one second while removing repository-layer
coverage from CI. Revisit only when a test that genuinely needs external
infrastructure is written.

## Architecture

```
push (any branch) ─┐
pull_request ──────┼──▶ verify ─┬── ✗ ──▶ stop
workflow_dispatch ─┘            │
                                └── ✓ ──▶ deploy   [if: ref == main && event != pull_request]
                                              │
                                              ├─ upload releases/<sha>.jar
                                              ├─ SSM: install + restart, WAIT for result
                                              ├─ health gate (poll /actuator/health)
                                              ├─ ✓ ─▶ record current-sha, prune to last 10
                                              └─ ✗ ─▶ redeploy previous sha, re-check, FAIL the run
```

Two jobs in one file, `.github/workflows/ci-cd.yml`, replacing `deploy.yml`.

### Job `verify`

Runs on every push, every pull request, and manual dispatch.

- `actions/checkout@v4`
- `actions/setup-java@v4` — `distribution: corretto`, `java-version: 21`,
  `cache: gradle`
- `./gradlew build`

One task covers everything: it compiles both modules, runs all 88 tests, and
enforces formatting, because `spotless { enforceCheck = true }` is already set
(`build.gradle:72`) which wires `spotlessCheck` into `check`.

- Upload `server/build/libs/server-*.jar` via `actions/upload-artifact@v4`
- On failure, upload `server/build/reports/tests/test/` for diagnosis

Concurrency: `group: verify-${{ github.ref }}`, `cancel-in-progress: true`. A
superseded commit's verify run is cancelled, which also prevents its deploy.

The JAR is resolved by glob, never by hardcoded name. `server`'s plain `jar` task
is disabled (`build.gradle:168-170`), so `bootJar` produces exactly one artifact
and the glob is unambiguous. This removes the `version`-change breakage.

### Job `deploy`

```yaml
needs: verify
if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
permissions:
  id-token: write    # required for OIDC
  contents: read
concurrency:
  group: deploy-prod
  cancel-in-progress: false
```

`cancel-in-progress: false` is deliberate. There is one instance; two concurrent
deploys would race on `systemctl restart dbuff`. Queue them, and never cancel a
deploy midway through a restart.

**The deploy downloads the artifact from `verify` rather than rebuilding it**, so
the bytes that reach production are the exact bytes that passed the tests.

Sequence:

1. `actions/download-artifact@v4`
2. `aws-actions/configure-aws-credentials@v4` with
   `role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}`, `aws-region: eu-north-1`
3. Upload to `s3://dbuff-deploy-<acct>/releases/${{ github.sha }}.jar`
4. Read `s3://dbuff-deploy-<acct>/releases/current-sha` into `PREVIOUS_SHA`.
   If the object does not exist (first deploy), set a flag that disables the
   rollback step and log a warning — do not fail.
5. Resolve the instance ID the same way `deploy.sh` does, for consistency:
   ```bash
   aws cloudformation describe-stack-resources --stack-name dbuff \
     --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
     --output text
   ```
6. `aws ssm send-command` with `AWS-RunShellScript`:
   ```bash
   aws s3 cp s3://<bucket>/releases/<sha>.jar /opt/dbuff/server.jar
   chown dbuff:dbuff /opt/dbuff/server.jar
   systemctl restart dbuff
   ```
7. **`aws ssm wait command-executed --command-id … --instance-id …`**, then
   assert the invocation status is `Success`. On failure, print
   `StandardErrorContent` from `get-command-invocation`. This is the check the
   current workflow omits entirely, and the reason it cannot fail.
8. **Health gate.** A single SSM command that loops on the instance:
   ```bash
   for i in $(seq 1 30); do
     curl -fsS localhost:8080/actuator/health | grep -q '"status":"UP"' && exit 0
     sleep 5
   done
   journalctl -u dbuff -n 200 --no-pager
   exit 1
   ```
   Polling `localhost` rather than the Elastic IP means no dependency on
   security-group rules and no IP lookup, and a failure returns 200 lines of the
   real stack trace into the Actions log via `StandardOutputContent`. Prod
   already exposes the endpoint — `management.endpoints.web.exposure.include=health`
   in `application-prod.properties`. Budget: 30 × 5 s = 150 s.
9. On success:
   - Write `${{ github.sha }}` to `s3://<bucket>/releases/current-sha`
   - **Also copy the JAR to `s3://<bucket>/server.jar`.** The instance `UserData`
     fetches that exact key at first boot (`template.yaml:478`). Since
     infrastructure is out of scope we are not editing `UserData`, so keeping
     `server.jar` current means a future instance rebuild via `deploy.sh` comes
     up on the current release instead of a stale one.
   - Prune `releases/*.jar` to the most recent 10. The `current-sha` object is
     not a `.jar` and is filtered out of the prune list.
10. On failure (`if: failure()`), and only when `PREVIOUS_SHA` is known:
    re-run steps 6–8 with `PREVIOUS_SHA`, then **fail the job regardless**.
    Production recovers; the run stays red so the failure is not silently
    absorbed.

### OIDC: a separate CloudFormation stack

New file `infrastructure/cloudformation/cicd.yaml`, deployed as its own stack
named `dbuff-cicd`, containing only:

- `AWS::IAM::OIDCProvider` for `token.actions.githubusercontent.com`
  (audience `sts.amazonaws.com`)
- `AWS::IAM::Role` named `dbuff-github-deploy`

This is deliberately **not** added to the existing `dbuff` template. Adding it
there would require running `update-stack` against the stack that owns the
database purely to set up CI, which is exactly the coupling this design removes.
A separate stack also means the CI role can be torn down without touching the
application.

Trust policy condition:

```
StringEquals:
  token.actions.githubusercontent.com:aud: sts.amazonaws.com
  token.actions.githubusercontent.com:sub: repo:antoncorekt/dbuff:ref:refs/heads/main
```

Pinning `sub` to the `main` ref means a fork or a feature branch cannot assume
the role even if the workflow file is modified there.

Permissions, least-privilege:

| Action | Resource |
|---|---|
| `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` | `arn:aws:s3:::dbuff-deploy-<acct>/releases/*` and `…/server.jar` |
| `s3:ListBucket` | `arn:aws:s3:::dbuff-deploy-<acct>`, condition `s3:prefix` = `releases/*` |
| `ssm:SendCommand` | `arn:aws:ssm:eu-north-1::document/AWS-RunShellScript` |
| `ssm:SendCommand` | `arn:aws:ec2:eu-north-1:<acct>:instance/*`, condition `ssm:resourceTag/Name` = `dbuff-instance` |
| `ssm:GetCommandInvocation`, `ssm:ListCommandInvocations` | `*` |
| `cloudformation:DescribeStackResources` | `arn:aws:cloudformation:eu-north-1:<acct>:stack/dbuff/*` |

`ssm:SendCommand` must be **two separate statements**. A single statement listing
both the document and the instance ARN with the tag condition would deny the
call, because the document carries no `Name` tag and the condition would fail
against it.

**No change to the `dbuff` stack is required.** `Ec2Role` already grants
`s3:GetObject` and `s3:ListBucket` on the entire `dbuff-deploy-<acct>` bucket
(`template.yaml:229-235`), so the instance can already read `releases/<sha>.jar`.
The instance is already tagged `Name=dbuff-instance` (`template.yaml:516-517`)
and already carries `AmazonSSMManagedInstanceCore`.

**Pre-flight check:** if an OIDC provider for `token.actions.githubusercontent.com`
already exists in the account, `create-stack` fails with
`EntityAlreadyExists`. Verify first with
`aws iam list-open-id-connect-providers`, and if one exists, drop the provider
resource from `cicd.yaml` and reference the existing ARN instead.

### `deploy.sh`

Add one command, `cicd`, that deploys the `dbuff-cicd` stack, so the new stack is
managed the same way as the existing one. No existing command changes behaviour.

## Secrets

Add two:

| Secret | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::<acct>:role/dbuff-github-deploy` |
| `AWS_ACCOUNT_ID` | already present, used to build the bucket name |

Delete eight, which existed only to feed `update-stack`. They remain in the
local `.env`, which is what `deploy.sh` reads:

`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `KEY_PAIR_NAME`, `DB_PASSWORD`,
`DOTA_API_KEY`, `SCRAPPER_API_KEY`, `OPENAI_API_KEY`, `DISCORD_BOT_TOKEN`.

Operationally, `gh` is currently authenticated only to `an internal GHE host`.
This repository is on public GitHub — the `github-antoncorekt` remote is an SSH
alias with `HostName github.com`, owner `antoncorekt/dbuff` — so a second host
login is needed: `gh auth login --hostname github.com`. `gh` holds both hosts
concurrently. Use `gh secret set NAME` and let it prompt, rather than
`--body "value"`, which writes the secret into shell history.

## Error handling

| Failure | Behaviour |
|---|---|
| Compile, test, or spotless failure | `verify` fails; `deploy` never starts; production untouched |
| S3 upload failure | `deploy` fails before the instance is touched |
| SSM command not delivered / non-zero | Job fails with `StandardErrorContent` printed; rollback runs |
| App starts but health never reports UP | Health gate fails after 150 s with 200 journal lines printed; rollback runs |
| Rollback itself fails | Job fails loudly; requires manual intervention |
| First-ever deploy (no `current-sha`) | Rollback skipped with a warning; job still fails on health failure |
| Two pushes in quick succession | Second deploy queues behind the first via `concurrency` |

## Known limitations

These are accepted, not solved, and are recorded so they are not mistaken for
guarantees.

1. **Rollback restores the JAR, not the database.** `ddl-auto=update` means a new
   release can add columns or tables during startup, before the health check
   fails. Reverting the JAR leaves those schema changes in place. Additive
   Hibernate changes are normally harmless to the previous JAR, but this is not a
   true rollback and must not be treated as one. Taking a
   `deploy.sh backup-now` before a schema-affecting release remains a manual
   judgement call.
2. **CI cannot detect a Spring context failure.** `@SpringBootTest` is commented
   out at `DbuffApplicationTests.java:11`, so no test proves the application
   boots. The health gate catches it at deploy time instead, which costs a failed
   deploy plus a rollback rather than a failed CI run. Closing this properly needs
   a PostgreSQL service container in CI, which is out of scope for this iteration.
3. **Template changes do not ship automatically.** After editing
   `template.yaml` you must run `deploy.sh deploy` yourself. This is the
   intended trade for not automating a database-destroying operation.
4. **Rollback is not exercised until it is needed.** See "Verification" for the
   drill that partially mitigates this.

## Verification

1. **Static:** run `actionlint` against `.github/workflows/ci-cd.yml`, and
   `aws cloudformation validate-template` against `cicd.yaml`.
2. **`verify` job:** confirmed working locally — `./gradlew clean build` is green,
   88 tests, 0 failures, 32 s cold. Push a branch and confirm the job runs and
   the artifact is produced.
3. **Deliberate red:** push a branch with a knowingly-broken test and confirm
   `verify` fails and `deploy` is skipped.
4. **First deploy:** trigger via `workflow_dispatch` on `main` rather than
   waiting for a push, so it happens while being watched. Confirm
   `releases/<sha>.jar`, `current-sha`, and `server.jar` all land in S3 and the
   health gate passes.
5. **Health-gate drill (low risk):** with the app healthy, run the health-gate
   script manually via SSM against a deliberately wrong port. It must exit 1 and
   return journal output. This validates the gate's failure path without causing
   an outage.
6. **Rollback:** exercised for real only on a genuine failure. Reviewed by
   inspection; step 10 reuses the same install-and-health-check code path as the
   forward deploy, so it is not a separate untested implementation.

## Files

| File | Change |
|---|---|
| `.github/workflows/ci-cd.yml` | New — `verify` + `deploy` |
| `.github/workflows/deploy.yml` | Delete |
| `infrastructure/cloudformation/cicd.yaml` | New — OIDC provider + deploy role |
| `infrastructure/cloudformation/deploy.sh` | Add a `cicd` command |
| `CLAUDE.md` | Document the deploy path and that infra stays manual |
| `infrastructure/cloudformation/template.yaml` | **No change** |
