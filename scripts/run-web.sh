#!/usr/bin/env bash
# The Play web front-end. Listens on 0.0.0.0:9000 so that it is reachable
# over the Tailscale interface; keep the port closed on your public NIC.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

PORT="${AIRLINE_WEB_PORT:-9000}"
echo ">> Starting web front-end on port ${PORT}"
sbt_run airline-web "run ${PORT}"
