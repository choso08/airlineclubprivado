#!/usr/bin/env bash
# Saves a compressed copy of the whole game to backups/.
#
#   ./scripts/backup-db.sh
#
# With several people playing, the database IS the game: every airline,
# route, plane and euro earned lives only in there. Losing it means everyone
# starts over, so take backups before you change anything risky.
#
# To run it nightly at 04:00, add this to `crontab -e`:
#   0 4 * * * /opt/airline/scripts/backup-db.sh >> /var/log/airline-backup.log 2>&1
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

BACKUP_DIR="${AIRLINE_BACKUP_DIR:-$REPO_ROOT/backups}"
KEEP="${AIRLINE_BACKUP_KEEP:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/airline-${STAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

HOST="${AIRLINE_DB_HOST:-localhost:3306}"
DB_HOST="${HOST%%:*}"
DB_PORT="${HOST##*:}"
[[ "$DB_PORT" == "$DB_HOST" ]] && DB_PORT=3306

DUMP_CMD="$(command -v mariadb-dump || command -v mysqldump)"
if [[ -z "$DUMP_CMD" ]]; then
  # No client on the host - go through the database container instead.
  if docker compose ps mysql >/dev/null 2>&1; then
    DUMP_CMD="docker compose exec -T mysql mysqldump"
  else
    echo "ERROR: no mysqldump/mariadb-dump found and no mysql container running." >&2
    exit 1
  fi
fi

echo ">> Backing up ${AIRLINE_DB_SCHEMA:-airline_v2_1} to $OUT"

# Delete the half-written file if anything below fails, so a failed run can
# never leave something that looks like a usable backup.
trap 'rm -f "$OUT"' ERR

set +e
$DUMP_CMD \
  --host="$DB_HOST" --port="$DB_PORT" \
  --user="${AIRLINE_DB_USER:-sa}" --password="${AIRLINE_DB_PASSWORD:-admin}" \
  --single-transaction --quick --default-character-set=utf8mb4 \
  "${AIRLINE_DB_SCHEMA:-airline_v2_1}" | gzip > "$OUT"
PIPE_STATUS=("${PIPESTATUS[@]}")
set -e

if [[ "${PIPE_STATUS[0]}" -ne 0 || "${PIPE_STATUS[1]}" -ne 0 ]]; then
  echo "ERROR: the dump failed (is the database running?) - discarding $OUT" >&2
  rm -f "$OUT"
  exit 1
fi

# An empty gzip stream is still ~20 bytes, so a plain -s test is not enough
# to tell a real backup from a failed one.
SIZE=$(stat -c%s "$OUT" 2>/dev/null || stat -f%z "$OUT")
if [[ "$SIZE" -lt 10240 ]]; then
  echo "ERROR: backup is only ${SIZE} bytes - that cannot be a full game. Discarding." >&2
  rm -f "$OUT"
  exit 1
fi

trap - ERR

echo ">> Done: $(du -h "$OUT" | cut -f1)"

# Drop the oldest ones so backups cannot fill the disk.
ls -1t "$BACKUP_DIR"/airline-*.sql.gz 2>/dev/null | tail -n "+$((KEEP + 1))" | while read -r old; do
  echo ">> Removing old backup $(basename "$old")"
  rm -f "$old"
done

echo ">> To restore:"
echo "   gunzip < $OUT | mariadb -u ${AIRLINE_DB_USER:-sa} -p ${AIRLINE_DB_SCHEMA:-airline_v2_1}"
