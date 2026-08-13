#!/usr/bin/env bash
# Run game weeks now, as fast as the machine manages, instead of waiting.
#
#   ./scripts/run-cycles.sh 3        advance three weeks
#   ./scripts/run-cycles.sh 1 --yes  skip the confirmation
#
# Useful for two quite different things:
#
#   - testing. Most of what the game does only happens at a cycle boundary -
#     passengers move, money changes hands, loyalty shifts, aircraft age. A
#     change to any of that cannot be checked without waiting for a week to
#     pass, and at ten minutes a week that is not a test anyone runs twice.
#   - the evening someone wants the world to move on a bit.
#
# How it works: the simulation runs one week every AIRLINE_CYCLE_SECONDS. This
# stops the normal service, runs the same simulation with that interval set to
# a few seconds so weeks follow each other back to back, watches the clock in
# the database, and puts the normal service back when it has gone far enough.
#
# THE WORLD REALLY MOVES. Passengers fly, salaries are paid, aircraft wear out,
# and none of it can be undone except by restoring the backup this takes first.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

COUNT="${1:-1}"
CONFIRMED=0
for arg in "$@"; do [[ "$arg" == "--yes" ]] && CONFIRMED=1; done

if ! [[ "$COUNT" =~ ^[0-9]+$ ]] || [[ "$COUNT" -lt 1 ]]; then
  echo "Usage: ./scripts/run-cycles.sh <how many weeks> [--yes]" >&2
  exit 1
fi

db() {
  mariadb --skip-column-names -u "$AIRLINE_DB_USER" \
    ${AIRLINE_DB_PASSWORD:+-p"$AIRLINE_DB_PASSWORD"} "$AIRLINE_DB_SCHEMA" -e "$1"
}

current_cycle() { db "SELECT cycle FROM cycle LIMIT 1;" | tail -1; }

START="$(current_cycle)"
if [[ -z "$START" ]]; then
  echo "Could not read the game clock. Is the database set up?" >&2
  exit 1
fi
TARGET=$(( START + COUNT ))

echo "== Advancing the game =="
echo
echo "   now:   week $START"
echo "   after: week $TARGET  ($COUNT week(s))"
echo
echo "   Everyone's game moves forward. Flights are flown, money is paid,"
echo "   aircraft get a week older. This cannot be undone except by restoring"
echo "   the backup taken below."
echo

if [[ "$CONFIRMED" != "1" ]]; then
  read -r -p "   Type RUN to go ahead: " answer
  if [[ "$answer" != "RUN" ]]; then
    echo "   Nothing done."
    exit 1
  fi
  echo
fi

echo ">> [1/4] Backing up first"
"$REPO_ROOT/scripts/backup-db.sh"

have_service() {
  command -v systemctl >/dev/null 2>&1 || return 1
  systemctl list-unit-files "$1.service" 2>/dev/null | grep -q "^$1.service" \
    || systemctl cat "$1.service" >/dev/null 2>&1
}

SERVICE=0
if have_service airline-sim; then
  SERVICE=1
  echo ">> [2/4] Stopping the normal simulation"
  sudo systemctl stop airline-sim
else
  echo ">> [2/4] No airline-sim service - stop the simulation window yourself"
  echo "         (Ctrl+C in it) before continuing, or weeks will be run twice."
  read -r -p "         Press enter when it is stopped. " _
fi

# Put the normal service back whatever happens below - including Ctrl+C. An
# interrupted run that left the game with no simulation would look exactly like
# the game being broken.
restore() {
  echo
  echo ">> Restoring the normal simulation"
  if [[ -n "${FAST_PID:-}" ]]; then
    # The process we started, and then anything it spawned. sbt runs the
    # simulation inside its own JVM today, but if that ever becomes a forked
    # one, killing only the launcher would leave a simulation running weeks
    # every five seconds with nobody watching it.
    #
    # Deliberately NOT a process-group kill: this script's own group is not
    # reliably distinct from the child's, and signalling it killed the tidy-up
    # halfway through - the simulation stopped, but the game was left with no
    # simulation running and nothing said so.
    kill -TERM "$FAST_PID" 2>/dev/null || true
    pkill -TERM -P "$FAST_PID" 2>/dev/null || true
    wait "$FAST_PID" 2>/dev/null || true
  fi
  if [[ "$SERVICE" == "1" ]]; then
    sudo systemctl start airline-sim
  else
    echo "   Start it again with ./scripts/run-simulation.sh"
  fi
  echo "   Game clock: week $(current_cycle)"
}
trap restore EXIT INT TERM

echo ">> [3/4] Running weeks back to back"
LOG="$REPO_ROOT/backups/run-cycles.log"

# Five seconds between weeks. A week takes far longer than that to compute, so
# in practice each one starts the moment the last finishes - which is as fast
# as this machine can go. The queued-up requests behind it do not matter: the
# process is stopped as soon as the clock reaches the target.
AIRLINE_CYCLE_SECONDS=5 "$REPO_ROOT/scripts/run-simulation.sh" > "$LOG" 2>&1 &
FAST_PID=$!

echo "   log: $LOG"
echo

LAST_SEEN="$START"
# A week takes under a minute on modest hardware, but the first one also pays
# for the simulation starting up. Twenty minutes each is generous rather than
# tight - the loop exits as soon as the clock moves, so the allowance only
# matters when something has genuinely gone wrong.
DEADLINE=$(( SECONDS + COUNT * 1200 + 600 ))

while true; do
  if ! kill -0 "$FAST_PID" 2>/dev/null; then
    echo "   The simulation stopped on its own. Last 20 lines:"
    tail -20 "$LOG"
    exit 1
  fi

  NOW="$(current_cycle)"
  if [[ -n "$NOW" && "$NOW" != "$LAST_SEEN" ]]; then
    echo "   week $NOW"
    LAST_SEEN="$NOW"
  fi
  if [[ -n "$NOW" && "$NOW" -ge "$TARGET" ]]; then
    break
  fi
  if [[ "$SECONDS" -gt "$DEADLINE" ]]; then
    echo "   Giving up: the clock is still on week $NOW. Last 20 lines:"
    tail -20 "$LOG"
    exit 1
  fi
  sleep 3
done

echo
echo ">> [4/4] Reached week $TARGET"
# The trap does the restoring.
