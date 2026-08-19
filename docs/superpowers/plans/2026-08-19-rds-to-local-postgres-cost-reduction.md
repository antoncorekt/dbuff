# AWS Cost Reduction: RDS → Local Postgres on Graviton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut the DBuff AWS bill from ~$42/mo to ~$20/mo by moving PostgreSQL from RDS onto the application EC2 instance and switching that instance to ARM/Graviton.

**Architecture:** Delete the `AWS::RDS::DBInstance` and run PostgreSQL 16 directly on the app instance, reached over `localhost`. Replace RDS automated backups with a nightly `pg_dump` to the existing S3 deploy bucket, driven by a systemd timer. Switch the instance from `t3.small` (x86) to `t4g.small` (Graviton2) and grow the root volume to 40 GB to hold the database.

**Tech Stack:** CloudFormation, Amazon Linux 2023 (arm64), PostgreSQL 16, systemd, Spring Boot 3.5 / Java 21 (Corretto aarch64), AWS CLI.

---

## Cost Model

Region is **eu-north-1** (`infrastructure/cloudformation/deploy.sh:21`). All prices below are eu-north-1 on-demand.

### Current (actual bill: $42.20/mo)

| Service | Component | $/mo |
|---|---|---|
| EC2 | t3.small @ $0.0216/hr | 15.77 |
| EC2 | 20 GB gp3 root @ ~$0.0836/GB | 1.67 |
| RDS | db.t3.micro @ ~$0.019/hr | 13.87 |
| RDS | 20 GB gp3 @ ~$0.127/GB | 2.54 |
| RDS | backup storage beyond free tier | ~0.60 |
| VPC | **2 ×** public IPv4 @ $0.005/hr | 7.30 |
| S3 | deploy artifacts | 0.01 |
| | **Total** | **~$41.8** |

The two IPv4 addresses are the Elastic IP on the instance and the RDS instance's own public address, which exists only because `template.yaml:311` sets `PubliclyAccessible: true`.

### Target

| Component | $/mo |
|---|---|
| t4g.small @ ~$0.0176/hr | 12.85 |
| 40 GB gp3 root | 3.34 |
| 1 × public IPv4 (the EIP) | 3.65 |
| S3 (artifacts + rolling DB backups) | ~0.10 |
| **Total** | **~$19.9** |

**Saving: ~$22/mo (~$264/yr).** A 1-year no-upfront Compute Savings Plan would take this to ~$16.3/mo, but that is deliberately deferred to Task 13 until the instance size is proven.

## Decisions and Trade-offs

These were settled during design discussion; recording them so the implementer does not relitigate them:

- **Losing RDS point-in-time recovery.** Replaced by a nightly logical dump. Worst-case data loss goes from ~5 minutes to ~24 hours, and restore becomes a manual `pg_restore`. Accepted for a hobby project.
- **`synchronous_commit = off`.** Trades up to ~200 ms of committed transactions on an unclean shutdown for materially better write throughput on gp3. Accepted; flagged in the config so it can be reverted with one line.
- **2 GiB is tight, not comfortable.** Budget is ~1.4 GB of 2 GB (OS ~250 MB, JVM ~850 MB at `Xmx512m`, Postgres ~330 MB). A 2 GB swapfile is the safety net. Instance resize is a 2-minute stop/start, so starting small is a reversible bet — Task 11 defines the metric that would trigger a move to `t4g.medium`.
- **ARM is safe here.** Corretto 21 has an aarch64 build and the `bootJar` is pure bytecode, so no build change is needed. The only native dependency is Playwright, and `ScrapperServicePlaywright` is a `@Service` that nothing injects — `Playwright.create()` is never called, so no browser is ever downloaded or launched. If Playwright is ever wired up, its arm64 browser install becomes a new problem; `t3a.small` ($13.72/mo) is the x86 fallback.

## Pre-existing Issues Discovered (context, mostly out of scope)

1. **Flyway is not installed.** There is no `flyway` dependency in `build.gradle` and no Flyway configuration anywhere. `server/src/main/resources/db/migration/V2__item_ranking_indexes.sql` through `V5__*.sql` have **never executed**. The schema is created solely by `spring.jpa.hibernate.ddl-auto=update` (as `application-prod.properties:16` admits). Consequences for this plan: `pg_dump` is the only source of truth for the schema, and the indexes in `V2__item_ranking_indexes.sql` do not exist in the running database. Task 12 notes this; actually adding Flyway is **not** in scope.
2. **`CLAUDE.md` is wrong** where it says "Database migrations: Flyway, scripts in `server/src/main/resources/db/migration/`". Task 12 fixes the line.
3. **The database is currently exposed to the internet.** `template.yaml:186-188` opens port 5432 to `AllowedSshCidr`, which defaults to `0.0.0.0/0`. This plan removes that exposure entirely by binding Postgres to `localhost`.
4. **`AllowedSshCidr` defaults to `0.0.0.0/0`** for SSH too. Task 8 surfaces this but does not change the default, since locking it down could lock the owner out.

## File Structure

| File | Change | Responsibility after change |
|---|---|---|
| `infrastructure/cloudformation/template.yaml` | Modify | Single-instance stack: VPC, arm64 t4g instance running app + Postgres, EIP, IAM, SSM params. No RDS. |
| `infrastructure/cloudformation/deploy.sh` | Modify | Build/upload/deploy; corrected defaults and usage text; new `backup-now` and `restore` helpers. |
| `server/src/main/resources/application-prod.properties` | Modify | Prod config pointing at localhost Postgres with a pool sized for a co-located database. |
| `CLAUDE.md` | Modify | Correct the migrations claim and document the new topology. |
| `docs/superpowers/plans/2026-08-19-rds-to-local-postgres-cost-reduction.md` | Created | This plan. |

No new Java source files. No application code changes beyond properties.

---

## Task 1: De-risk — confirm PostgreSQL 16 exists on AL2023 arm64

The whole plan depends on `postgresql16-server` being installable on Amazon Linux 2023 for arm64. RDS runs Postgres 16 (`template.yaml:300`), and a `pg_dump` from 16 will **not** restore into 15. Verify before changing anything.

**Files:** none (investigation only)

- [x] **Step 1: Launch a throwaway arm64 instance to test the package**

```bash
cd /Users/akozlovskyi/Documents/dbuff/dbuff
AMI=$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --region eu-north-1 --query 'Parameter.Value' --output text)
echo "AMI: $AMI"

SUBNET=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='PublicSubnet1'].PhysicalResourceId" \
  --output text)
echo "Subnet: $SUBNET"
```

Expected: a non-empty `ami-...` id and a `subnet-...` id.

- [x] **Step 2: Run the package check via SSM on a throwaway instance**

```bash
INSTANCE=$(aws ec2 run-instances \
  --image-id "$AMI" \
  --instance-type t4g.small \
  --subnet-id "$SUBNET" \
  --iam-instance-profile Name=dbuff-ec2-profile \
  --region eu-north-1 \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=dbuff-pg16-probe}]' \
  --query 'Instances[0].InstanceId' --output text)
echo "Probe instance: $INSTANCE"
aws ec2 wait instance-status-ok --instance-ids "$INSTANCE" --region eu-north-1
```

