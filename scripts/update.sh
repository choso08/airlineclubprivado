#!/usr/bin/env bash
# Apply a code change with as little disruption as possible.
#
#   ./scripts/update.sh              fetch, build, restart what changed
#   ./scripts/update.sh --no-pull    build and restart local changes only
#   ./scripts/update.sh --web-only   only rebuild and restart the web site
#   ./scripts/update.sh --now        do not wait for a cycle to finish
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
SKIP_WAIT=0
for arg in "$@"; do
  case "$arg" in
    --no-pull)  PULL=0 ;;
    --web-only) WEB_ONLY=1 ;;
    --now)      SKIP_WAIT=1 ;;
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

  # game-settings.env is tracked AND yours to edit, and git will not pull over
  # a file you have changed. That is how the automatic updater came to fail
  # silently every ten minutes: the settings file changes in most releases, and
  # one edited line here was enough to stop everything.
  #
  # So your version is set aside, the file is put back to what was shipped, the
  # pull runs, and your choices are put back on top afterwards - compared
  # against what was shipped BEFORE, so a value you never touched follows the
  # release and one you did stays yours.
  SETTINGS="$REPO_ROOT/game-settings.env"
  SAVED=0
  if [[ -f "$SETTINGS" ]] && ! git -C "$REPO_ROOT" diff --quiet -- game-settings.env 2>/dev/null; then
    cp "$SETTINGS" "$SETTINGS.yours"
    git -C "$REPO_ROOT" show HEAD:game-settings.env > "$SETTINGS.base" 2>/dev/null || true
    git -C "$REPO_ROOT" checkout -- game-settings.env
    SAVED=1
  fi

  git -C "$REPO_ROOT" pull --ff-only

  if [[ "$SAVED" == "1" ]]; then
    "$REPO_ROOT/scripts/merge-settings.sh" "$SETTINGS" "$SETTINGS.yours" "$SETTINGS.base" || true
    rm -f "$SETTINGS.yours" "$SETTINGS.base"
  fi
else
  echo ">> [2/5] Skipping pull"
fi

# Written before building so it is baked into what gets deployed.
"$REPO_ROOT/scripts/write-version.sh" "$PREVIOUS_COMMIT" || true

# Build while the old version is still serving. Nothing is stopped yet, so a
# compile error here is free.
echo ">> [3/5] Building (the running game is untouched until this succeeds)"
# One build, so staging the web site compiles the simulation with it - there
# is no longer a publish step in between that could deliver a stale copy.
( cd "$REPO_ROOT" && "$REPO_ROOT/scripts/sbt" airlineWeb/stage )

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

echo ">> [5/5] Restarting the simulation"
if ! have_service airline-sim; then
  manual_restart_note "simulation" "run-simulation.sh"
  exit 1
fi

# Killing the simulation part way through a cycle can leave that cycle half
# applied, so we do not do it. But the previous version of this waited for the
# NEXT cycle to finish, which had it backwards: a cycle takes under a minute to
# compute and then the simulation sits idle until the next one is due. At ten
# minute cycles it is idle roughly nine tenths of the time, so the honest
# answer is almost always "restart it now" - and instead you were made to wait
# for a boundary that had just gone past.
#
# So look at what it is doing rather than at the clock. The log says
# "cycle N starting!" when one begins and "cycle N spent X secs" when it ends,
# so whichever came last tells us whether anything is in flight.
sim_is_busy() {
  local last
  last="$(journalctl -u airline-sim -n 300 --no-pager 2>/dev/null \
          | grep -oE 'cycle [0-9]+ starting!|cycle [0-9]+ spent [0-9]+ secs' | tail -1)"
  [[ "$last" == *starting!* ]]
}

if [[ "$SKIP_WAIT" == "1" ]]; then
  echo "   --now given: restarting without waiting"
elif ! sim_is_busy; then
  echo "   no cycle in progress - restarting straight away"
else
  # A cycle IS running. Wait for this one only; it is a matter of seconds.
  # The allowance is generous because a slow machine mid-cycle is not a fault.
  echo "   a cycle is in progress - waiting for it to finish (usually under a minute)"
  DEADLINE=$(( SECONDS + 600 ))
  while sim_is_busy; do
    if [[ "$SECONDS" -gt "$DEADLINE" ]]; then
      echo "   still going after 10 minutes - restarting anyway"
      break
    fi
    sleep 5
  done
fi
sudo systemctl restart airline-sim

# The update timer is written by install-services.sh, which this does not run -
# so a change to how often the game checks for updates sits in the settings
# file doing nothing until somebody notices. Say so rather than let it be
# quietly ignored.
WANTED_INTERVAL="${AIRLINE_UPDATE_INTERVAL_MINUTES:-5}"
INSTALLED_INTERVAL="$(grep -m1 -oE 'OnUnitActiveSec=[0-9]+min' \
  /etc/systemd/system/airline-update.timer 2>/dev/null | grep -oE '[0-9]+' || true)"
if [[ -n "$INSTALLED_INTERVAL" && "$INSTALLED_INTERVAL" != "$WANTED_INTERVAL" ]]; then
  echo
  echo ">> Update checks are still every ${INSTALLED_INTERVAL} minutes, but the settings"
  echo "   file asks for ${WANTED_INTERVAL}. Rewrite the timer with:"
  echo "     ./scripts/install-services.sh"
fi

echo ">> Done. Check both halves are healthy:"
echo "     systemctl status airline-web airline-sim"
echo "   If something broke, the backup from step 1 is in backups/."
