#!/usr/bin/env bash
# Forget the "this place has no photograph" entries, so they are looked up again.
#
#   ./scripts/clear-image-cache.sh          forget only the failures
#   ./scripts/clear-image-cache.sh --all    forget everything, including photos
#                                           that were found
#   ./scripts/clear-image-cache.sh FRA      just Frankfurt (city and airport)
#   ./scripts/clear-image-cache.sh FRA --all   Frankfurt, photo included
#
# Why this is needed at all:
#
# When the game looks up a picture and is told there is none, it writes that
# down and never asks again - sensible, since most airports genuinely have no
# photograph and asking every time would be wasteful. But it wrote the same
# thing down when the lookup FAILED, and a lookup with no Google API key fails
# every time. So any city or airport opened before the switch to Wikipedia has
# a permanent "no picture" against its name, and switching providers, restarting
# and rebuilding all change nothing.
#
# This clears those rows. Photographs already found are kept, unless --all.
#
# (New failures no longer get written down - see WikimediaImageUtil - so this
# is a one-off for entries created before that fix.)
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

ALL=0
IATA=""
for arg in "$@"; do
  case "$arg" in
    --all) ALL=1 ;;
    -*)    echo "Unknown option: $arg" >&2; exit 1 ;;
    *)     IATA="$(echo "$arg" | tr '[:lower:]' '[:upper:]')" ;;
  esac
done

db() {
  mariadb -u "$AIRLINE_DB_USER" ${AIRLINE_DB_PASSWORD:+-p"$AIRLINE_DB_PASSWORD"} \
    "$AIRLINE_DB_SCHEMA" -e "$1"
}

# One airport, or all of them. The picture of a city and the picture of its
# airport are two separate entries under the same airport id, so naming an
# airport clears both.
WHERE=""
if [[ -n "$IATA" ]]; then
  AIRPORT_ID="$(db "SELECT id FROM airport WHERE iata = '$IATA' LIMIT 1;" --skip-column-names 2>/dev/null | tail -1)"
  if [[ -z "$AIRPORT_ID" ]]; then
    echo "No airport with IATA code $IATA." >&2
    exit 1
  fi
  WHERE=" WHERE resource_id = $AIRPORT_ID"
  echo "Only $IATA (airport $AIRPORT_ID)."
fi

TOTAL="$(db "SELECT COUNT(*) FROM google_resource$WHERE;" --skip-column-names 2>/dev/null | tail -1)"
BLANK="$(db "SELECT COUNT(*) FROM google_resource${WHERE:- WHERE 1=1} AND url IS NULL;" --skip-column-names 2>/dev/null | tail -1)"

echo "Remembered images: ${TOTAL:-0}"
echo "  of which \"no picture\": ${BLANK:-0}"
echo

if [[ "$ALL" == "1" ]]; then
  echo "Forgetting ALL of them. Every picture will be looked up again on first"
  echo "view, which is slower but harmless."
  db "DELETE FROM google_resource$WHERE;"
  echo "Done - ${TOTAL:-0} entries cleared."
else
  if [[ "${BLANK:-0}" == "0" ]]; then
    echo "Nothing to clear. If pictures are still missing, the lookups are"
    echo "failing now rather than being remembered - check the log:"
    echo "    journalctl -u airline-web -n 100 | grep -i wikipedia"
    exit 0
  fi
  db "DELETE FROM google_resource${WHERE:- WHERE 1=1} AND url IS NULL;"
  echo "Done - $BLANK entries cleared. Photographs already found were kept."
fi

echo
echo "The site also keeps them in memory for a day, so restart it to see the"
echo "difference straight away:"
echo "    sudo systemctl restart airline-web"
