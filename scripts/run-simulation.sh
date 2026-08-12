#!/usr/bin/env bash
# The background simulation loop: advances game cycles, moves passengers,
# pays out income. Must be running for the game to progress.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

echo ">> Starting background simulation (MainSimulation)"
sbt_run airline-data "runMain com.patson.MainSimulation"