Expected: the wait returns after ~2 minutes with no output.

- [x] **Step 3: Query the package list**

```bash
CMD=$(aws ssm send-command \
  --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["dnf list available postgresql16-server postgresql16 2>&1 | tail -20","uname -m"]' \
  --region eu-north-1 --query 'Command.CommandId' --output text)
sleep 15
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text
```

Expected: lines listing `postgresql16-server.aarch64` and `postgresql16.aarch64`, and `aarch64` from `uname -m`.

**Decision point:** if `postgresql16-server` is **not** available, STOP and report. Do not fall back to `postgresql15-server` — a Postgres 16 dump cannot be restored into 15, which would silently break Task 10. The fallback in that case is to add the PGDG RHEL 9 aarch64 repository, which is a change to Task 5 that must be agreed first.

- [x] **Step 4: Terminate the probe instance**

```bash
aws ec2 terminate-instances --instance-ids "$INSTANCE" --region eu-north-1 \
  --query 'TerminatingInstances[0].CurrentState.Name' --output text
```

Expected: `shutting-down`.

- [x] **Step 5: Commit the plan itself**

```bash
git add docs/superpowers/plans/2026-08-19-rds-to-local-postgres-cost-reduction.md
git commit -m "docs: plan for RDS to local Postgres cost reduction"
```

---

## Task 2: Back up the existing database twice

Two independent copies before anything is destroyed: an RDS snapshot (fast rollback) and a logical dump in S3 (what Task 10 actually restores).

**Files:** none (operational)

- [x] **Step 0: Grant the running instance permission to write to S3**

The deployed IAM policy allows only `s3:GetObject`/`s3:ListBucket`. The
`s3:PutObject` grant on `db-backups/*` is part of Task 6, which does not take
effect until the Task 9 deploy — i.e. after the destructive step. Without this,
Step 4 below fails with `AccessDenied` and the migration would proceed with no
dump at all.

Add it as a separate inline policy so CloudFormation, which only reconciles the
`dbuff-ssm-s3-access` policy it declares, leaves it alone:

```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
aws iam put-role-policy \
  --role-name dbuff-ec2-role \
  --policy-name dbuff-migration-backup-write \
  --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\",
      \"Action\": \"s3:PutObject\",
      \"Resource\": \"arn:aws:s3:::dbuff-deploy-${ACCOUNT}/db-backups/*\"
    }]
  }"
```

Expected: no output. IAM changes take a few seconds to propagate. This grant
becomes redundant once Task 9 deploys the Task 6 policy; remove it afterwards
with `aws iam delete-role-policy --role-name dbuff-ec2-role --policy-name
dbuff-migration-backup-write`.

- [x] **Step 1: Take a manual RDS snapshot**

```bash
aws rds create-db-snapshot \
  --db-instance-identifier dbuff-postgres \
  --db-snapshot-identifier dbuff-pre-migration-20260819 \
  --region eu-north-1 \
  --query 'DBSnapshot.Status' --output text
```

Expected: `creating`.

- [x] **Step 2: Wait for the snapshot to finish**

```bash
aws rds wait db-snapshot-available \
  --db-snapshot-identifier dbuff-pre-migration-20260819 \
  --region eu-north-1 && echo "SNAPSHOT READY"
```

Expected: `SNAPSHOT READY` after a few minutes. This snapshot is the rollback path and is retained manually — it is not deleted when the instance is.

- [x] **Step 3: Dump the database to S3 from the current instance**

Run the dump on the EC2 instance rather than locally: it is in the same AZ as RDS, needs no Postgres client on the workstation, and lands the file in S3 where the new instance can read it. Get the instance id and account id first.

```bash
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
  --output text)
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo "Instance: $INSTANCE  Account: $ACCOUNT"
```

Expected: an `i-...` id and a 12-digit account id.

- [x] **Step 4: Run the dump**

`DB_PASSWORD` must be the same value used when the stack was deployed. It is read from the running systemd unit so it does not have to be retyped or pasted into shell history.

```bash
CMD=$(aws ssm send-command \
  --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript \
  --region eu-north-1 \
  --parameters "commands=[\
\"dnf install -y postgresql16 >/dev/null 2>&1\",\
\"DB_HOST=\$(grep -oP 'DB_HOST=\\\\K.*' /etc/systemd/system/dbuff.service)\",\
\"export PGPASSWORD=\$(grep -oP 'DB_PASSWORD=\\\\K.*' /etc/systemd/system/dbuff.service)\",\
\"pg_dump -Fc -h \$DB_HOST -U dbuffuser -d dbuff -f /tmp/dbuff-pre-migration.dump\",\
\"ls -lh /tmp/dbuff-pre-migration.dump\",\
\"aws s3 cp /tmp/dbuff-pre-migration.dump s3://dbuff-deploy-${ACCOUNT}/db-backups/dbuff-pre-migration.dump --region eu-north-1\"\
]" \
  --query 'Command.CommandId' --output text)
echo "Command: $CMD"
sleep 60
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query '[Status,StandardOutputContent,StandardErrorContent]' --output text
```

Expected: `Success`, a file listing showing a non-zero size for `/tmp/dbuff-pre-migration.dump`, and an S3 upload line. If `Status` is `Failed`, read `StandardErrorContent` and fix before continuing.

- [x] **Step 5: Verify the dump exists in S3 and record its size**

```bash
aws s3 ls "s3://dbuff-deploy-${ACCOUNT}/db-backups/" --region eu-north-1 --human-readable
```

Expected: `dbuff-pre-migration.dump` with a non-zero size. **Write that size down** — Task 10 compares against it.

- [x] **Step 6: Capture exact baseline row counts from RDS**

Dump size alone is a weak verification signal, and `n_live_tup` is a statistics
estimate that can be wrong by a wide margin. Capture exact per-table counts from
the source now, while RDS is still alive, so Task 10 can diff instead of guessing.
`query_to_xml` runs a real `count(*)` per table in a single statement.

```bash
cat > /tmp/baseline.json <<'JSON'
{
  "commands": [
    "set -euo pipefail",
    "DB_HOST=$(grep -oP 'DB_HOST=\\K.*' /etc/systemd/system/dbuff.service)",
    "export PGPASSWORD=$(grep -oP 'DB_PASSWORD=\\K.*' /etc/systemd/system/dbuff.service)",
    "psql -h \"$DB_HOST\" -U dbuffuser -d dbuff -At -F ',' -c \"SELECT table_name, (xpath('/row/cnt/text()', query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint AS rows FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;\""
  ]
}
JSON

CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --parameters file:///tmp/baseline.json \
  --region eu-north-1 --query 'Command.CommandId' --output text)
sleep 30
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text \
  | tee /tmp/dbuff-baseline-counts.txt
```

Expected: `table,count` lines, one per public table. **Keep
`/tmp/dbuff-baseline-counts.txt`.** Task 9 Step 0 recaptures this on a quiesced
database and overwrites the file — that quiesced copy is what Task 10 diffs
against. This one is a rehearsal of the query and a floor: no count should ever
come back *lower* than what it records here.

