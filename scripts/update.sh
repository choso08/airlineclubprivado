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

have_systemd() { command -v systemctl >/dev/null 2>&1 && systemctl list-units >/dev/null 2>&1; }

echo ">> [1/5] Backing up the database first"
"$REPO_ROOT/scripts/backup-db.sh"

if [[ "$PULL" == "1" ]]; then
  echo ">> [2/5] Fetching changes"
  git -C "$REPO_ROOT" pull --ff-only
else
  echo ">> [2/5] Skipping pull"
fi

# Build while the old version is still serving. Nothing is stopped yet, so a
# compile error here is free.
echo ">> [3/5] Building (the running game is untouched until this succeeds)"
( cd "$REPO_ROOT/airline-data" && "$REPO_ROOT/scripts/sbt" publishLocal )
( cd "$REPO_ROOT/airline-web"  && "$REPO_ROOT/scripts/sbt" stage )

echo ">> [4/5] Restarting the web site (~15s of dead pages, game keeps running)"
if have_systemd; then
  sudo systemctl restart airline-web
else
  echo "   No systemd - restart ./scripts/run-web.sh by hand."
fi

if [[ "$WEB_ONLY" == "1" ]]; then
  echo ">> [5/5] --web-only: leaving the simulation alone. Done."
  exit 0
fi

echo ">> [5/5] Restarting the simulation, waiting for the current cycle to end"
if have_systemd; then
  # Wait for a cycle boundary so we do not kill one halfway through. Cycles
  # take 1-3 minutes, so give it generous headroom before going anyway.
  LAST="$(journalctl -u airline-sim -n 200 --no-pager 2>/dev/null | grep -c 'spent .* secs' || echo 0)"
  echo "   waiting up to 5 minutes for a cycle to complete..."
  for _ in $(seq 1 60); do
    NOW="$(journalctl -u airline-sim -n 200 --no-pager 2>/dev/null | grep -c 'spent .* secs' || echo 0)"
    if [[ "$NOW" -gt "$LAST" ]]; then
      echo "   cycle finished - restarting now"
      break
    fi
    sleep 5
  done
  sudo systemctl restart airline-sim
else
  echo "   No systemd - restart ./scripts/run-simulation.sh by hand,"
  echo "   ideally just after a 'cycle N spent X secs' line appears."
fi

echo
echo ">> Done. Check both halves are healthy:"
echo "     systemctl status airline-web airline-sim"
echo "   If something broke, the backup from step 1 is in backups/."
