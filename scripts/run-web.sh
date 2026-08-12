#!/usr/bin/env bash
# The Play web front-end.
#
#   ./scripts/run-web.sh          production build (default)
#   ./scripts/run-web.sh --dev    sbt dev mode, recompiles on each request
#
# Production is the default on purpose. Play's dev mode (`sbt run`) exits as
# soon as its stdin closes, so it dies the moment you close the terminal or
# run it under a service manager - useless for an instance friends rely on.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

PORT="${AIRLINE_WEB_PORT:-9000}"

if [[ "${1:-}" == "--dev" ]]; then
  echo ">> Starting web front-end in DEV mode on port ${PORT} (dies when stdin closes)"
  sbt_run airline-web "run ${PORT}"
fi

STAGE_DIR="$REPO_ROOT/airline-web/target/universal/stage"
if [[ ! -x "$STAGE_DIR/bin/airline-web" ]]; then
  echo ">> No production build found, running 'sbt stage' first..."
  ( cd "$REPO_ROOT/airline-web" && "$REPO_ROOT/scripts/sbt" stage )
fi

if [[ "${AIRLINE_APP_SECRET:-changeme}" == "changeme" ]]; then
  echo "WARNING: AIRLINE_APP_SECRET is unset or still the default." >&2
  echo "         Anyone who knows it can forge a session for any account." >&2
  echo "         Generate one with: openssl rand -base64 48" >&2
fi

echo ">> Starting web front-end on port ${PORT}"
# Binds 0.0.0.0 so the Tailscale interface can reach it. Keep the port closed
# on your public NIC - see SELF-HOSTING.md.
exec "$STAGE_DIR/bin/airline-web" \
  -Dhttp.port="${PORT}" \
  -Dpidfile.path=/dev/null