---

## Task 3: Switch the instance to Graviton

**Files:**
- Modify: `infrastructure/cloudformation/template.yaml:40-44` (InstanceType parameter)
- Modify: `infrastructure/cloudformation/template.yaml:336` (AMI)
- Modify: `infrastructure/cloudformation/template.yaml:341-346` (volume size)

- [x] **Step 1: Change the InstanceType parameter to the t4g family**

Replace lines 40-44:

```yaml
  InstanceType:
    Type: String
    Default: t3.small
    AllowedValues: [t3.micro, t3.small, t3.medium]
    Description: EC2 instance type
```

with:

```yaml
  InstanceType:
    Type: String
    Default: t4g.small
    AllowedValues: [t4g.small, t4g.medium]
    Description: >-
      EC2 instance type (Graviton/arm64 only - the AMI below is arm64).
      t4g.small (2 GiB) runs the app plus Postgres with ~600 MB headroom.
      Step up to t4g.medium (4 GiB) only if swap usage is sustained.
```

`t4g.micro` is deliberately excluded: 1 GiB cannot hold the JVM and Postgres together.

- [x] **Step 2: Change the AMI to arm64**

Replace line 336:

```yaml
      ImageId: !Sub '{{resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64}}'
```

with:

```yaml
      ImageId: !Sub '{{resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64}}'
```

- [x] **Step 3: Grow the root volume to 40 GB**

Replace lines 341-346:

```yaml
      BlockDeviceMappings:
        - DeviceName: /dev/xvda
          Ebs:
            VolumeSize: 20
            VolumeType: gp3
            Encrypted: true
```

with:

```yaml
      BlockDeviceMappings:
        - DeviceName: /dev/xvda
          Ebs:
            # 40 GB: OS + JAR + Postgres data + 2 GB swapfile + room to grow.
            # gp3 includes 3000 IOPS / 125 MB-s at any size, matching what the
            # 20 GB RDS volume provided, so there is no IO regression.
            VolumeSize: 40
            VolumeType: gp3
            Encrypted: true
```

- [x] **Step 4: Validate the template still parses**

```bash
aws cloudformation validate-template \
  --template-body file://infrastructure/cloudformation/template.yaml \
  --region eu-north-1 --query 'Parameters[*].ParameterKey' --output text
```

Expected: the parameter list printed with no error.

- [x] **Step 5: Commit**

```bash
git add infrastructure/cloudformation/template.yaml
git commit -m "infra: switch app instance to t4g.small arm64 with 40GB root volume"
```

---

## Task 4: Remove all RDS resources

**Files:**
- Modify: `infrastructure/cloudformation/template.yaml` — delete `PrivateSubnet1`, `PrivateSubnet2`, `DbSecurityGroup`, `DbSubnetGroup`, `Database`, the `RdsEndpoint` output; adjust `AppInstance.DependsOn`

- [x] **Step 1: Delete the unused private subnets (lines 99-118)**

Delete the whole `PrivateSubnet1` and `PrivateSubnet2` blocks including the `# --- Private Subnets (for RDS) ---` comment. They are dead weight: `DbSubnetGroup` referenced `PublicSubnet1`/`PublicSubnet2`, never these, and they have no route table association.

- [x] **Step 2: Delete the `DbSecurityGroup` block (lines 173-194)**

Delete it entirely. This is what exposed port 5432 to `0.0.0.0/0`.

- [x] **Step 3: Delete `DbSubnetGroup` and `Database` (lines 282-318)**

Delete from the `# ---------- RDS ----------` comment through the end of the `Database` resource.

- [x] **Step 4: Fix `AppInstance.DependsOn`**

It currently reads `DependsOn: Database` (line 332), which no longer resolves. The instance needs the internet gateway attached before UserData can reach S3 and SSM. Replace:

```yaml
  AppInstance:
    Type: AWS::EC2::Instance
    DependsOn: Database
```

with:

```yaml
  AppInstance:
    Type: AWS::EC2::Instance
    DependsOn: VPCGatewayAttachment
```

- [x] **Step 5: Delete the `RdsEndpoint` output (lines 473-475)**

```yaml
  RdsEndpoint:
    Description: RDS PostgreSQL endpoint
    Value: !GetAtt Database.Endpoint.Address
```

Delete those three lines.

- [x] **Step 6: Confirm no dangling references remain**

```bash
grep -n "Database\|DbSubnetGroup\|DbSecurityGroup\|PrivateSubnet" \
  infrastructure/cloudformation/template.yaml
```

Expected: **no output.** Any hit is a dangling reference that will fail the stack update. Note `DbPassword` is still a parameter and must NOT be removed — it becomes the local Postgres role password, so it will not appear in this grep.

- [x] **Step 7: Validate and commit**

```bash
aws cloudformation validate-template \
  --template-body file://infrastructure/cloudformation/template.yaml \
  --region eu-north-1 >/dev/null && echo "TEMPLATE OK"
git add infrastructure/cloudformation/template.yaml
git commit -m "infra: remove RDS instance, DB security group, and unused private subnets"
```

Expected: `TEMPLATE OK`.

---

## Task 5: Install and tune PostgreSQL 16 in UserData

**Files:**
- Modify: `infrastructure/cloudformation/template.yaml` — UserData, after the CloudWatch agent block and before the SSM secret fetch

Two escaping rules for this file, both already established by the existing UserData: `${...}` is substituted by CloudFormation's `Fn::Sub`, so shell variables must be written **without** braces (`$DOTA_API_KEY`, not `${DOTA_API_KEY}`); and quoted heredocs (`<<'EOF'`) prevent shell expansion, so prefer them plus a `sed` pass over escaping every `$`.

- [x] **Step 1: Add the swapfile block**

Insert immediately after the `amazon-cloudwatch-agent-ctl ... -s` line (currently line 384):

```bash
          # ---------- Swap ----------
          # 2 GiB safety net. RAM budget on t4g.small is ~1.4 GB of 2 GB, so
          # swap should stay near-empty; sustained usage means resize the instance.
          dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
          chmod 600 /swapfile
          mkswap /swapfile
          swapon /swapfile
          echo '/swapfile none swap sw 0 0' >> /etc/fstab
          echo 'vm.swappiness=10' > /etc/sysctl.d/99-dbuff-swap.conf
          sysctl -p /etc/sysctl.d/99-dbuff-swap.conf
```

- [x] **Step 2: Add the Postgres install and tuning block**

Insert directly after the swap block:

