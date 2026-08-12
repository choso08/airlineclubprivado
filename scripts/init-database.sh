#!/usr/bin/env bash
# First-time world generation.
#
# *** THIS WIPES EVERYTHING, INCLUDING PLAYER ACCOUNTS. ***
#
# MainInit calls Meta.createSchema(), which DROPs and recreates every table -
# user, user_secret, user_airline, airline, airplane, link included. It is a
# fresh start, not a world refresh. Run it once during setup, before anyone
# registers, and settle any world data changes (extra airports, sizes) before
# your friends start playing.
#
# To change the world after people have started, restore is the only safe
# route: back up, re-init, and accept that everyone starts over. There is no
# supported way to swap the airports out from under existing routes - airport
# ids are reassigned, and every saved route points at the old ones.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

SCHEMA="${AIRLINE_DB_SCHEMA:-airline_v2_1}"

# Is there anything to lose? Only ask if the tables are actually populated.
EXISTING_USERS=""
if command -v mariadb >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
  CLIENT="$(command -v mariadb || command -v mysql)"
  HOST="${AIRLINE_DB_HOST:-localhost:3306}"
  EXISTING_USERS=$("$CLIENT" \
    --host="${HOST%%:*}" --port="$([[ "${HOST##*:}" != "${HOST%%:*}" ]] && echo "${HOST##*:}" || echo 3306)" \
    --user="${AIRLINE_DB_USER:-sa}" --password="${AIRLINE_DB_PASSWORD:-admin}" \
    --skip-column-names --batch \
    -e "SELECT COUNT(*) FROM user;" "$SCHEMA" 2>/dev/null || echo "")
fi

if [[ -n "$EXISTING_USERS" && "$EXISTING_USERS" -gt 0 ]]; then
  echo
  echo "  ****************************************************************"
  echo "  *  WARNING: $SCHEMA already has $EXISTING_USERS registered player(s)."
  echo "  *"
  echo "  *  This will DELETE them, along with every airline, aircraft,"
  echo "  *  route and euro they have earned. It cannot be undone."
  echo "  *"
  echo "  *  Back up first:  ./scripts/backup-db.sh"
  echo "  ****************************************************************"
  echo
  read -r -p "  Type ERASE to continue, anything else to abort: " CONFIRM
  if [[ "$CONFIRM" != "ERASE" ]]; then
    echo ">> Aborted. Nothing was changed."
    exit 1
  fi
fi

echo ">> Initialising world data into $SCHEMA at ${AIRLINE_DB_HOST:-localhost:3306}"
sbt_run airline-data "runMain com.patson.init.MainInit"
