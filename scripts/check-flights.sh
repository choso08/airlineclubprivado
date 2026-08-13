#!/usr/bin/env bash
# Explain why a flight does or does not appear on an airport's departure board.
#
#   ./scripts/check-flights.sh LIS            everything at Lisbon
#   ./scripts/check-flights.sh LIS TAP        focus on one airline
#
# Two different things make a route invisible there, and they look identical:
#
#   1. It flies nothing. A route's frequency is not what you typed - the game
#      recomputes it from the aircraft assigned to it, counting only the ones
#      already DELIVERED. No aircraft, or none delivered yet, means no flights
#      at all, on any day, for ever. This is the one worth acting on.
#   2. It flies, but not now. The board lists only the next 24 HOURS and stops
#      after 90 rows. A route with one or two flights a week is invisible most
#      of the time and there is nothing wrong with it.
#
# So this works out the real weekly timetable of every route, using the game's
# own scheduling rule, and says which of the two you are looking at.
#
# Verified against the running game rather than derived from the source alone:
# the times below match what /airports/<id>/departures returns.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

AIRPORT="${1:-}"
AIRLINE_FILTER="${2:-}"

if [[ -z "$AIRPORT" ]]; then
  echo "Usage: ./scripts/check-flights.sh <IATA> [airline name]" >&2
  echo "   eg: ./scripts/check-flights.sh LIS" >&2
  echo "       ./scripts/check-flights.sh LIS TAP" >&2
  exit 1
fi

AIRPORT="$(echo "$AIRPORT" | tr '[:lower:]' '[:upper:]')"

db() {
  mariadb --skip-column-names -u "$AIRLINE_DB_USER" \
    ${AIRLINE_DB_PASSWORD:+-p"$AIRLINE_DB_PASSWORD"} \
    "$AIRLINE_DB_SCHEMA" -e "$1"
}

AIRPORT_ROW="$(db "SELECT id, name, airport_size FROM airport WHERE iata = '$AIRPORT' LIMIT 1;")"
if [[ -z "$AIRPORT_ROW" ]]; then
  echo "No airport with IATA code $AIRPORT." >&2
  exit 1
fi
AIRPORT_ID="$(echo "$AIRPORT_ROW" | cut -f1)"
AIRPORT_NAME="$(echo "$AIRPORT_ROW" | cut -f2)"
AIRPORT_SIZE="$(echo "$AIRPORT_ROW" | cut -f3)"

echo "== $AIRPORT - $AIRPORT_NAME =="
echo

# Both directions, because the board mixes departures with arrivals - the same
# way the game builds it. transport_type = 0 is FLIGHT; 1 is the world's own
# ground transit, which never appears on a board.
#
# The frequency column on the route is NOT what the game flies. Every time a
# route is loaded, the game recomputes its frequency from the aircraft actually
# assigned to it, counting only those that are READY - not sold, and past their
# delivery cycle. A route whose aircraft are still being built therefore has a
# frequency of zero however the column reads, and the same is true of a route
# with no aircraft at all. So this works out the frequency the same way, and
# reports the stored one beside it when they disagree.
CYCLE="$(db "SELECT cycle FROM cycle LIMIT 1;")"
CYCLE="${CYCLE:-0}"

ASSIGNMENT_SQL="
    COALESCE(SUM(CASE WHEN p.is_sold = 0 AND p.constructed_cycle <= $CYCLE
                      THEN la.frequency ELSE 0 END), 0),
    COUNT(la.airplane),
    COALESCE(SUM(CASE WHEN p.is_sold = 0 AND p.constructed_cycle <= $CYCLE
                      THEN 1 ELSE 0 END), 0)"

ROWS="$(db "
  SELECT l.id, a.id, a.name, ao.iata, l.distance, $ASSIGNMENT_SQL, 'departs', l.frequency
  FROM link l
    JOIN airline a ON a.id = l.airline
    JOIN airport ao ON ao.id = l.to_airport
    LEFT JOIN link_assignment la ON la.link = l.id
    LEFT JOIN airplane p ON p.id = la.airplane
  WHERE l.from_airport = $AIRPORT_ID AND l.transport_type = 0
  GROUP BY l.id, a.id, a.name, ao.iata, l.distance, l.frequency
  UNION ALL
  SELECT l.id, a.id, a.name, ao.iata, l.distance, $ASSIGNMENT_SQL, 'arrives', l.frequency
  FROM link l
    JOIN airline a ON a.id = l.airline
    JOIN airport ao ON ao.id = l.from_airport
    LEFT JOIN link_assignment la ON la.link = l.id
    LEFT JOIN airplane p ON p.id = la.airplane
  WHERE l.to_airport = $AIRPORT_ID AND l.transport_type = 0
  GROUP BY l.id, a.id, a.name, ao.iata, l.distance, l.frequency;")"

