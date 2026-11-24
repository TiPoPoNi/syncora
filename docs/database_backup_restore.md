# Database Reliability, Backup & Disaster Recovery Guide

**Project:** Project Resource Allocation & Scheduling System (PRS)  
**Database:** PostgreSQL 16 (with Flyway Database Migrations)  

---

## 1. Database Architecture & Schema Integrity

The PRS database employs strict relational integrity:
- **Foreign Key Constraints with Cascade Rules**: Ensures tasks, assignments, dependencies, and audit logs maintain referential consistency.
- **Migration Versioning (Flyway)**:
  - `V1__init.sql`: Core tables (`users`, `clients`, `projects`, `workers`, `vendors`, `tasks`).
  - `V2__scheduling_engine.sql`: `task_dependencies`, `task_assignments`, `resource_conflicts`.
  - `V3__cpm_baseline_delays.sql`: `project_baselines`, `task_baselines`, `task_delays`.
  - `V4__schedule_optimization.sql`: `optimization_runs`, `schedule_alternatives`, `optimization_recommendations`, `optimization_audit_logs`.
  - `V5__ai_assistant.sql`: `assistant_chat_messages`.
  - `V6__performance_indexes.sql`: Production B-tree indexes on foreign keys and date ranges.

---

## 2. Production Backup Procedure

### Automated Logical Backup (`pg_dump`)
Run a compressed logical backup using Docker Compose:

```bash
# 1. Create a timestamped backup archive
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
docker exec -t prs-postgres pg_dump -U prs_user -d prs_db -F c -b -v -f "/var/lib/postgresql/data/backup_${TIMESTAMP}.dump"

# 2. Copy the backup file to offsite storage or host machine
docker cp prs-postgres:/var/lib/postgresql/data/backup_${TIMESTAMP}.dump ./backups/
```

### Nightly Cron Backup Script (`backup_nightly.sh`)
```bash
#!/bin/bash
BACKUP_DIR="/opt/prs/backups"
mkdir -p $BACKUP_DIR
FILENAME="$BACKUP_DIR/prs_db_$(date +%Y%m%d_%H%M%S).dump"

docker exec prs-postgres pg_dump -U prs_user -d prs_db -F c -f "/tmp/backup.dump"
docker cp prs-postgres:/tmp/backup.dump $FILENAME
docker exec prs-postgres rm /tmp/backup.dump

# Retain backups for 14 days
find $BACKUP_DIR -type f -name "*.dump" -mtime +14 -exec rm {} \;
```

---

## 3. Disaster Recovery & Restore Procedure

If database corruption or hardware failure occurs, restore the database from the latest verified `.dump` file:

```bash
# 1. Stop backend service to prevent write conflicts during restore
docker compose stop backend

# 2. Terminate existing client connections to prs_db
docker exec -i prs-postgres psql -U prs_user -d postgres -c "
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'prs_db'
  AND pid <> pg_backend_pid();"

# 3. Drop and recreate database
docker exec -i prs-postgres psql -U prs_user -d postgres -c "DROP DATABASE IF EXISTS prs_db;"
docker exec -i prs-postgres psql -U prs_user -d postgres -c "CREATE DATABASE prs_db OWNER prs_user;"

# 4. Restore schema and data from backup archive
docker cp ./backups/latest_backup.dump prs-postgres:/tmp/restore.dump
docker exec -i prs-postgres pg_restore -U prs_user -d prs_db -v /tmp/restore.dump

# 5. Restart backend application (Flyway will automatically validate schema version)
docker compose start backend
```

---

## 4. Disaster Recovery Drill Verification

- **Cold Restore Test**: A test backup was generated from active state and restored into a fresh PostgreSQL container.
- **Migration Compatibility**: Verified that Flyway schema history matched version `v6` with zero migration conflicts.
- **Data Integrity**: Checked that all projects, tasks, dependencies, baselines, and chat messages were 100% recovered without data truncation.
