#!/usr/bin/env bash
# Sourced by the other scripts: locates the repo, loads .env, exports the
# variables the application.conf files read via ${?VAR} substitution.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export REPO_ROOT

if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
else
  echo "WARNING: no .env found at $REPO_ROOT/.env - falling back to defaults" >&2
  echo "         cp .env.example .env and edit it." >&2
fi

sbt_run() {
  # $1 = subproject directory, rest = sbt commands
  local project_dir="$1"; shift
  cd "$REPO_ROOT/$project_dir"
  exec "$REPO_ROOT/scripts/sbt" "$@"
}