if [[ -z "$ROWS" ]]; then
  echo "No flights at all touch this airport yet."
  echo "(Ground transit does not count - the board only ever shows flights.)"
  exit 0
fi

# Everything below reproduces com.patson.model.Scheduling.getLinkSchedule and
# the filtering in Application.getDepartures. Kept deliberately close to the
# Scala so the two can be compared line by line:
#
#   a major airport (size 5 or more) can take off around the clock: 24 hours
#   x 12 five-minute slots x 7 days = 2016 places in the week. A smaller one
#   is limited to 06:00-22:55, which is 17 hours, so 1428 places.
#   offset   = (distance + airline id) % slots
#   interval = slots / frequency
#   flight i departs at slot (offset + i * interval) % slots
#
# Both directions use THIS airport's size: the board swaps the ends of an
# arriving route before scheduling it, so the departure end is always here.
NOW_DAY="$(date +%w)"     # 0 = Sunday, same as the browser's getDay()
NOW_HOUR="$(date +%-H)"
NOW_MIN="$(date +%-M)"

echo "$ROWS" | awk -F'\t' \
  -v filter="$AIRLINE_FILTER" \
  -v nowDay="$NOW_DAY" -v nowHour="$NOW_HOUR" -v nowMin="$NOW_MIN" \
  -v airport="$AIRPORT" -v cycle="$CYCLE" -v airportSize="$AIRPORT_SIZE" '
function slotDay(i)   { return int(i / PER_DAY) }
function slotHour(i)  { return FROM_HOUR + int((i % PER_DAY) / 12) }
function slotMin(i)   { return (i % 12) * 5 }
function dayName(d)   { return substr("SunMonTueWedThuFriSat", d * 3 + 1, 3) }

BEGIN {
  # Airport.MAJOR_AIRPORT_LOWER_THRESHOLD
  if (airportSize >= 5) { FROM_HOUR = 0; HOURS = 24 } else { FROM_HOUR = 6; HOURS = 17 }
  PER_DAY = HOURS * 12
  SLOTS = PER_DAY * 7
  nowTotal = nowDay * 1440 + nowHour * 60 + nowMin
  BOARD_ROWS = 90          # three boards of thirty
  matched = 0
  lowerFilter = tolower(filter)
}

{
  linkId = $1; airlineId = $2; airline = $3; other = $4
  distance = $5; frequency = $6; assigned = $7; ready = $8
  direction = $9; storedFrequency = $10

  routes++
  if (frequency <= 0) {
    # Say WHICH of the two ways a route ends up flying nothing, because they
    # need different things done about them.
    if (assigned == 0) {
      why = "no aircraft assigned to it"
    } else if (ready == 0) {
      why = sprintf("%d aircraft assigned, none delivered yet", assigned)
    } else {
      why = "aircraft assigned but zero frequency"
    }
    zeroFreq[++zeroCount] = sprintf("  %-24s %s %-4s  %s", airline, direction, other, why)
    if (lowerFilter != "" && index(tolower(airline), lowerFilter)) matchedZero++
    next
  }
  if (storedFrequency != frequency) {
    stale[++staleCount] = sprintf("  %-24s %s %-4s  flies %d/week, stored as %d", airline, direction, other, frequency, storedFrequency)
  }

  isMine = (lowerFilter != "" && index(tolower(airline), lowerFilter)) ? 1 : 0
  if (isMine) matched++

  offset = (distance + airlineId) % SLOTS
  interval = int(SLOTS / frequency)

  for (i = 0; i < frequency; i++) {
    slot = (offset + i * interval) % SLOTS
    d = slotDay(slot); h = slotHour(slot); m = slotMin(slot)
    total = d * 1440 + h * 60 + m

    # The board wraps the week only when asked for a Saturday, exactly as the
    # game does - reproduced rather than corrected, so the numbers match what
    # a player actually sees.
    wrapped = (nowDay == 6 && d == 0) ? total + 7 * 1440 : total

    n = ++flights
    fDay[n] = d; fHour[n] = h; fMin[n] = m
    fAirline[n] = airline; fOther[n] = other; fDir[n] = direction
    fMine[n] = isMine; fWrapped[n] = wrapped

    if (wrapped >= nowTotal && wrapped <= nowTotal + 1440) {
      inWindow[++windowCount] = n
    }
    if (isMine) {
      mineList[++mineCount] = n
    }
  }
}

