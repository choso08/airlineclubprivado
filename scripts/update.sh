#!/usr/bin/env bash
# Apply a code change with as little disruption as possible.
#
#   ./scripts/update.sh              fetch, build, restart what changed
#   ./scripts/update.sh --no-pull    build and restart local changes only
#   ./scripts/update.sh --web-only   only rebuild and restart the web site
#
# How the downtime works out:
#
#   The web site and the simulation are separate processes sharing a database.
#   Restarting the WEB SITE does not pause the game - the clock keeps running,
#   flights keep flying, money keeps moving. Players just get a dead page for
#   the ~15 seconds it takes to come back. Most fixes (pages, buttons, prices
#   shown on screen) only need this.
#
#   Restarting the SIMULATION is the disruptive one, so this script waits for
#   the current cycle to finish first. Killing it mid-cycle can leave a cycle
#   half-applied.
#
#   The new version is COMPILED BEFORE ANYTHING IS STOPPED, so a build that
#   fails costs zero downtime - you stay on the old version and nothing was
#   touched.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

PULL=1
WEB_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --no-pull)  PULL=0 ;;
    --web-only) WEB_ONLY=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

# systemd being available is not the same as the services being installed.
# Checking only the former made this fail with "Unit airline-web.service not
# found" for anyone who had not done the boot-time setup yet - after the build
# had already succeeded, leaving a perfectly good new version unstarted.
have_service() {
  command -v systemctl >/dev/null 2>&1 || return 1
  systemctl list-unit-files "$1.service" 2>/dev/null | grep -q "^$1.service" \
    || systemctl cat "$1.service" >/dev/null 2>&1
}

manual_restart_note() {
  local what="$1"
  echo "   The $what is not installed as a service, so it cannot be restarted"
  echo "   automatically. Stop it in its terminal window (Ctrl+C) and run:"
  echo "       ./scripts/$2"
  echo
  echo "   To have this handled for you in future, install the services:"
  echo "       see 'start on its own' in WINDOWS-SETUP.md"
}

# Whatever happens below, do not leave the restart announcement behind - a
# stale one would park every player under a countdown.
RESTART_FLAG="${AIRLINE_RESTART_FLAG:-/tmp/airline-restart-at}"
trap 'rm -f "$RESTART_FLAG"' EXIT

echo ">> [1/5] Backing up the database first"
"$REPO_ROOT/scripts/backup-db.sh"

# Remember where we were, so the changelog can say what this update brings.
PREVIOUS_COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo '')"

if [[ "$PULL" == "1" ]]; then
  echo ">> [2/5] Fetching changes"
  git -C "$REPO_ROOT" pull --ff-only
else
  echo ">> [2/5] Skipping pull"
fi

# Written before building so it is baked into what gets deployed.
"$REPO_ROOT/scripts/write-version.sh" "$PREVIOUS_COMMIT" || true

# Build while the old version is still serving. Nothing is stopped yet, so a
# compile error here is free.
echo ">> [3/5] Building (the running game is untouched until this succeeds)"
( cd "$REPO_ROOT/airline-data" && "$REPO_ROOT/scripts/sbt" publishLocal )
( cd "$REPO_ROOT/airline-web"  && "$REPO_ROOT/scripts/sbt" stage )

echo ">> [4/5] Restarting the web site (game clock keeps running throughout)"
WARN_SECONDS="${AIRLINE_UPDATE_WARNING_SECONDS:-30}"

# Only announce a restart we can actually perform. Warning players and then
# failing to restart is worse than not warning them.
if ! have_service airline-web; then
  echo
  manual_restart_note "web site" "run-web.sh"
  echo "   The new version is built and waiting - only the restart is missing."
  exit 1
fi

# Tell anyone currently playing. The browser polls /instance-status, shows a
# countdown, and reloads itself once the new instance answers - so nobody is
# left staring at a page that has quietly stopped working.
if [[ "$WARN_SECONDS" -gt 0 ]]; then
  echo "   Warning players, restarting in ${WARN_SECONDS}s..."
  echo $(( ($(date +%s) + WARN_SECONDS) * 1000 )) > "$RESTART_FLAG"
  sleep "$WARN_SECONDS"
fi

sudo systemctl restart airline-web

# The new process reports a different start time, which is what makes the
# browsers reload; the flag has done its job. (The EXIT trap covers the
# failure paths.)
rm -f "$RESTART_FLAG"

if [[ "$WEB_ONLY" == "1" ]]; then
  echo ">> [5/5] --web-only: leaving the simulation alone. Done."
  exit 0
fi

echo ">> [5/5] Restarting the simulation, waiting for the current cycle to end"
if ! have_service airline-sim; then
  manual_restart_note "simulation" "run-simulation.sh"
  exit 1
fi
# Wait for a cycle boundary so we do not kill one halfway through.
#
# How long to wait has to come from the cycle length, not from a fixed number.
# A flat five minutes was fine at the default pace and quietly wrong the moment
# anyone slowed the game down: with ten minute cycles the wait would expire
# before the cycle ended, every time, and the restart it was meant to avoid
# would happen anyway - having first made you wait five minutes for it.
#
# Two full cycles plus a minute. One would be enough if we always arrived at
# the start of one; we do not, so the worst case is nearly a whole cycle of
# waiting before the one we are watching for even begins.
CYCLE_SECONDS="${AIRLINE_CYCLE_SECONDS:-1800}"
WAIT_SECONDS=$(( CYCLE_SECONDS * 2 + 60 ))
POLLS=$(( WAIT_SECONDS / 5 ))

LAST="$(journalctl -u airline-sim -n 200 --no-pager 2>/dev/null | grep -c 'spent .* secs' || echo 0)"
printf '   cycles are %ds, so waiting up to %d minutes for one to complete...\n' \
  "$CYCLE_SECONDS" "$(( WAIT_SECONDS / 60 ))"
for _ in $(seq 1 "$POLLS"); do
  NOW="$(journalctl -u airline-sim -n 200 --no-pager 2>/dev/null | grep -c 'spent .* secs' || echo 0)"
  if [[ "$NOW" -gt "$LAST" ]]; then
    echo "   cycle finished - restarting now"
    break
  fi
  sleep 5
done
sudo systemctl restart airline-sim

echo
echo ">> Done. Check both halves are healthy:"
echo "     systemctl status airline-web airline-sim"
echo "   If something broke, the backup from step 1 is in backups/."