```bash
          # ---------- PostgreSQL 16 ----------
          dnf install -y postgresql16-server postgresql16
          /usr/bin/postgresql-setup --initdb

          # Tuning for 2 GiB shared with the JVM. Appended rather than edited in
          # place: in postgresql.conf the last occurrence of a setting wins.
          cat >> /var/lib/pgsql/data/postgresql.conf <<'PGCONF'

          # --- dbuff tuning ---
          listen_addresses = 'localhost'
          max_connections = 30
          shared_buffers = 256MB
          effective_cache_size = 768MB
          maintenance_work_mem = 64MB
          work_mem = 8MB
          wal_compression = on
          checkpoint_completion_target = 0.9
          # SSD-backed gp3: random reads cost about the same as sequential
          random_page_cost = 1.1
          effective_io_concurrency = 200
          # Trades up to ~200ms of committed transactions on an unclean shutdown
          # for better write throughput. Set to 'on' if that is unacceptable.
          synchronous_commit = off
          log_min_duration_statement = 2000
          PGCONF

          # The AL2023 default pg_hba uses 'ident' for TCP loopback, which fails
          # for the 'dbuff' OS user connecting as role 'dbuffuser'. The app uses
          # jdbc:postgresql://localhost, i.e. a TCP connection, so this must be
          # password auth or the application cannot start.
          sed -i -E 's|^(host[[:space:]]+all[[:space:]]+all[[:space:]]+127\.0\.0\.1/32[[:space:]]+).*|\1scram-sha-256|' /var/lib/pgsql/data/pg_hba.conf
          sed -i -E 's|^(host[[:space:]]+all[[:space:]]+all[[:space:]]+::1/128[[:space:]]+).*|\1scram-sha-256|' /var/lib/pgsql/data/pg_hba.conf

          systemctl enable --now postgresql
```

Note the heredoc body is indented to match the surrounding UserData block. Because `PGCONF` is quoted, the leading whitespace is preserved literally into `postgresql.conf` — Postgres tolerates leading whitespace on config lines, so this is safe.

- [x] **Step 3: Add role and database creation**

Insert directly after `systemctl enable --now postgresql`:

```bash
          # Create the application role and database, idempotently, so an
          # instance replacement on an existing volume does not fail the boot.
          sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQLEOF
          SELECT 'CREATE ROLE dbuffuser LOGIN PASSWORD ''${DbPassword}'''
            WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'dbuffuser')\gexec
          SELECT 'CREATE DATABASE dbuff OWNER dbuffuser'
            WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dbuff')\gexec
          SQLEOF
```

`${DbPassword}` is substituted by CloudFormation. The doubled single quotes produce a correctly quoted SQL literal. **Constraint to respect:** `DbPassword` must not contain a single quote, or this SQL breaks. Task 8 documents that constraint.

- [x] **Step 4: Point the app at localhost and fix the pool size**

In the systemd unit inside UserData, replace the `ExecStart` line (currently line 423):

```
          ExecStart=/usr/bin/java --enable-preview -Xms256m -Xmx512m -Dspring.datasource.hikari.maximum-pool-size=30 -jar /opt/dbuff/server.jar
```

with:

```
          ExecStart=/usr/bin/java --enable-preview -Xms256m -Xmx512m -jar /opt/dbuff/server.jar
```

The `-D` override is removed so that `application-prod.properties` is the single place the pool is configured (Task 7 sets it to 10). Then replace the `DB_HOST`/`DB_PORT` environment lines (currently 430-431):

```
          Environment=DB_HOST=${Database.Endpoint.Address}
          Environment=DB_PORT=${Database.Endpoint.Port}
```

with:

```
          Environment=DB_HOST=localhost
          Environment=DB_PORT=5432
```

- [x] **Step 5: Make the app wait for Postgres**

Quartz uses the JDBC job store (`spring.quartz.job-store-type=jdbc`), so the app hard-fails if the database is not up. Replace the `[Unit]` section of the `dbuff.service` heredoc:

```
          [Unit]
          Description=DBuff Application
          After=network.target
```

with:

```
          [Unit]
          Description=DBuff Application
          After=network.target postgresql.service
          Requires=postgresql.service
```

- [x] **Step 6: Validate and commit**

```bash
aws cloudformation validate-template \
  --template-body file://infrastructure/cloudformation/template.yaml \
  --region eu-north-1 >/dev/null && echo "TEMPLATE OK"
grep -c "Database.Endpoint" infrastructure/cloudformation/template.yaml
```

Expected: `TEMPLATE OK`, then `0`.

```bash
git add infrastructure/cloudformation/template.yaml
git commit -m "infra: run PostgreSQL 16 on the app instance with swap and systemd ordering"
```

---

## Task 6: Nightly backup to S3

Replaces RDS automated backups. Uses a systemd timer rather than cron, because `cronie` is not installed on AL2023 minimal.

**Files:**
- Modify: `infrastructure/cloudformation/template.yaml` — IAM policy (around line 267-273) and UserData

- [x] **Step 1: Grant the instance write access to the backup prefix**

The existing policy allows only `s3:GetObject`/`s3:ListBucket`. Replace the S3 statement:

```yaml
              - Effect: Allow
                Action:
                  - s3:GetObject
                  - s3:ListBucket
                Resource:
                  - !Sub 'arn:aws:s3:::dbuff-deploy-${AWS::AccountId}'
                  - !Sub 'arn:aws:s3:::dbuff-deploy-${AWS::AccountId}/*'
```

with:

```yaml
              - Effect: Allow
                Action:
                  - s3:GetObject
                  - s3:ListBucket
                Resource:
                  - !Sub 'arn:aws:s3:::dbuff-deploy-${AWS::AccountId}'
                  - !Sub 'arn:aws:s3:::dbuff-deploy-${AWS::AccountId}/*'
              # Nightly pg_dump upload; replaces RDS automated backups.
              - Effect: Allow
                Action:
                  - s3:PutObject
                Resource: !Sub 'arn:aws:s3:::dbuff-deploy-${AWS::AccountId}/db-backups/*'
```

- [x] **Step 2: Write the backup script in UserData**

Insert after the role/database creation block from Task 5 Step 3:

This block goes after the `mkdir -p /opt/dbuff /var/log/dbuff` from Task 5, so
the log directory already exists and no second `mkdir` is needed here.

```bash
          # ---------- Nightly backup ----------
          # Replaces RDS automated backups. Retention is an S3 lifecycle rule on
          # the db-backups/ prefix, not this script's problem.
          cat > /usr/local/bin/dbuff-backup.sh <<'BKEOF'
          #!/bin/bash
          set -euo pipefail
          BUCKET="dbuff-deploy-${AWS::AccountId}"
          REGION="${AWS::Region}"
          STAMP=$(date -u +%Y%m%dT%H%M%SZ)
          TMP=$(mktemp /tmp/dbuff-XXXXXX.dump)
          trap 'rm -f "$TMP"' EXIT
          # Runs as root, switches to the postgres OS user so peer auth applies
          # and no password needs to live on disk. -Fc already zlib-compresses,
          # so there is nothing to gain by piping through gzip. The redirect is
          # done by this root shell rather than pg_dump's -f, because mktemp made
          # the file root-owned 0600 and the postgres user could not open it.
          sudo -u postgres pg_dump -Fc -d dbuff > "$TMP"
          # Cheap integrity gate: a truncated dump fails to list. Upload only a
          # dump we know is readable, so S3 never holds a corrupt file that looks
          # like a valid backup.
          pg_restore --list "$TMP" > /dev/null
          aws s3 cp "$TMP" "s3://$BUCKET/db-backups/dbuff-$STAMP.dump" --region "$REGION"
          echo "backup complete: dbuff-$STAMP.dump ($(du -h "$TMP" | cut -f1))"
          BKEOF
          chmod 755 /usr/local/bin/dbuff-backup.sh
```

