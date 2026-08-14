#!/usr/bin/env bash
# Why the game is or is not updating itself.
#
#   ./scripts/update-status.sh
#
# The updater is quiet on purpose - it runs every few minutes and finding
# nothing is the normal case - so when it stops there is nothing to see. This
# prints everything that decides whether it runs, in one go: the timer, the
# last few attempts, what the checkout looks like, and the two things that
# stop it dead (a tracked file edited on the server, and a full disk).
#
# Nothing here changes anything.
set -uo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
# common.sh turns on exit-on-error, which is wrong for a report: nearly every
# command here is a question that may legitimately have no answer - no systemd,
# no journal, no remote - and each one is a thing worth printing rather than a
# reason to stop half way down the page.
set +e
cd "$REPO_ROOT"

heading() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }
note()    { printf '   %s\n' "$*"; }
bad()     { printf '\033[1;31m   %s\033[0m\n' "$*"; }
good()    { printf '\033[1;32m   %s\033[0m\n' "$*"; }

heading "SETTING"
if [[ "${AIRLINE_AUTO_UPDATE:-on}" == "off" ]]; then
  bad "AIRLINE_AUTO_UPDATE=off - the updater exits immediately. That is the answer."
else
  good "AIRLINE_AUTO_UPDATE is on"
fi
note "Checking every ${AIRLINE_UPDATE_INTERVAL_MINUTES:-5} minute(s), per game-settings.env"

heading "TIMER"
if ! command -v systemctl >/dev/null 2>&1; then
  bad "systemd is not available here - the timer cannot be running."
else
  if systemctl is-enabled airline-update.timer >/dev/null 2>&1; then
    good "airline-update.timer is installed"
  else
    bad "airline-update.timer is NOT installed - run ./scripts/install-services.sh"
  fi
  systemctl list-timers 'airline-*' --no-pager 2>/dev/null | sed -n '1,5p' | sed 's/^/   /'

  INSTALLED="$(grep -m1 -oE 'OnUnitActiveSec=[0-9]+min' \
    /etc/systemd/system/airline-update.timer 2>/dev/null | grep -oE '[0-9]+' || true)"
  WANTED="${AIRLINE_UPDATE_INTERVAL_MINUTES:-5}"
  if [[ -n "$INSTALLED" && "$INSTALLED" != "$WANTED" ]]; then
    bad "The installed timer says every ${INSTALLED} minutes, the settings ask for ${WANTED}."
    note "Rewrite it with: ./scripts/install-services.sh"
  fi

  STATE="$(systemctl show -p Result --value airline-update.service 2>/dev/null || true)"
  if [[ -n "$STATE" && "$STATE" != "success" ]]; then
    bad "The last run ended with: $STATE"
  fi
fi

heading "LAST ATTEMPTS"
STATUS_FILE="$REPO_ROOT/backups/auto-update.status"
if [[ -f "$STATUS_FILE" ]]; then
  tail -8 "$STATUS_FILE" | sed 's/^/   /'
else
  note "(no record yet - this file starts filling from the next check)"
fi
if command -v journalctl >/dev/null 2>&1; then
  note ""
  note "From the journal:"
  journalctl -u airline-update --no-pager -n 12 2>/dev/null \
    | grep -vE "^-- |Started|Finished|Succeeded" | tail -8 | sed 's/^/   /'
fi

heading "THIS CHECKOUT"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
note "Branch : $BRANCH"
note "Commit : $(git log --oneline -1 2>/dev/null)"

if git fetch --quiet origin "$BRANCH" 2>/dev/null; then
  BEHIND="$(git rev-list --count "HEAD..origin/$BRANCH" 2>/dev/null || echo '?')"
  AHEAD="$(git rev-list --count "origin/$BRANCH..HEAD" 2>/dev/null || echo '?')"
  if [[ "$BEHIND" == "0" && "$AHEAD" == "0" ]]; then
    good "Up to date with the remote - there is nothing to install."
  elif [[ "$AHEAD" != "0" ]]; then
    bad "$AHEAD commit(s) here that the remote does not have - the updater will not touch a"
    note "checkout that has diverged. A human has to sort that out."
  else
    note "$BEHIND commit(s) waiting:"
    git log --oneline "HEAD..origin/$BRANCH" 2>/dev/null | head -5 | sed 's/^/     /'
  fi
else
  bad "Could not reach the remote. Network, or credentials."
fi

heading "LOCAL EDITS"
# The one that catches everybody: a tracked file changed on the server stops
# the updater, because pulling over it would either fail or destroy the work.
# .env and game-settings.env are yours to edit and are handled separately.
DIRTY="$(git status --porcelain --untracked-files=no \
           -- . ':(exclude).env' ':(exclude)game-settings.env' 2>/dev/null)"
if [[ -n "$DIRTY" ]]; then
  bad "Tracked files edited here - THIS STOPS THE UPDATER:"
  echo "$DIRTY" | head -8 | sed 's/^/     /'
  note ""
  note "Undo one with:   git checkout -- <file>"
  note "Or keep it with: git stash"
else
  good "No local edits in the way"
fi
if ! git diff --quiet -- game-settings.env 2>/dev/null; then
  note "game-settings.env has your edits - that is fine, they are merged through updates."
fi

heading "ROOM TO WORK"
# A build needs a couple of gigabytes. Out of space looks like a mysterious
# failure rather than a full disk.
df -h "$REPO_ROOT" 2>/dev/null | sed 's/^/   /'
AVAIL_KB="$(df -Pk "$REPO_ROOT" 2>/dev/null | awk 'NR==2 {print $4}')"
if [[ -n "$AVAIL_KB" && "$AVAIL_KB" -lt 2000000 ]]; then
  bad "Under 2GB free - a build may fail for no visible reason."
  note "Old backups are in backups/; ./scripts/backup-db.sh keeps ${AIRLINE_BACKUP_KEEP:-14}."
fi

heading "SERVICES"
for unit in airline-web airline-sim; do
  if systemctl is-active "$unit" >/dev/null 2>&1; then
    good "$unit is running"
  else
    bad "$unit is NOT running: systemctl status $unit"
  fi
done

heading "IS THE GAME ACTUALLY MOVING"
# Services running is not the same as weeks passing - the night the driver
# changed, both were up and the game had been standing still for hours.
HEALTH_FILE="${AIRLINE_BACKUP_DIR:-$REPO_ROOT/backups}/health.status"
if [[ -f "$HEALTH_FILE" ]]; then
  HEALTH_LINE="$(cat "$HEALTH_FILE")"
  case "$HEALTH_LINE" in
    *"OK -"*)      good "$HEALTH_LINE" ;;
    *RESTARTED*)   bad  "$HEALTH_LINE" ;;
    *)             bad  "$HEALTH_LINE" ;;
  esac
else
  note "No check has run yet. Run ./scripts/watchdog.sh, or install the timer"
  note "with ./scripts/install-services.sh so it runs every 5 minutes."
fi
echo
