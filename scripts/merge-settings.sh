#!/usr/bin/env bash
# Keep your settings across an update.
#
#   ./scripts/merge-settings.sh <file> <yours> <base>
#
#     file   the file as the update just left it
#     yours  a copy of it as you had it, before the update
#     base   the version the repository shipped before the update
#
# game-settings.env is in git and is also yours to edit, and those two do not
# sit together: the moment an update changes a line you had changed, git
# refuses to pull at all. Which is exactly what happened here - the updater ran
# every ten minutes, the pull failed, and nothing said so.
#
# Your changes are worked out by comparing what YOU had against what the
# repository USED to ship, not against what it ships now. That is the whole
# point: it separates "the player chose this" from "the repository changed it",
# so a setting you never touched follows the repository and one you did stays
# yours.
set -euo pipefail

FILE="${1:-}"; YOURS="${2:-}"; BASE="${3:-}"
if [[ -z "$FILE" || -z "$YOURS" || -z "$BASE" ]]; then
  echo "Usage: merge-settings.sh <file> <yours> <base>" >&2
  exit 1
fi
[[ -f "$FILE" && -f "$YOURS" && -f "$BASE" ]] || exit 0

pairs() { grep -E '^[A-Z_][A-Z0-9_]*=' "$1" 2>/dev/null || true; }
value_of() { grep -m1 -E "^$2=" "$1" 2>/dev/null || true; }

KEPT=0
while IFS= read -r yourLine; do
  [[ -z "$yourLine" ]] && continue
  key="${yourLine%%=*}"

  # Unchanged by you? Then whatever the repository says now wins.
  [[ "$yourLine" == "$(value_of "$BASE" "$key")" ]] && continue
  # Already what you want?
  [[ "$yourLine" == "$(value_of "$FILE" "$key")" ]] && continue

  if [[ -n "$(value_of "$FILE" "$key")" ]]; then
    # Replaced in place so the comments explaining it stay put. Done with awk
    # on an exact key match rather than sed: these values contain slashes and
    # braces - tile URLs especially - and would need escaping otherwise.
    awk -v k="$key" -v repl="$yourLine" '
      $0 ~ "^" k "=" { print repl; next }
      { print }
    ' "$FILE" > "$FILE.merging" && mv "$FILE.merging" "$FILE"
  else
    # Gone from the new version. Kept rather than dropped in silence: if it
    # still does something it should keep doing it, and if it does not, it sits
    # there visibly instead of vanishing.
    printf '\n# Kept from your settings; not shipped by default any more.\n%s\n' "$yourLine" >> "$FILE"
  fi
  echo "   kept $key"
  KEPT=$((KEPT + 1))
done < <(pairs "$YOURS")

[[ "$KEPT" -gt 0 ]] && echo "   $KEPT setting(s) of yours kept through the update"
exit 0
