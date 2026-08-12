#!/usr/bin/env bash
# Move the game clock forwards or backwards.
#
#   ./scripts/game-time.sh                 show where the game is
#   ./scripts/game-time.sh --add 52        jump forward a year
#   ./scripts/game-time.sh --remove 4      wind back a month
#   ./scripts/game-time.sh --set 100       jump to a specific week
#
# READ THIS BEFORE USING IT
#
# One cycle is one in-game week, and this only changes the NUMBER. It does not
# play those weeks: no passengers fly, no income is earned, no aircraft age,
# no loans are repaid. Jumping forward a year moves the date and nothing else,
# and every airline's balance is exactly where it was.
#
# If what you want is for the game to actually PROGRESS faster, this is the
# wrong tool. Lower AIRLINE_CYCLE_SECONDS in game-settings.env instead - the
# weeks then really happen, just sooner.
#
# Going BACKWARDS is worse than going forwards. Routes, income history and
# aircraft purchase dates all still refer to cycles in what has become the
# future, so the game will show negative aircraft ages and gaps in the
# financial history. It is supported because it is occasionally useful for
# undoing an accidental jump, not because it is safe.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

SCHEMA="${AIRLINE_DB_SCHEMA:-airline_v2_1}"
HOST="${AIRLINE_DB_HOST:-localhost:3306}"
DB_HOST="${HOST%%:*}"
DB_PORT="${HOST##*:}"
[[ "$DB_PORT" == "$DB_HOST" ]] && DB_PORT=3306

CLIENT="$(command -v mariadb || command -v mysql)"
[[ -n "$CLIENT" ]] || { echo "ERROR: no mysql/mariadb client found." >&2; exit 1; }

db() {
  "$CLIENT" --host="$DB_HOST" --port="$DB_PORT" \
    --user="${AIRLINE_DB_USER:-sa}" --password="${AIRLINE_DB_PASSWORD:-admin}" \
    --skip-column-names --batch -e "$1" "$SCHEMA" 2>/dev/null
}

CURRENT="$(db 'SELECT cycle FROM cycle LIMIT 1;')"
[[ -n "$CURRENT" ]] || { echo "ERROR: could not read the current cycle from $SCHEMA." >&2; exit 1; }

START_YEAR="${AIRLINE_GAME_START_YEAR:-1970}"
show_date() {
  local cycle="$1"
  # Cycle 0 is 1 January of the start year; each cycle is a week.
  date -u -d "${START_YEAR}-01-01 +$(( cycle * 7 )) days" '+%a %d %b %Y' 2>/dev/null \
    || echo "week $cycle"
}

if [[ $# -eq 0 ]]; then
  echo "  Current cycle : $CURRENT"
  echo "  In-game date  : $(show_date "$CURRENT")"
  echo "  Calendar start: $START_YEAR (AIRLINE_GAME_START_YEAR)"
  echo
  echo "  ./scripts/game-time.sh --add 52      jump forward a year"
  echo "  ./scripts/game-time.sh --remove 4    wind back a month"
  echo "  ./scripts/game-time.sh --set 100     jump to a specific week"
  exit 0
fi

case "${1:-}" in
  --add)    TARGET=$(( CURRENT + ${2:?how many weeks?} )) ;;
  --remove) TARGET=$(( CURRENT - ${2:?how many weeks?} )) ;;
  --set)    TARGET=${2:?which week?} ;;
  *) echo "Unknown option: $1  (try --add, --remove or --set)" >&2; exit 1 ;;
esac

if [[ "$TARGET" -lt 0 ]]; then
  echo "ERROR: that would take the game before week 0." >&2
  exit 1
fi

echo
echo "  From : cycle $CURRENT  ($(show_date "$CURRENT"))"
echo "  To   : cycle $TARGET  ($(show_date "$TARGET"))"
echo

if [[ "$TARGET" -gt "$CURRENT" ]]; then
  echo "  Those $(( TARGET - CURRENT )) week(s) will NOT be played out. The date moves;"
  echo "  balances, passengers and aircraft ages do not."
else
  echo "  WINDING BACK. Routes, income history and aircraft dates will refer to"
  echo "  cycles in the future, so expect odd ages and gaps in the finances."
fi
echo

# The simulation writes the cycle at the end of every tick, so if it is running
# it will simply overwrite this within a couple of minutes.
if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet airline-sim 2>/dev/null; then
  echo "  NOTE: the simulation is running and will overwrite this at the end of"
  echo "        its current cycle. Stop it first:"
  echo "          sudo systemctl stop airline-sim"
  echo "        ...then run this, then start it again."
  echo
fi

read -r -p "  Type YES to continue: " CONFIRM
[[ "$CONFIRM" == "YES" ]] || { echo "  Aborted, nothing changed."; exit 1; }

echo ">> Backing up first"
"$REPO_ROOT/scripts/backup-db.sh" >/dev/null

db "UPDATE cycle SET cycle = $TARGET;"
NEW="$(db 'SELECT cycle FROM cycle LIMIT 1;')"

if [[ "$NEW" == "$TARGET" ]]; then
  echo ">> Done. The game is now at cycle $NEW ($(show_date "$NEW"))."
  echo "   Players need to reload for the clock to catch up."
else
  echo "ERROR: the cycle still reads $NEW - the change did not stick." >&2
  exit 1
fi
