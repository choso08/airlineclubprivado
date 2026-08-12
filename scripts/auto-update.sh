#!/usr/bin/env bash
# Checks for new commits and, only if there are any, runs the normal update.
#
#   ./scripts/auto-update.sh
#
# Meant to be run on a timer. It is quiet and does nothing at all when the
# repository is already up to date, so running it every ten minutes costs a
# `git fetch` and no disruption.
#
# Install it (every 10 minutes) with `crontab -e`:
#
#   */10 * * * * cd $HOME/airline && ./scripts/auto-update.sh >> backups/auto-update.log 2>&1
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

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '')"
if [[ -z "$BRANCH" || "$BRANCH" == "HEAD" ]]; then
  echo "$(date '+%F %T') auto-update: not on a branch, skipping" >&2
  exit 1
fi

# A dirty tree means someone edited files on the server. Pulling would either
# fail or clobber their work, so stop and say so rather than guess. Changes to
# .env and game-settings.env are expected and ignored.
DIRTY="$(git status --porcelain -- . ':(exclude).env' ':(exclude)game-settings.env' 2>/dev/null)"
if [[ -n "$DIRTY" ]]; then
  echo "$(date '+%F %T') auto-update: local changes present, not touching them:" >&2
  echo "$DIRTY" | head -5 >&2
  exit 1
fi

if ! git fetch --quiet origin "$BRANCH" 2>/dev/null; then
  echo "$(date '+%F %T') auto-update: could not reach the remote, will retry next time" >&2
  exit 0    # a network blip is not a failure worth alerting on
fi

LOCAL="$(git rev-parse HEAD)"
REMOTE="$(git rev-parse "origin/$BRANCH")"

if [[ "$LOCAL" == "$REMOTE" ]]; then
  exit 0    # already current - the common case, stay silent
fi

# Only fast-forwards. If the branches have diverged, something unusual has
# happened and a human should look.
if ! git merge-base --is-ancestor "$LOCAL" "$REMOTE"; then
  echo "$(date '+%F %T') auto-update: local and remote have diverged, needs a human" >&2
  exit 1
fi

COUNT="$(git rev-list --count "$LOCAL..$REMOTE")"
echo "$(date '+%F %T') auto-update: $COUNT new commit(s), updating"
git log --oneline "$LOCAL..$REMOTE" | sed 's/^/    /'

"$REPO_ROOT/scripts/update.sh"

echo "$(date '+%F %T') auto-update: done, now at $(git rev-parse --short HEAD)"