END {
  printf "%d routes, %d flights a week in total (cycle %d).\n", routes, flights, cycle
  if (zeroCount > 0) {
    printf "\n%d route(s) fly nothing:\n", zeroCount
    for (i = 1; i <= zeroCount; i++) print zeroFreq[i]
    print "  -> a route only flies as often as the aircraft ON it can manage."
    print "     With none assigned, or none delivered yet, it flies nothing -"
    print "     and it will never appear on any departure board."
  }
  if (staleCount > 0) {
    printf "\n%d route(s) whose stored frequency is out of date:\n", staleCount
    for (i = 1; i <= staleCount; i++) print stale[i]
    print "  -> harmless. The game recomputes it from the aircraft on load;"
    print "     the timetable below uses the recomputed one, as the game does."
  }

  if (filter != "" && matched == 0 && matchedZero == 0) {
    printf "\nNo airline matching \"%s\" flies to or from %s at all.\n", filter, airport
    exit 0
  }

  # ---------------------------------------------------------------- timetable
  if (mineCount > 0) {
    printf "\nWeekly timetable for \"%s\" at %s:\n\n", filter, airport
    # simple insertion sort by time - a handful of rows, clarity beats speed
    for (i = 1; i <= mineCount; i++) order[i] = mineList[i]
    for (i = 2; i <= mineCount; i++) {
      key = order[i]; j = i - 1
      while (j >= 1 && (fDay[order[j]] * 1440 + fHour[order[j]] * 60 + fMin[order[j]]) > \
                       (fDay[key] * 1440 + fHour[key] * 60 + fMin[key])) {
        order[j + 1] = order[j]; j--
      }
      order[j + 1] = key
    }
    for (i = 1; i <= mineCount; i++) {
      n = order[i]
      printf "  %s %02d:%02d   %s %s   (%s)\n", dayName(fDay[n]), fHour[n], fMin[n], fDir[n], fOther[n], fAirline[n]
    }
  }

  # ------------------------------------------------------- what the board shows
  printf "\nRight now it is %s %02d:%02d, so the board covers the next 24 hours.\n", dayName(nowDay), nowHour, nowMin
  printf "%d flights fall in that window", windowCount
  if (windowCount > BOARD_ROWS) {
    printf ", but the board only draws %d rows,\nso it stops at ", BOARD_ROWS
  } else {
    printf ", which fits in the %d rows the board draws.\n", BOARD_ROWS
  }

  # rank the window by time, so we can say who is drawn and who is cut off
  for (i = 1; i <= windowCount; i++) w[i] = inWindow[i]
  for (i = 2; i <= windowCount; i++) {
    key = w[i]; j = i - 1
    while (j >= 1 && fWrapped[w[j]] > fWrapped[key]) { w[j + 1] = w[j]; j-- }
    w[j + 1] = key
  }
  if (windowCount > BOARD_ROWS) {
    last = w[BOARD_ROWS]
    printf "%s %02d:%02d.\n", dayName(fDay[last]), fHour[last], fMin[last]
  }

  if (filter == "") {
    print "\nRun it again with an airline name to follow one in particular:"
    printf "  ./scripts/check-flights.sh %s \"TAP\"\n", airport
    exit 0
  }

  # ------------------------------------------------------------------ verdict
  shown = 0; cut = 0
  for (i = 1; i <= windowCount && i <= BOARD_ROWS; i++) if (fMine[w[i]]) shown++
  for (i = BOARD_ROWS + 1; i <= windowCount; i++) if (fMine[w[i]]) cut++

  print ""
  if (mineCount == 0) {
    printf "VERDICT: \"%s\" has routes here but none of them fly.\n", filter
  } else if (shown > 0) {
    printf "VERDICT: %d of their flights %s on the board right now. Nothing is wrong.\n", shown, (shown == 1 ? "is" : "are")
  } else if (cut > 0) {
    printf "VERDICT: %d of their flights are inside the next 24 hours, but they fall\n", cut
    printf "         past row %d, so the board runs out of space before reaching them.\n", BOARD_ROWS
    print   "         Nothing is broken - the airport is simply busier than the board."
  } else {
    printf "VERDICT: none of their %d weekly flights depart in the next 24 hours.\n", mineCount
    print   "         Look at the timetable above and check back at one of those times,"
    print   "         or have them raise the frequency on the route."
  }
}'