`mktemp` writes to `/tmp` on the 40 GB root volume; the `trap` removes the dump
even on failure.

Note the heredoc is quoted, so the shell does not expand `$STAMP`/`$TMP` when the
file is written — but `Fn::Sub` still substitutes its own references, which is how
the bucket and region get baked in without the `__PLACEHOLDER__` + `sed` dance.
The consequence is that **no brace-delimited shell variable may appear anywhere in
this block**: `Fn::Sub` would try to resolve it as a template reference and fail
the deploy. Bare `$NAME` is safe; `${NAME}` is not.

- [x] **Step 3: Add the systemd service and timer**

Insert directly after:

```bash
          cat > /etc/systemd/system/dbuff-backup.service <<'BKSVC'
          [Unit]
          Description=DBuff nightly database backup to S3
          After=postgresql.service
          Requires=postgresql.service

          [Service]
          Type=oneshot
          ExecStart=/usr/local/bin/dbuff-backup.sh
          StandardOutput=append:/var/log/dbuff/backup.log
          StandardError=append:/var/log/dbuff/backup.log
          BKSVC

          cat > /etc/systemd/system/dbuff-backup.timer <<'BKTMR'
          [Unit]
          Description=Run the DBuff database backup nightly

          [Timer]
          # 02:30 UTC, inside the app's configured quiet hours (01:00-09:00)
          OnCalendar=*-*-* 02:30:00
          Persistent=true

          [Install]
          WantedBy=timers.target
          BKTMR

          systemctl daemon-reload
          systemctl enable --now dbuff-backup.timer
          # Run the backup once at boot, to smoke-test pg_dump and the
          # s3:PutObject grant now rather than discovering a broken backup at
          # 02:30 the following morning. This explicit trigger is required:
          # Persistent=true only replays a run the timer *missed*, and on a
          # first-ever enable systemd stamps the timer as though it had just
          # run, so nothing fires until the next OnCalendar match. Learned the
          # hard way - after the first boot of this instance, backup.log did not
          # exist. Tolerates failure because a fresh instance has an empty
          # database and a backup must never block the boot.
          systemctl start dbuff-backup.service || true
```

`enable --now` starts the *timer*, not the service — a distinction that cost us
the boot-time smoke test on the first deploy. `Persistent=true` still earns its
place: it covers a run missed because the instance was stopped over 02:30.

- [x] **Step 4: Validate and commit**

```bash
aws cloudformation validate-template \
  --template-body file://infrastructure/cloudformation/template.yaml \
  --region eu-north-1 >/dev/null && echo "TEMPLATE OK"
git add infrastructure/cloudformation/template.yaml
git commit -m "infra: nightly pg_dump to S3 via systemd timer, replacing RDS backups"
```

---

## Task 7: Size the connection pool for a co-located database

**Files:**
- Modify: `server/src/main/resources/application-prod.properties:7-12`

A pool of 30 against a local Postgres means up to 30 backend processes, each with its own `work_mem` allocation, competing with the JVM for 2 GiB. Ten is ample: `app.concurrency.max-parallel-matches=5` and `spring.quartz.properties.org.quartz.threadPool.threadCount=5` bound real concurrency, and Hikari queues rather than failing when saturated.

- [x] **Step 1: Replace the HikariCP block**

Replace lines 7-12:

```properties
# HikariCP - sized for virtual threads on a single instance
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=60000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=600000
```

with:

```properties
# HikariCP - Postgres now runs on this same 2 GiB instance, so every pooled
# connection is a local backend process competing with the JVM for memory.
# Real concurrency is bounded by app.concurrency.max-parallel-matches=5 and the
# Quartz thread pool of 5; Hikari queues beyond that rather than failing.
# Keep this <= postgresql.conf max_connections (30) with headroom for psql.
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=60000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=600000
```

- [x] **Step 2: Update the stale comment about the datasource URL**

Replace line 1:

```properties
# Production profile - deployed on AWS EC2 with RDS PostgreSQL
```

with:

```properties
# Production profile - deployed on AWS EC2 with PostgreSQL 16 on the same host.
# DB_HOST/DB_PORT are injected by the systemd unit as localhost:5432.
```

- [x] **Step 3: Confirm no other pool override survives**

```bash
grep -rn "maximum-pool-size" server/src/main/resources/ infrastructure/
```

Expected: exactly two hits — `application.properties` (50, the local-dev default, unchanged) and `application-prod.properties` (10). **No hit in `template.yaml`.** A hit in the template means the Task 5 Step 4 edit was missed and the `-D` flag will override this file.

- [x] **Step 4: Verify the build still passes**

```bash
cd /Users/akozlovskyi/Documents/dbuff/dbuff && ./gradlew :server:bootJar -x spotlessCheck
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add server/src/main/resources/application-prod.properties
git commit -m "config: size Hikari pool for co-located Postgres"
```

---

## Task 8: Update deploy.sh

The usage text is wrong in three places and there are no helpers for the new backup/restore operations.

**Files:**
- Modify: `infrastructure/cloudformation/deploy.sh:38-52` (usage), `:82` (default instance type), and add two commands

- [x] **Step 1: Fix the usage text**

Replace lines 38-52:

```bash
Required environment variables for deploy:
  KEY_PAIR_NAME       EC2 key pair name
  DB_PASSWORD         RDS password (min 8 chars)
  DOTA_API_KEY        OpenDota API key
  SCRAPPER_API_KEY    ScraperAPI key
  OPENAI_API_KEY      OpenAI API key
  DISCORD_BOT_TOKEN   Discord bot token

Optional:
  GOOGLE_VISION_CREDENTIALS_PATH  Path to Google Vision service-account JSON (enables OCR)
  STACK_NAME          CloudFormation stack name (default: dbuff)
  AWS_REGION          AWS region (default: us-east-1)
  INSTANCE_TYPE       EC2 instance type (default: t3.small)
  ALLOWED_SSH_CIDR    SSH CIDR (default: 0.0.0.0/0)
EOF
```

with:

```bash
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
```

Two corrections here: the region default was documented as `us-east-1` but line 21 actually defaults to `eu-north-1`, and `DB_PASSWORD` is no longer an RDS password.

- [x] **Step 2: Change the default instance type**

Replace line 82:

```bash
  INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
```

with:

```bash
  INSTANCE_TYPE="${INSTANCE_TYPE:-t4g.small}"
```

- [x] **Step 3: Add `backup-now` and `restore` commands**

Insert before the `case "${1:-}" in` block (currently line 137):

```bash
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
```

`pg_restore` is followed by `|| true` deliberately: it exits non-zero on benign warnings such as a missing role or an already-present extension, which would otherwise abort the SSM command before the app restarts. Task 10 verifies the restore by row count rather than by exit code.

- [x] **Step 4: Wire the new commands into the dispatcher**

Replace the `case` block:

