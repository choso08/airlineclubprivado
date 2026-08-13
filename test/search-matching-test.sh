#!/usr/bin/env bash
# The search matching rules, checked without standing the game up.
#
#   ./test/search-matching-test.sh
#
# SearchText has no dependencies of its own, so this compiles that one file
# and its battery and runs them - a couple of seconds, and no database, no web
# server and no Elasticsearch anywhere near it.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$HERE")"
OUT="$HERE/target/search-matching"

command -v javac >/dev/null || { echo "javac not found - install a JDK" >&2; exit 1; }

mkdir -p "$OUT"
javac -d "$OUT" \
  "$REPO/airline-web/app/controllers/SearchText.java" \
  "$HERE/java/SearchMatchingTest.java"

echo
echo "=== search matching battery ==="
echo
java -cp "$OUT" controllers.SearchMatchingTest
