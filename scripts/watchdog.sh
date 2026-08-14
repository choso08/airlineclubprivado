#!/usr/bin/env bash
# Notices when the game has stopped moving, and gets it moving again.
#
# The night the database driver changed, the simulation threw in the middle of
# every cycle. The web site stayed up, the clock kept counting down, and the
# only sign was that flights on the map jumped backwards - which took most of a
# day to be reported and understood. Nothing was watching.
#
# This is what watches. It runs on a timer and checks the one thing that
# matters: has the game's week number moved? If it has not moved in a while
# and the simulation is supposed to be running, the simulation is stuck, and a
# restart is almost always the answer.
#
#   ./scripts/watchdog.sh          check once, restart if stuck
#   ./scripts/watchdog.sh --dry    check and report, change nothing
#
# What it leaves behind:
#   backups/health.status   one line, human readable, for update-status.sh
#   the airline log in game gets a note when it had to step in, so you can see
#   from inside the game that something happened while you were away
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

DRY_RUN=0
[[ "${1:-}" == "--dry" ]] && DRY_RUN=1

STATE_DIR="${AIRLINE_BACKUP_DIR:-$REPO_ROOT/backups}"
mkdir -p "$STATE_DIR"
STATE_FILE="$STATE_DIR/watchdog.state"
HEALTH_FILE="$STATE_DIR/health.status"

CYCLE_SECONDS="${AIRLINE_CYCLE_SECONDS:-1800}"
# How long a stall has to last before we act. Two cycles plus a margin: one
# slow cycle is normal, two missed in a row is not.
STALL_SECONDS=$(( CYCLE_SECONDS * 2 + 120 ))

CLIENT="$(command -v mariadb || command -v mysql || true)"
DB_USER="${AIRLINE_DB_USER:-sa}"
DB_PASS="${AIRLINE_DB_PASSWORD-admin}"
SCHEMA="${AIRLINE_DB_SCHEMA:-airline_v2_1}"

db() {
  [[ -n "$CLIENT" ]] || return 1
  if [[ -n "$DB_PASS" ]]; then
    "$CLIENT" --user="$DB_USER" --password="$DB_PASS" --skip-column-names --batch "$SCHEMA" -e "$1" 2>/dev/null
  else
    "$CLIENT" --user="$DB_USER" --skip-column-names --batch "$SCHEMA" -e "$1" 2>/dev/null
  fi
}

say_health() {
  echo "$(date '+%Y-%m-%d %H:%M:%S')  $1" > "$HEALTH_FILE"
  echo "$1"
}

# ------------------------------------------------------------------ checks ---

NOW=$(date +%s)
CYCLE="$(db "SELECT cycle FROM cycle LIMIT 1;" || true)"

if [[ -z "$CYCLE" ]]; then
  say_health "PROBLEM: cannot read the game's week number - is the database up?"
  exit 1
fi

SIM_ACTIVE=1
if command -v systemctl >/dev/null 2>&1; then
  systemctl is-active --quiet airline-sim 2>/dev/null || SIM_ACTIVE=0
fi

# Remember where we were, so the next run can tell whether anything moved.
LAST_CYCLE=""
LAST_SEEN=""
if [[ -f "$STATE_FILE" ]]; then
  LAST_CYCLE="$(cut -d' ' -f1 "$STATE_FILE" 2>/dev/null || true)"
  LAST_SEEN="$(cut -d' ' -f2 "$STATE_FILE" 2>/dev/null || true)"
fi

if [[ -z "$LAST_CYCLE" || "$LAST_CYCLE" != "$CYCLE" ]]; then
  # It moved (or this is the first run): note it and we are done.
  echo "$CYCLE $NOW" > "$STATE_FILE"
  if [[ "$SIM_ACTIVE" -eq 0 ]]; then
    say_health "PROBLEM: the simulation is not running (week $CYCLE)"
    exit 1
  fi
  say_health "OK - on week $CYCLE"
  exit 0
fi

# Same week as last time. How long has it been standing still?
STALLED_FOR=$(( NOW - ${LAST_SEEN:-$NOW} ))

if [[ "$STALLED_FOR" -lt "$STALL_SECONDS" ]]; then
  say_health "OK - on week $CYCLE, last moved ${STALLED_FOR}s ago"
  exit 0
fi

# ------------------------------------------------------------------- stuck ---

MINUTES=$(( STALLED_FOR / 60 ))
REASON="the game has been stuck on week $CYCLE for ${MINUTES} minute(s)"

if [[ "$DRY_RUN" -eq 1 ]]; then
  say_health "PROBLEM: $REASON (dry run - nothing restarted)"
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  say_health "PROBLEM: $REASON - restart it by hand, systemd is not available here"
  exit 1
fi

echo ">> $REASON - restarting the simulation"
sudo systemctl restart airline-sim || true
sleep 5

# Leave a note in the game itself, so it is visible from inside without
# anybody thinking to read a log file. Every airline gets it: whoever notices
# first can say something.
LOG_MESSAGE="The simulation was stuck on week $CYCLE and was restarted automatically"
if [[ -n "$CLIENT" ]]; then
  # category 3 = SELF_NOTE, severity 0 = WARN
  db "INSERT INTO log (airline, message, category, severity, cycle)
      SELECT id, '$LOG_MESSAGE', 3, 0, $CYCLE FROM airline;" || true
fi

# Give it until the next run to prove itself; do not count this moment as
# progress, or a simulation that dies on every start would look healthy.
echo "$CYCLE $NOW" > "$STATE_FILE"
say_health "RESTARTED: $REASON"
exit 1
