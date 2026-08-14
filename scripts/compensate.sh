#!/usr/bin/env bash
# Put money into (or take it out of) an airline's balance, and leave a note in
# that airline's log so there is a record of it having been done by hand.
#
#   ./scripts/compensate.sh <airline> <amount> ["reason"]
#
#   <airline>  airline id, or its name in quotes
#   <amount>   whole number, may be negative
#   [reason]   shown in the airline's log; defaults to a generic note
#
# Examples
#   ./scripts/compensate.sh AirAbacate 1335403700 "86 cycles lost to the crash"
#   ./scripts/compensate.sh 3 -125776
#
# Nothing happens without a yes. Pass --yes to skip the question when you are
# running several in a row.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

ASSUME_YES=0
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --yes|-y) ASSUME_YES=1 ;;
    *) ARGS+=("$arg") ;;
  esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

if [[ $# -lt 2 ]]; then
  sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
fi

AIRLINE_REF="$1"
AMOUNT="$2"
REASON="${3:-Adjusted by the server owner}"

[[ "$AMOUNT" =~ ^-?[0-9]+$ ]] || { echo "ERROR: amount must be a whole number, got '$AMOUNT'" >&2; exit 1; }

CLIENT="$(command -v mariadb || command -v mysql)"
[[ -n "$CLIENT" ]] || { echo "ERROR: no mariadb client found" >&2; exit 1; }

DB_USER="${AIRLINE_DB_USER:-sa}"
DB_PASS="${AIRLINE_DB_PASSWORD-admin}"
SCHEMA="${AIRLINE_DB_SCHEMA:-airline_v2_1}"

db() {
  if [[ -n "$DB_PASS" ]]; then
    "$CLIENT" --user="$DB_USER" --password="$DB_PASS" --skip-column-names --batch "$SCHEMA" -e "$1"
  else
    "$CLIENT" --user="$DB_USER" --skip-column-names --batch "$SCHEMA" -e "$1"
  fi
}

# Look up by id if it is a number, by name otherwise. Names are matched exactly
# so that "TAP" cannot quietly hit "TAP AIR PORTUGAL".
ESCAPED="${AIRLINE_REF//\'/\'\'}"
if [[ "$AIRLINE_REF" =~ ^[0-9]+$ ]]; then
  ROW=$(db "SELECT a.id, a.name FROM airline a WHERE a.id = $AIRLINE_REF;")
else
  ROW=$(db "SELECT a.id, a.name FROM airline a WHERE a.name = '$ESCAPED';")
fi

if [[ -z "$ROW" ]]; then
  echo "ERROR: no airline matches '$AIRLINE_REF'. The ones on this server:" >&2
  db "SELECT a.id, a.name FROM airline a ORDER BY a.id;" >&2
  exit 1
fi
if [[ $(printf '%s\n' "$ROW" | wc -l) -gt 1 ]]; then
  echo "ERROR: '$AIRLINE_REF' matches more than one airline. Use the id:" >&2
  printf '%s\n' "$ROW" >&2
  exit 1
fi

AIRLINE_ID=$(printf '%s' "$ROW" | cut -f1)
AIRLINE_NAME=$(printf '%s' "$ROW" | cut -f2)

# balance is a text column in this schema, so read it as a number explicitly.
BEFORE=$(db "SELECT CAST(balance AS SIGNED) FROM airline_info WHERE airline = $AIRLINE_ID;")
[[ -n "$BEFORE" ]] || { echo "ERROR: airline $AIRLINE_ID has no balance row" >&2; exit 1; }
AFTER=$((BEFORE + AMOUNT))

printf '  Airline   %s (id %s)\n' "$AIRLINE_NAME" "$AIRLINE_ID"
printf '  Balance   %s\n' "$(printf "%'d" "$BEFORE")"
printf '  Change    %s\n' "$(printf "%'+d" "$AMOUNT")"
printf '  After     %s\n' "$(printf "%'d" "$AFTER")"
printf '  Reason    %s\n' "$REASON"
echo

if [[ "$ASSUME_YES" -ne 1 ]]; then
  read -r -p "Apply this? [y/N] " ANSWER
  [[ "$ANSWER" == "y" || "$ANSWER" == "Y" ]] || { echo "Nothing changed."; exit 0; }
fi

CYCLE=$(db "SELECT cycle FROM cycle LIMIT 1;")
CYCLE="${CYCLE:-0}"
LOG_REASON="${REASON//\'/\'\'}"

# Write the balance as a plain integer: this column is text, and a value with a
# decimal point in it is what took the game down. See ResultSetUtil.
db "UPDATE airline_info SET balance = CAST($AFTER AS CHAR) WHERE airline = $AIRLINE_ID;"
# category 3 = SELF_NOTE, severity 1 = INFO
db "INSERT INTO log (airline, message, category, severity, cycle)
    VALUES ($AIRLINE_ID, '$LOG_REASON: $(printf "%'+d" "$AMOUNT")', 3, 1, $CYCLE);"

NOW=$(db "SELECT CAST(balance AS SIGNED) FROM airline_info WHERE airline = $AIRLINE_ID;")
printf 'Done. %s now holds %s\n' "$AIRLINE_NAME" "$(printf "%'d" "$NOW")"
echo "The player sees a note in their log; it takes effect on their next page load."
