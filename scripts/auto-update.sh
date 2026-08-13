#!/usr/bin/env bash
# Checks for new commits and, only if there are any, runs the normal update.
#
#   ./scripts/auto-update.sh
#
# Meant to be run on a timer. It is quiet and does nothing at all when the
# repository is already up to date, so a check costs one `git fetch` and no
# disruption - which is why it can run every few minutes.
#
# scripts/install-services.sh installs the timer; how often it fires is
# AIRLINE_UPDATE_INTERVAL_MINUTES in game-settings.env, 5 by default. Changing
# that needs install-services.sh run again to rewrite the timer.
#
# What happens when an update IS found:
#   1. the database is backed up
#   2. the new version is compiled - if that fails, nothing is stopped and the
#      old version keeps serving
#   3. players get a 30 second countdown, then the site restarts and their
#      pages reload themselves
#
# Turn it off by removing the crontab line, or by setting
# AIRLINE_AUTO_UPDATE=off in game-settings.env.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [[ "${AIRLINE_AUTO_UPDATE:-on}" == "off" ]]; then
  exit 0
fi

cd "$REPO_ROOT"

# Every run leaves a line here, whatever it decided. The journal has this too,
# but it is the first place anyone looks and it survives being asked about
# weeks later: "it stopped updating" needs an answer, and silence is not one.
STATUS_FILE="$REPO_ROOT/backups/auto-update.status"
record() {
  mkdir -p "$REPO_ROOT/backups" 2>/dev/null || true
  echo "$(date '+%F %T') $*" >> "$STATUS_FILE" 2>/dev/null || true
  # Keep the last few hundred lines; this runs every few minutes for months.
  if [[ -f "$STATUS_FILE" ]]; then
    tail -500 "$STATUS_FILE" > "$STATUS_FILE.trimmed" 2>/dev/null &&       mv "$STATUS_FILE.trimmed" "$STATUS_FILE" 2>/dev/null || true
  fi
}

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"
if [[ -z "$BRANCH" || "$BRANCH" == "HEAD" ]]; then
  echo "$(date '+%F %T') auto-update: not on a branch, skipping" >&2
  record "refused - not on a branch"
  exit 1
fi

# Stop if someone has edited TRACKED files on the server - pulling would
# either fail or clobber their work.
#
# Untracked files are deliberately ignored. They cannot be overwritten by a
# fast-forward (git refuses rather than clobbering), and treating them as
# "local changes" meant the updater blocked itself forever over things nobody
# considers changes: the backups/ directory our own script creates, and the
# .lnk files Windows leaves behind when you drag a shortcut to the Desktop.
#
# .env and game-settings.env are excluded too - those are meant to differ.
DIRTY="$(git status --porcelain --untracked-files=no \
           -- . ':(exclude).env' ':(exclude)game-settings.env' 2>/dev/null)"
if [[ -n "$DIRTY" ]]; then
  echo "$(date '+%F %T') auto-update: tracked files edited locally, not touching them:" >&2
  echo "$DIRTY" | head -5 >&2
  echo "  Revert them, or commit them, and the updater will resume." >&2
  record "refused - tracked files edited here: $(echo "$DIRTY" | head -3 | tr '\n' ' ')"
  exit 1
fi

if ! git fetch --quiet origin "$BRANCH" 2>/dev/null; then
  echo "$(date '+%F %T') auto-update: could not reach the remote, will retry next time" >&2
  record "could not reach the remote"
  exit 0    # a network blip is not a failure worth alerting on
fi

LOCAL="$(git rev-parse HEAD)"
REMOTE="$(git rev-parse "origin/$BRANCH")"

if [[ "$LOCAL" == "$REMOTE" ]]; then
  record "up to date at $(git rev-parse --short HEAD)"
  exit 0    # already current - the common case, stay silent
fi

# Only fast-forwards. If the branches have diverged, something unusual has
# happened and a human should look.
if ! git merge-base --is-ancestor "$LOCAL" "$REMOTE"; then
  echo "$(date '+%F %T') auto-update: local and remote have diverged, needs a human" >&2
  record "refused - the checkout has diverged from the remote"
  exit 1
fi

COUNT="$(git rev-list --count "$LOCAL..$REMOTE")"
echo "$(date '+%F %T') auto-update: $COUNT new commit(s), updating"
git log --oneline "$LOCAL..$REMOTE" | sed 's/^/    /'

record "installing $COUNT commit(s)"

# The update itself can fail - a compile error, a full disk, a database that
# will not answer - and that is exactly the case where nothing was ever said.
if "$REPO_ROOT/scripts/update.sh"; then
  echo "$(date '+%F %T') auto-update: done, now at $(git rev-parse --short HEAD)"
  record "updated to $(git rev-parse --short HEAD)"
else
  STATUS=$?
  echo "$(date '+%F %T') auto-update: the update FAILED (exit $STATUS)" >&2
  record "FAILED during the update (exit $STATUS) - see: journalctl -u airline-update -n 50"
  exit "$STATUS"
fi
