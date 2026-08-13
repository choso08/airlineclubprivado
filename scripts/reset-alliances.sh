#!/usr/bin/env bash
# Wipe every alliance and start that part of the game over.
#
#   ./scripts/reset-alliances.sh
#
# For when people have been experimenting and the world has filled with
# half-made alliances. Airlines, routes, aircraft and money are NOT touched -
# only alliance membership, history, missions and the champion bonuses that
# derive from them.
#
# Everyone simply ends up in no alliance, free to form new ones.
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

ALLIANCES="$(db 'SELECT COUNT(*) FROM alliance;')"
MEMBERS="$(db 'SELECT COUNT(*) FROM alliance_member;')"

if [[ -z "$ALLIANCES" ]]; then
  echo "ERROR: could not read $SCHEMA. Is the database running, and .env correct?" >&2
  exit 1
fi

echo
echo "  Alliances : $ALLIANCES"
echo "  Members   : $MEMBERS"
echo
if [[ "$ALLIANCES" == "0" ]]; then
  echo "  Nothing to reset."
  exit 0
fi

db 'SELECT CONCAT("    - ", name) FROM alliance ORDER BY id;'
echo
echo "  All of the above will be deleted, along with their membership, history,"
echo "  missions and rewards. Airlines, routes, aircraft and money are untouched."
echo

read -r -p "  Type RESET to continue: " CONFIRM
[[ "$CONFIRM" == "RESET" ]] || { echo "  Aborted, nothing changed."; exit 1; }

echo ">> Backing up first"
"$REPO_ROOT/scripts/backup-db.sh" >/dev/null

# Order matters only for readability - foreign key checks are off for the
# duration so the tables can go in any order without complaint.
echo ">> Clearing alliance data"
"$CLIENT" --host="$DB_HOST" --port="$DB_PORT" \
  --user="${AIRLINE_DB_USER:-sa}" --password="${AIRLINE_DB_PASSWORD:-admin}" \
  "$SCHEMA" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM alliance_mission_reward_property;
DELETE FROM alliance_mission_reward;
DELETE FROM alliance_mission_property_history;
DELETE FROM alliance_mission_property;
DELETE FROM alliance_mission_stats;
DELETE FROM alliance_mission;
DELETE FROM alliance_stats;
DELETE FROM alliance_history;
DELETE FROM alliance_member;
DELETE FROM alliance_label_color_by_airline;
DELETE FROM alliance_label_color_by_alliance;
DELETE FROM alliance;

SET FOREIGN_KEY_CHECKS = 1;
SQL

REMAINING="$(db 'SELECT COUNT(*) FROM alliance;')"
if [[ "$REMAINING" == "0" ]]; then
  echo ">> Done. $ALLIANCES alliance(s) removed; everyone is now unaligned."
  echo "   Players should reload the page."
  echo
  echo "   The next simulation cycle recomputes alliance standings from"
  echo "   nothing, so there is no need to restart anything by hand."
else
  echo "ERROR: $REMAINING alliance(s) still present - the reset did not complete." >&2
  exit 1
fi
