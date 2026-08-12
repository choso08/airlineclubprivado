#!/usr/bin/env bash
# Sourced by the other scripts: locates the repo, loads .env, exports the
# variables the application.conf files read via ${?VAR} substitution.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export REPO_ROOT

# Load .env WITHOUT clobbering variables that are already set, so a one-off
#   AIRLINE_WEB_PORT=9001 ./scripts/run-web.sh
# actually takes effect. Plain `set -a; source .env` would silently overwrite
# it with the file's value.
load_env_file() {
  local file="$1" line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"                      # tolerate CRLF files
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    [[ "$line" != *=* ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"      # trim leading space
    key="${key%"${key##*[![:space:]]}"}"      # trim trailing space
    key="${key#export }"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    # Strip one layer of surrounding quotes, if present.
    if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    # Already set in the environment? Leave it alone.
    if [[ -z "${!key+x}" ]]; then
      export "$key=$value"
    fi
  done < "$file"
}

# .env first (secrets, machine-specific), then game-settings.env (how the
# game plays, tracked in git). Because load_env_file never overwrites an
# existing value, .env wins on any key set in both, and anything you export
# on the command line wins over both.
if [[ -f "$REPO_ROOT/.env" ]]; then
  load_env_file "$REPO_ROOT/.env"
else
  echo "WARNING: no .env found at $REPO_ROOT/.env - falling back to defaults" >&2
  echo "         cp .env.example .env and edit it." >&2
fi

if [[ -f "$REPO_ROOT/game-settings.env" ]]; then
  load_env_file "$REPO_ROOT/game-settings.env"
fi

sbt_run() {
  # $1 = subproject directory, rest = sbt commands
  local project_dir="$1"; shift
  cd "$REPO_ROOT/$project_dir"
  exec "$REPO_ROOT/scripts/sbt" "$@"
}
