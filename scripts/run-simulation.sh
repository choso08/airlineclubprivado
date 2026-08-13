#!/usr/bin/env bash
# The background simulation loop: advances game cycles, moves passengers,
# pays out income. Must be running for the game to progress.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

echo ">> Starting background simulation (MainSimulation)"

# Without this the simulation has no logging backend and every error it hits is
# discarded before it reaches journalctl. See airline-data/logback-simulation.xml.
export AIRLINE_SBT_JVM_OPTS="${AIRLINE_SBT_JVM_OPTS:-} \
  -Dlogback.configurationFile=$REPO_ROOT/airline-data/logback-simulation.xml \
  -Dpekko.loggers.0=org.apache.pekko.event.slf4j.Slf4jLogger \
  -Dpekko.logging-filter=org.apache.pekko.event.slf4j.Slf4jLoggingFilter"

sbt_run airline-data "runMain com.patson.MainSimulation"