```bash
case "${1:-}" in
  build)  cmd_build ;;
  upload) cmd_upload ;;
  deploy) cmd_deploy ;;
  all)    cmd_build && cmd_upload && cmd_deploy ;;
  *)      usage ;;
esac
```

with:

```bash
case "${1:-}" in
  build)      cmd_build ;;
  upload)     cmd_upload ;;
  deploy)     cmd_deploy ;;
  all)        cmd_build && cmd_upload && cmd_deploy ;;
  backup-now) cmd_backup_now ;;
  restore)    cmd_restore "$@" ;;
  *)          usage ;;
esac
```

- [x] **Step 5: Add the new commands to the usage summary**

Replace lines 32-36:

```bash
Commands:
  build     Build the server JAR
  upload    Upload JAR to S3 (creates bucket if needed via stack)
  deploy    Create or update the CloudFormation stack
  all       build + upload + deploy
```

with:

```bash
Commands:
  build       Build the server JAR
  upload      Upload JAR to S3 (creates bucket if needed via stack)
  deploy      Create or update the CloudFormation stack
  all         build + upload + deploy
  backup-now  Trigger an immediate database backup to S3
  restore     Restore a dump from s3://<bucket>/db-backups/<key> (stops the app)
```

- [x] **Step 6: Verify the script parses and commit**

```bash
bash -n infrastructure/cloudformation/deploy.sh && echo "SYNTAX OK"
git add infrastructure/cloudformation/deploy.sh
git commit -m "infra: default to t4g.small, add backup-now and restore commands"
```

Expected: `SYNTAX OK`.

---

## Task 9: Deploy

This replaces the instance (AMI, instance type, and volume size all changed) and deletes the RDS instance. Task 2's snapshot and dump are the safety net.

**Files:** none (operational)

- [x] **Step 0: Stop the app and take the FINAL dump**

Task 2's dump is a safety net and a rehearsal, not the dump that gets restored.
The old app keeps writing to RDS after that dump is taken, and every one of those
writes is lost at restore. Close the window: stop the app so writes cease, then
dump. Downtime starts here and lasts until Task 10 finishes — roughly 15 minutes.

```bash
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
  --output text)
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

cat > /tmp/final-dump.json <<'JSON'
{
  "commands": [
    "set -euo pipefail",
    "systemctl stop dbuff",
    "sleep 5",
    "DB_HOST=$(grep -oP 'DB_HOST=\\K.*' /etc/systemd/system/dbuff.service)",
    "export PGPASSWORD=$(grep -oP 'DB_PASSWORD=\\K.*' /etc/systemd/system/dbuff.service)",
    "pg_dump -Fc -h \"$DB_HOST\" -U dbuffuser -d dbuff -f /tmp/dbuff-final.dump",
    "pg_restore --list /tmp/dbuff-final.dump > /dev/null && echo DUMP_READABLE",
    "ls -lh /tmp/dbuff-final.dump",
    "aws s3 cp /tmp/dbuff-final.dump s3://BUCKET/db-backups/dbuff-final.dump --region eu-north-1",
    "echo ---COUNTS---",
    "psql -h \"$DB_HOST\" -U dbuffuser -d dbuff -At -F ',' -c \"SELECT table_name, (xpath('/row/cnt/text()', query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint AS rows FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;\""
  ]
}
JSON
sed -i '' "s|BUCKET|dbuff-deploy-${ACCOUNT}|" /tmp/final-dump.json

CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --parameters file:///tmp/final-dump.json \
  --region eu-north-1 --query 'Command.CommandId' --output text)
sleep 90
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text \
  | tee /tmp/dbuff-final-out.txt
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query '[Status,StandardErrorContent]' --output text
```

Expected: `DUMP_READABLE`, a non-zero file size, an upload line, then
`---COUNTS---` followed by `table,count` lines, and finally `Success`.
Task 10 restores `dbuff-final.dump`, not `dbuff-pre-migration.dump`.

The counts are captured in the same SSM call as the dump, *after* the app is
stopped, so they describe exactly the state the dump contains — unlike Task 2's
baseline, which the app has been writing past ever since. Extract them as the
reference Task 10 diffs against:

```bash
sed -n '/^---COUNTS---$/,$p' /tmp/dbuff-final-out.txt | grep -v '^---COUNTS---$' \
  | grep -v '^$' > /tmp/dbuff-baseline-counts.txt
wc -l /tmp/dbuff-baseline-counts.txt
```

Expected: the same table count as Task 2 Step 6 produced, with per-table numbers
equal or higher. This overwrites Task 2's baseline file deliberately — the
quiesced one is the authoritative reference. If the two differ by a *lot* more
than a few hours of normal traffic, or any count went *down*, stop and work out
why before destroying RDS.

- [x] **Step 1: Build and upload the JAR**

The bytecode is architecture-independent, so no rebuild is strictly required — but rebuild anyway so the deployed artifact matches the committed config from Task 7.

```bash
cd /Users/akozlovskyi/Documents/dbuff/dbuff
./infrastructure/cloudformation/deploy.sh build
./infrastructure/cloudformation/deploy.sh upload
```

Expected: `BUILD SUCCESSFUL`, then `Upload complete`.

- [x] **Step 2: Deploy the stack**

`DB_PASSWORD` and the API keys must be set in `.env` or the environment, exactly as for the original deploy.

```bash
./infrastructure/cloudformation/deploy.sh deploy
```

Expected: `Stack outputs:` with a table containing `PublicIP`, `AppUrl`, `S3Bucket`, `HealthCheckUrl` — and **no** `RdsEndpoint`. This takes 10-20 minutes, most of it waiting for the RDS deletion snapshot.

If the update fails and rolls back, capture why before retrying:

```bash
aws cloudformation describe-stack-events --stack-name dbuff --region eu-north-1 \
  --query 'StackEvents[?ResourceStatus==`CREATE_FAILED` || ResourceStatus==`UPDATE_FAILED`].[LogicalResourceId,ResourceStatusReason]' \
  --output table
```

- [x] **Step 3: Confirm the Elastic IP is unchanged**

```bash
aws cloudformation describe-stacks --stack-name dbuff --region eu-north-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`PublicIP`].OutputValue' --output text
```

Expected: the same IP as before the migration. The EIP is a separate resource from the instance, so instance replacement preserves it and no DNS or Discord reconfiguration is needed.

- [x] **Step 4: Watch UserData run to completion**

```bash
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
  --output text)
CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --region eu-north-1 \
  --parameters 'commands=["tail -40 /var/log/user-data.log","systemctl is-active postgresql","systemctl is-active dbuff"]' \
  --query 'Command.CommandId' --output text)
sleep 20
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text
```

Expected: the tail ends without a `set -e` abort, then `active` twice. If `postgresql` is not active, the Task 1 package assumption failed. If `dbuff` is not active but Postgres is, check `journalctl -u dbuff -n 50` — the usual cause is a `pg_hba` auth failure, meaning the Task 5 Step 2 `sed` did not match.

---

## Task 10: Restore the data

The app has already started against an empty database, so `ddl-auto=update` has created an empty schema. Drop it and restore the dump, which carries both schema and data.

**Files:** none (operational)

- [x] **Step 1: Restore from the pre-migration dump**

