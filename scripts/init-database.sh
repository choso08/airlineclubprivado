#!/usr/bin/env bash
# One-time (destructive) world generation: creates the schema and loads
# cities, airports, runways, airplane models and transit data.
#
# Takes a while - it streams ~80MB of CSV and computes the city -> airport
# catchment for every city on earth. Safe to re-run; it truncates first.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

echo ">> Initialising world data into ${AIRLINE_DB_SCHEMA:-airline_v2_1} at ${AIRLINE_DB_HOST:-localhost:3306}"
echo ">> This is destructive: existing airports/cities/countries are replaced."
sbt_run airline-data "runMain com.patson.init.MainInit"
