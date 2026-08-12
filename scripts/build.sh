#!/usr/bin/env bash
# Compiles airline-data and publishes it to the local Ivy cache.
# airline-web depends on it as a library, so this must run first.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

echo ">> Building airline-data and publishing locally"
sbt_run airline-data publishLocal