```bash
cd /Users/akozlovskyi/Documents/dbuff/dbuff
./infrastructure/cloudformation/deploy.sh restore dbuff-final.dump
```

Expected: `Success` and no fatal errors in the output. Benign `pg_restore` warnings about roles or ownership are expected and are why the command tolerates a non-zero exit.

- [x] **Step 2: Diff exact row counts against the Task 9 Step 0 baseline**

Not `n_live_tup` — that is a statistics estimate that reads zero until autovacuum
runs and would need a `vacuumdb --analyze-only` before it meant anything. Run the
same exact-count query used for the baseline, so the two outputs are directly
comparable line for line.

```bash
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
  --output text)

cat > /tmp/verify.json <<'JSON'
{
  "commands": [
    "set -euo pipefail",
    "sudo -u postgres psql -d dbuff -At -c \"SELECT pg_size_pretty(pg_database_size(current_database()));\"",
    "echo ---",
    "sudo -u postgres psql -d dbuff -At -F ',' -c \"SELECT table_name, (xpath('/row/cnt/text()', query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint AS rows FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;\""
  ]
}
JSON

CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --parameters file:///tmp/verify.json \
  --region eu-north-1 --query 'Command.CommandId' --output text)
sleep 30
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text \
  | tee /tmp/dbuff-restored-counts.txt
```

Now diff. `sed` strips the size line and the `---` marker so only the
`table,count` lines are compared:

```bash
sed -n '/^---$/,$p' /tmp/dbuff-restored-counts.txt | grep -v '^---$' \
  > /tmp/restored-only.txt
diff /tmp/dbuff-baseline-counts.txt /tmp/restored-only.txt && echo "ROW COUNTS MATCH"
```

Expected: `ROW COUNTS MATCH` with no diff output, and a database size in the same
ballpark as the 14.8 MiB dump (expect it larger — indexes and page overhead are
not compressed).

Two kinds of diff, treated differently:

- **A count is lower, or a table is missing entirely.** The restore lost data.
  STOP. Do not proceed to Task 11 and do not delete the RDS snapshot; roll back
  per the rollback section below.
- **A count is higher, or a new table appears.** Almost certainly benign: the app
  started before this check and wrote new rows, or Hibernate `ddl-auto=update`
  created a table for one of the uncommitted entities. Confirm the *direction* of
  every difference is upward before accepting it.

If the diff is noisy because the app is live, stop it and re-check on a quiet
database:

```bash
aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --region eu-north-1 \
  --parameters 'commands=["systemctl stop dbuff"]' >/dev/null
# re-run the verify block above, then:
aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --region eu-north-1 \
  --parameters 'commands=["systemctl start dbuff"]' >/dev/null
```

- [x] **Step 3: Confirm the application is healthy against the restored data**

```bash
IP=$(aws cloudformation describe-stacks --stack-name dbuff --region eu-north-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`PublicIP`].OutputValue' --output text)
curl -fsS "http://${IP}:8080/actuator/health"
```

Expected: `{"status":"UP"}`. A non-200 means the app cannot reach the database; check `journalctl -u dbuff -n 50`.

---

## Task 11: Verify memory headroom and the backup path

**Files:** none (operational)

- [x] **Step 1: Check the memory budget against the prediction**

```bash
INSTANCE=$(aws cloudformation describe-stack-resources --stack-name dbuff \
  --region eu-north-1 \
  --query "StackResources[?LogicalResourceId=='AppInstance'].PhysicalResourceId" \
  --output text)
CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --region eu-north-1 \
  --parameters 'commands=["free -m","swapon --show","ps -eo rss,comm --sort=-rss | head -8"]' \
  --query 'Command.CommandId' --output text)
sleep 20
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text
```

Expected: `used` around 1.3-1.5 GB of ~1.9 GB total, and swap `used` at or near 0.

**Escalation criterion, to be checked again after a week of real traffic:** if swap used exceeds ~200 MB on a sustained basis, or the OOM killer appears in `journalctl -k`, redeploy with `INSTANCE_TYPE=t4g.medium`. That is a parameter change plus a stop/start, about two minutes of downtime, and costs an extra ~$11.70/mo.

- [x] **Step 2: Prove the backup works end to end**

Do not wait for 02:30 UTC to find out whether the timer works.

```bash
cd /Users/akozlovskyi/Documents/dbuff/dbuff
./infrastructure/cloudformation/deploy.sh backup-now
```

Expected: `Success` and a `backup complete: dbuff-<stamp>.dump` line (`-Fc` already zlib-compresses, so there is no `.gz`).

- [x] **Step 3: Confirm the backup landed in S3 with a plausible size**

```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
aws s3 ls "s3://dbuff-deploy-${ACCOUNT}/db-backups/" --region eu-north-1 --human-readable
```

Expected: the new `dbuff-<stamp>.dump` alongside `dbuff-pre-migration.dump`. It should be about the same size as the pre-migration dump - matching sizes are corroboration that the restore kept everything - and certainly not near-zero — a few kilobytes would mean it dumped an empty database.

- [x] **Step 4: Confirm the timer is scheduled**

```bash
CMD=$(aws ssm send-command --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript --region eu-north-1 \
  --parameters 'commands=["systemctl list-timers dbuff-backup.timer --no-pager"]' \
  --query 'Command.CommandId' --output text)
sleep 15
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE" \
  --region eu-north-1 --query 'StandardOutputContent' --output text
```

Expected: a row for `dbuff-backup.timer` with a `NEXT` timestamp at the coming 02:30 UTC.

- [x] **Step 5: Confirm the database is no longer reachable from the internet**

```bash
IP=$(aws cloudformation describe-stacks --stack-name dbuff --region eu-north-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`PublicIP`].OutputValue' --output text)
nc -z -w 5 "$IP" 5432 && echo "REACHABLE - BAD" || echo "not reachable - good"
```

Expected: `not reachable - good`. Postgres binds to `localhost` and no security group rule permits 5432.

---

## Task 12: Retention, documentation, and cost confirmation

**Files:**
- Modify: `CLAUDE.md`

- [x] **Step 1: Add a lifecycle rule so backups do not accumulate forever**

The deploy bucket is created by `deploy.sh` with `aws s3 mb`, not by CloudFormation, so the rule is applied with the CLI.

```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
cat > /tmp/lifecycle.json <<'JSON'
{
  "Rules": [
    {
      "ID": "expire-db-backups-after-30-days",
      "Status": "Enabled",
      "Filter": {"Prefix": "db-backups/"},
      "Expiration": {"Days": 30}
    }
  ]
}
JSON
aws s3api put-bucket-lifecycle-configuration \
  --bucket "dbuff-deploy-${ACCOUNT}" \
  --lifecycle-configuration file:///tmp/lifecycle.json \
  --region eu-north-1
aws s3api get-bucket-lifecycle-configuration --bucket "dbuff-deploy-${ACCOUNT}" \
  --region eu-north-1
```

Expected: the rule echoed back. Note this expires `dbuff-pre-migration.dump` after 30 days too — acceptable, since the RDS snapshot is the long-term rollback artifact.

