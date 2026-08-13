#!/usr/bin/env bash
# Records which version is running, and what changed, for the game to show.
#
#   ./scripts/write-version.sh [previous-commit]
#
# Writes airline-web/conf/version.json. Called by update.sh before building,
# with the commit that was running beforehand, so the changelog covers exactly
# what this update brings.
#
# Generated from git rather than read at runtime: by the time the application
# is running it should not care whether it was deployed from a checkout, and
# parsing .git from Scala to answer "what version am I" is a poor trade.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

OUT="$REPO_ROOT/airline-web/conf/version.json"
PREVIOUS="${1:-}"

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo '{"version":"unknown","date":"","changes":[]}' > "$OUT"
  exit 0
fi

COMMIT="$(git rev-parse --short HEAD)"
DATE="$(git log -1 --format=%cd --date=format:'%Y-%m-%d %H:%M')"
COUNT="$(git rev-list --count HEAD)"

# JSON-escape a line of text: backslashes and quotes, and drop control chars.
escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\000-\037'
}

CHANGES=""
if [[ -n "$PREVIOUS" ]] && git cat-file -e "$PREVIOUS" 2>/dev/null; then
  # Subject lines only. Commit bodies here are long, and the panel is a summary
  # for players rather than a log for whoever wrote them.
  while IFS= read -r subject; do
    [[ -z "$subject" ]] && continue
    [[ -n "$CHANGES" ]] && CHANGES+=","
    CHANGES+="\"$(escape "$subject")\""
  done < <(git log --format=%s "$PREVIOUS..HEAD" 2>/dev/null | head -20)
fi

cat > "$OUT" <<EOF
{
  "version": "b$COUNT.$COMMIT",
  "commit": "$COMMIT",
  "date": "$DATE",
  "changes": [$CHANGES]
}
EOF

echo ">> Version b$COUNT.$COMMIT ($DATE)"
