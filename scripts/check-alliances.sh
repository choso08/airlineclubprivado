#!/usr/bin/env bash
# What the alliances actually look like in the database - and, if you ask, a
# tidy-up.
#
#   ./scripts/check-alliances.sh            show
#   ./scripts/check-alliances.sh --repair   fix what is safe to fix
#
# The alliance screen can only draw what it is given, so when it looks wrong
# the question is always which of these three it is:
#
#   an alliance with no leader     nobody can accept an application to it, so
#                                  it is a dead end that still looks joinable.
#                                  Everyone who applies waits for nothing.
#   an alliance with no members    the wreckage of one that was abandoned. It
#                                  sits in the list for ever.
#   a member with no alliance      the worst of the three: the game thinks you
#                                  are in an alliance, so it will not let you
#                                  form one - and the alliance it names is not
#                                  there to leave.
#
# --repair promotes the longest-standing member of a leaderless alliance to
# leader, deletes alliances that have nobody in them at all, and clears
# membership rows pointing at alliances that no longer exist. It never touches
# an alliance that has a leader, and it takes a backup first.
set -uo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

REPAIR=0
[[ "${1:-}" == "--repair" ]] && REPAIR=1

SCHEMA="${AIRLINE_DB_SCHEMA:-airline_v2_1}"
HOST="${AIRLINE_DB_HOST:-localhost:3306}"
DB_HOST="${HOST%%:*}"
DB_PORT="${HOST##*:}"
[[ "$DB_PORT" == "$DB_HOST" ]] && DB_PORT=3306

CLIENT="$(command -v mariadb || command -v mysql)"
[[ -n "$CLIENT" ]] || { echo "ERROR: no mysql/mariadb client found." >&2; exit 1; }

# common.sh turns on "exit on error", so a query that fails would otherwise
# kill this script where it stands - no output, no explanation, and the check
# below for an unreadable database would never get the chance to run.
db() {
  "$CLIENT" --host="$DB_HOST" --port="$DB_PORT" \
    --user="${AIRLINE_DB_USER:-sa}" --password="${AIRLINE_DB_PASSWORD-admin}" \
    --skip-column-names --batch -e "$1" "$SCHEMA" 2>/dev/null || true
}

TOTAL="$(db 'SELECT COUNT(*) FROM alliance;')"
if [[ -z "$TOTAL" ]]; then
  echo "ERROR: could not read $SCHEMA. Is the database running, and .env correct?" >&2
  exit 1
fi

echo
echo "  ALLIANCES ($TOTAL)"
echo "  ─────────────────────────────────────────────────────────────────"
db "SELECT CONCAT('  ', RPAD(a.name, 22),
                  RPAD(IFNULL(l.name, '(no leader)'), 22),
                  LPAD(COUNT(CASE WHEN m.role <> 'APPLICANT' THEN 1 END), 3), ' members ',
                  LPAD(COUNT(CASE WHEN m.role  = 'APPLICANT' THEN 1 END), 3), ' waiting')
     FROM alliance a
     LEFT JOIN alliance_member m ON m.alliance = a.id
     LEFT JOIN alliance_member lm ON lm.alliance = a.id AND lm.role = 'LEADER'
     LEFT JOIN airline l ON l.id = lm.airline
     GROUP BY a.id, a.name, l.name
     ORDER BY a.id;"

LEADERLESS="$(db "SELECT COUNT(*) FROM alliance a WHERE NOT EXISTS
                    (SELECT 1 FROM alliance_member m WHERE m.alliance = a.id AND m.role = 'LEADER');")"
EMPTY="$(db "SELECT COUNT(*) FROM alliance a WHERE NOT EXISTS
               (SELECT 1 FROM alliance_member m WHERE m.alliance = a.id);")"
ORPHANS="$(db "SELECT COUNT(*) FROM alliance_member m WHERE NOT EXISTS
                 (SELECT 1 FROM alliance a WHERE a.id = m.alliance);")"

echo
echo "  PROBLEMS"
echo "  ─────────────────────────────────────────────────────────────────"
echo "  Alliances with nobody in charge     : $LEADERLESS"
echo "  Alliances with nobody in them       : $EMPTY"
echo "  Airlines in an alliance that is gone: $ORPHANS"

if [[ "$LEADERLESS" == "0" && "$EMPTY" == "0" && "$ORPHANS" == "0" ]]; then
  echo
  echo "  Nothing wrong here."
  exit 0
fi

if [[ "$ORPHANS" != "0" ]]; then
  echo
  echo "  Stuck airlines:"
  db "SELECT CONCAT('    ', al.name, ' - thinks it is in alliance ', m.alliance)
      FROM alliance_member m JOIN airline al ON al.id = m.airline
      WHERE NOT EXISTS (SELECT 1 FROM alliance a WHERE a.id = m.alliance);"
fi

if [[ "$REPAIR" == "0" ]]; then
  echo
  echo "  Run with --repair to fix these."
  exit 0
fi

echo
echo "  --repair will:"
[[ "$LEADERLESS" != "0" ]] && echo "    - make the longest-standing member of each leaderless alliance its leader"
[[ "$EMPTY" != "0" ]]      && echo "    - delete the alliances with nobody in them"
[[ "$ORPHANS" != "0" ]]    && echo "    - free the airlines whose alliance no longer exists"
echo "    Airlines, routes, aircraft and money are untouched."
echo
read -r -p "  Type REPAIR to continue: " CONFIRM
[[ "$CONFIRM" == "REPAIR" ]] || { echo "  Aborted, nothing changed."; exit 1; }

echo ">> Backing up first"
"$REPO_ROOT/scripts/backup-db.sh" >/dev/null

# Promote before deleting: an alliance with members but no leader must keep its
# members, and one with no members at all has nobody to promote, so the order
# settles which of the two each alliance is.
echo ">> Promoting a leader where there is none"
db "UPDATE alliance_member m
    JOIN (
      SELECT m2.alliance, MIN(m2.joined_cycle) AS first_cycle
      FROM alliance_member m2
      WHERE m2.role <> 'APPLICANT'
        AND NOT EXISTS (SELECT 1 FROM alliance_member l
                        WHERE l.alliance = m2.alliance AND l.role = 'LEADER')
      GROUP BY m2.alliance
    ) pick ON pick.alliance = m.alliance AND pick.first_cycle = m.joined_cycle
    SET m.role = 'LEADER'
    WHERE m.role <> 'APPLICANT';"

echo ">> Removing alliances with nobody in them"
db "DELETE FROM alliance WHERE NOT EXISTS
      (SELECT 1 FROM (SELECT * FROM alliance_member) m WHERE m.alliance = alliance.id);"

echo ">> Freeing airlines whose alliance is gone"
db "DELETE FROM alliance_member WHERE NOT EXISTS
      (SELECT 1 FROM (SELECT * FROM alliance) a WHERE a.id = alliance_member.alliance);"

echo
echo ">> Done. Restart the web front-end so it forgets what it had cached:"
echo "     sudo systemctl restart airline-web"
echo
"$0"
