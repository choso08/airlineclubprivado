#!/usr/bin/env bash
# Every battery, one after another.
#
#   ./test/run-all.sh [url]
#
# Default url is http://localhost:9000. Most of these talk to a running game -
# they check what a player would actually see, which is the only thing worth
# checking about a browser - so start it first:
#
#   ./scripts/run-web.sh
#
# The exit status is the number of batteries that failed, so this can be hung
# off anything that cares. Nothing here writes to the database.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
URL="${1:-http://localhost:9000}"

FAILED=0
RAN=0

run() {
  local name="$1"; shift
  RAN=$((RAN + 1))
  echo
  echo "────────────────────────────────────────────────────────────"
  echo "  $name"
  echo "────────────────────────────────────────────────────────────"
  if "$@"; then
    return 0
  fi
  FAILED=$((FAILED + 1))
  echo "  ^ $name FAILED"
}

# This one needs nothing running at all.
run "search matching"   "$HERE/search-matching-test.sh"

# These need the game up.
if curl -s -o /dev/null -m 5 "$URL"; then
  run "search"          node "$HERE/search-test.js" "$URL"
  run "dropdown filter" node "$HERE/select-search-test.js" "$URL"
  run "game mechanics"  node "$HERE/mechanics-test.js" "$URL"
  run "translation"     node "$HERE/translate-test.js" "$URL"
  run "map theme"       node "$HERE/theme-test.js" "$URL"
  run "flight animation" node "$HERE/flight-animation-test.js" "$URL"
  run "instance features" node "$HERE/features-test.js" "$URL"
  run "auto refresh"    node "$HERE/auto-refresh-test.js" "$URL"
  run "map shim"        node "$HERE/osm-shim-test.js" "$URL"
else
  echo
  echo "  $URL is not answering - skipping everything that needs the game running."
  echo "  Start it with ./scripts/run-web.sh"
fi

echo
echo "════════════════════════════════════════════════════════════"
echo "  $((RAN - FAILED))/$RAN batteries passed"
echo "════════════════════════════════════════════════════════════"
exit "$FAILED"