- [x] **Step 2: Correct the Flyway claim in CLAUDE.md**

Replace:

```markdown
- **Database migrations**: Flyway, scripts in `server/src/main/resources/db/migration/`.
```

with:

```markdown
- **Database schema**: managed by Hibernate `ddl-auto=update`. Flyway is **not**
  a dependency, so the `V2`-`V5` scripts in `server/src/main/resources/db/migration/`
  have never run — including the indexes in `V2__item_ranking_indexes.sql`. Treat
  a `pg_dump` as the only source of truth for the live schema.
```

- [x] **Step 3: Document the new topology in CLAUDE.md**

Replace:

```markdown
- PostgreSQL 16 runs via `docker-compose.yml` on `localhost:5432`
```

with:

```markdown
- PostgreSQL 16 runs via `docker-compose.yml` on `localhost:5432` for local development
- In production PostgreSQL 16 runs **on the application EC2 instance** (arm64
  `t4g.small`), reached over `localhost:5432` and bound to loopback only. There is
  no RDS instance. Backups are a nightly `pg_dump` to
  `s3://dbuff-deploy-<account>/db-backups/` via the `dbuff-backup.timer` systemd
  unit, retained 30 days. Restore with
  `infrastructure/cloudformation/deploy.sh restore <key>`.
```

- [x] **Step 4: Commit the documentation**

```bash
git add CLAUDE.md
git commit -m "docs: correct Flyway claim and document single-instance Postgres topology"
```

- [x] **Step 5: Confirm no RDS resources or second IPv4 remain billable**

```bash
aws rds describe-db-instances --region eu-north-1 \
  --query 'DBInstances[*].DBInstanceIdentifier' --output text
aws ec2 describe-addresses --region eu-north-1 \
  --query 'Addresses[*].[PublicIp,InstanceId]' --output table
```

Expected: empty output from the first command, and exactly **one** address from the second, associated with the app instance. A second address, or one with no `InstanceId`, is $3.65/mo of waste — release it.

- [x] **Step 6: Verify the snapshot survived the RDS deletion**

```bash
aws rds describe-db-snapshots --region eu-north-1 \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,Status,SnapshotType]' --output table
```

Expected: `dbuff-pre-migration-20260819` with status `available`, plus the automatic final snapshot CloudFormation took on deletion (`DeletionPolicy: Snapshot`). Snapshot storage is billed at ~$0.0552/GB-mo in eu-north-1 — roughly $0.30/mo for a 5 GB snapshot. Keep them until Task 13, then delete to reclaim that.

- [ ] **Step 7: Check the bill actually moved**

Wait 48 hours after deploying, then:

```bash
aws ce get-cost-and-usage \
  --time-period Start=$(date -u -v-2d +%Y-%m-%d),End=$(date -u +%Y-%m-%d) \
  --granularity DAILY --metrics UnblendedCost \
  --group-by Type=DIMENSION,Key=SERVICE \
  --region us-east-1 \
  --query 'ResultsByTime[-1].Groups[*].[Keys[0],Metrics.UnblendedCost.Amount]' \
  --output table
```

Expected: no `Relational Database Service` line, and the `Virtual Private Cloud` daily figure roughly halved (~$0.12/day rather than ~$0.24/day). Cost Explorer's API endpoint is always `us-east-1` regardless of where the resources live; the `date -u -v-2d` syntax is BSD/macOS.

---

## Task 13: Buy a Savings Plan (deferred — do this after a week)

Deliberately last. Committing before the instance size is proven risks paying for twelve months of the wrong instance family.

**Files:** none (operational)

- [ ] **Step 1: Re-check the escalation criterion from Task 11 Step 1**

Re-run that step. Proceed only if swap is still near zero and there are no OOM events after a week of real traffic. If the instance had to move to `t4g.medium`, that is fine — just size the commitment to the instance you actually settled on.

- [ ] **Step 2: Get a rate recommendation**

```bash
aws ce get-savings-plans-purchase-recommendation \
  --savings-plans-type COMPUTE_SP \
  --term-in-years ONE_YEAR \
  --payment-option NO_UPFRONT \
  --lookback-period-in-days SEVEN_DAYS \
  --region us-east-1 \
  --query 'SavingsPlansPurchaseRecommendation.SavingsPlansPurchaseRecommendationDetails[*].[HourlyCommitmentToPurchase,EstimatedMonthlySavingsAmount,EstimatedSavingsPercentage]' \
  --output table
```

Expected: an hourly commitment around $0.012-0.013 with an estimated saving near 25-30%.

- [ ] **Step 3: Purchase via the console**

Do this in the console rather than the CLI — it shows the total 12-month obligation before committing, which the CLI does not. Console → Billing → Savings Plans → Purchase. Choose **Compute Savings Plan**, 1 year, no upfront, at the recommended hourly commitment.

Compute (not EC2 Instance) Savings Plans are the right choice here despite the slightly smaller discount: they are not locked to an instance family, so a later move to `t4g.medium` stays covered.

- [ ] **Step 4: Delete the migration snapshots once you are confident**

```bash
aws rds delete-db-snapshot --db-snapshot-identifier dbuff-pre-migration-20260819 \
  --region eu-north-1 --query 'DBSnapshot.Status' --output text
```

Only after a successful restore has been verified and a few nightly S3 backups have accumulated. Also delete the automatic final snapshot listed in Task 12 Step 6.

---

## Rollback

If the migration fails at any point before Task 12, the path back is:

1. **Revert the template and redeploy.** `git revert` the commits from Tasks 3-8, then `./infrastructure/cloudformation/deploy.sh deploy`. This recreates an x86 `t3.small` and an empty RDS instance.
2. **Restore RDS from the snapshot.** The recreated RDS instance will be empty, so restore `dbuff-pre-migration-20260819` into a new instance and point `DB_HOST` at it — or restore the snapshot over `dbuff-postgres` before redeploying.
3. **Or stay on the new instance and re-restore.** If only the data restore failed, `deploy.sh restore dbuff-pre-migration.dump` is idempotent — it drops and recreates the database each time, so it can be retried freely.

The Elastic IP is preserved across all of these, so nothing external needs reconfiguring.

## Out of Scope

Recorded so they are not silently absorbed:

- Adding Flyway and reconciling the never-executed `V2`-`V5` migrations, including the missing indexes from `V2__item_ranking_indexes.sql`.
- Removing the unused `ScrapperServicePlaywright` and the `com.microsoft.playwright` dependency (~100 MB of the JAR's dependency tree, no runtime cost).
- Tightening `AllowedSshCidr` from `0.0.0.0/0`.
- Reducing `spring.servlet.multipart.max-file-size=20MB`, which is the real OOM risk on a 512 MB heap: a `BufferedImage` costs ~4 bytes per pixel regardless of compressed size, so a 20 MB high-resolution upload could demand ~96 MB in a single allocation. Typical 1920×1080 scoreboard screenshots need only ~8 MB, so this is a latent risk rather than a live one.
- Migrating `GenerationType.IDENTITY` entities to `SEQUENCE` for Hibernate batching, as noted in `application-prod.properties`.
