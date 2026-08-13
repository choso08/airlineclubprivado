# Modernising the backend

Notes for making the simulation faster and safer to change. Everything here is
measured on this instance, not guessed — the numbers come from the profiler
built into the cycle, so they can be re-checked after every change.

---

## Where the time actually goes

`CycleProfiler` times each phase of a cycle and prints a breakdown after every
tick. From a real cycle on a 4-core machine, 3 820 airports, world freshly
generated:

```
--- cycle 50 phase breakdown (73.301s total) ---
    links + passengers    42.2s   57.6%  #############################
    load airports         18.2s   24.8%  ############
    airports              11.8s   16.0%  ########
    (everything else)      0.7s    0.9%
    airlines               0.2s    0.3%
    airplane models        0.1s    0.1%
    users                  0.1s    0.1%
    alliances              0.0s    0.0%
    events                 0.0s    0.0%
    airplanes              0.0s    0.0%
    countries              0.0s    0.0%
    airport assets         0.0s    0.0%
    purge history          0.0s    0.0%
    purge logs             0.0s    0.0%
```

**Three phases are 98.4% of the cycle.** The other eleven, together, are under
one second. That settles what is worth touching and what is not: optimising
anything outside the top three cannot win more than 1% however well it is done.

See it yourself after any change:

```bash
journalctl -u airline-sim -n 200 | grep -A 16 "phase breakdown"
```

Set `AIRLINE_PROFILE_CYCLES=off` to disable, though the cost is a dozen
`currentTimeMillis` calls per cycle, so there is little reason to.

---

## The three that matter

### 1. `load airports` — 18.2s, 24.8%

The most suspicious number here, and probably the easiest win.

`AirportSource.loadAllAirports(true)` pulls all 3 820 airports with their
full detail out of MySQL at the start of every cycle. Airport rows barely
change between cycles — population, income and runways are set at world
generation and only move when someone builds something.

Worth investigating, roughly in order of effort:

- **Load once, invalidate on change.** The cache is already there
  (`AirportCache`), it is just flushed wholesale by `invalidateCaches()` on
  every cycle. Being selective about that is a small change with a large
  payoff.
- **Load less.** `fullLoad = true` fetches features, runways and city shares
  for every airport. Check what the cycle genuinely reads.
- **Batch the queries.** Worth confirming this is not N+1 across 3 820 rows,
  which at 18 seconds it very much smells like.

### 2. `links + passengers` — 42.2s, 57.6%

The real simulation: `LinkSimulation.linkSimulation`, which builds passenger
demand and routes everyone. This is the part that genuinely has work to do,
so expect to win less here per unit of effort than on airport loading.

- It already uses parallel collections in places. Check whether the whole
  thing scales across cores or whether a serial section dominates — on a
  2-core i3 that distinction matters a lot.
- Demand is computed between airport pairs; the search space grows with the
  square of the airports in play.

### 3. `airports` — 11.8s, 16.0%

`AirportSimulation.airportSimulation` — loyalty, champions, per-airport stats.

---

## What is already modern

Worth stating, because the upstream README is misleading about it. It says
JDK 8 and describes `activator`, neither of which is true any more:

| | Version |
|---|---|
| Scala | 2.13.14 |
| Play | 3.0.5 |
| Pekko | 1.0.3 (already migrated off Akka) |
| sbt | 1.9.9 |
| JDK | runs on 21 |

The genuinely dated pieces:

- **`mysql-connector-java` 5.1.49**, from 2019, and the legacy
  `com.mysql.jdbc.Driver` class name. This is what forces MySQL 5.7 rather
  than 8.x. Moving to `mysql-connector-j` 8.x would lift that, but it is not
  a drop-in: the driver class name changes and the auth plugin behaviour
  differs.
- **Hand-written JDBC** throughout `com.patson.data`, with string-concatenated
  SQL and manual `ResultSet` walking.
- **`play-json` 2.7.4** in airline-data while airline-web is on Play 3.

---

## Tests

There are 23 spec files, contrary to first impressions — the suite is real:

```
Total number of tests run: 198
Tests: succeeded 187, failed 11
```

**At least some of the failures are inherited, not introduced here.** A
pristine upstream clone at the same commit was run through the same suite and
fails `AviationHubSimulationSpec` "computeUpdatingAirports should accurately
update paxByAirport" identically — off by exactly one passenger per airport in
both.

That comparison run did not finish, so the remaining ten were not matched up
one by one. Worth completing before assuming they are all upstream's:

```bash
git -c advice.detachedHead=false clone --depth 1 https://github.com/patsonluk/airline /tmp/upstream
cd /tmp/upstream/airline-data && sbt test
```

None of them touch the code changed here — the changes are configuration
lookups with upstream values as defaults, and the failing specs assert on game
maths that was not modified.

Most look like assertions on tuned game constants that have since drifted, eg
`0.6045561434450323 was not less than 0.6`. They are worth fixing before
relying on the suite as a gate, since a suite that is always red teaches
everyone to ignore it.

```bash
cd airline-data && ../scripts/sbt test
```

---

## Done so far

### Connection validation — cycle 73s to ~60s, airport loading 17.3s to ~11s

`Meta` set `testConnectionOnCheckout(true)`, which runs a validation round trip
to MySQL before handing over *every* connection. The simulation asks for
connections constantly - loading airports alone takes several per airport, so
tens of thousands per cycle - and each was paying for that probe.

c3p0's own documentation calls checkout testing its most expensive option and
recommends idle testing instead: connections are validated in the background
while unused, so a checkout costs nothing. Cover for a connection dying between
tests is still there via `acquireRetryAttempts` and `autoReconnect=true` in the
JDBC URL.

Measured on the same world, warm cycles only (the first cycle after a restart
is always slower - cold JIT, cold database cache - so it is excluded):

|                    | before | after        |
|--------------------|--------|--------------|
| whole cycle        | 71.8s  | 57.9s, 62.6s |
| load airports      | 17.3s  | 11.6s, 10.9s |
| .. of which assets | 10.7s  | 6.9s, 6.4s   |
| links + passengers | 40.9s  | 36.2s, 41.7s |

Airport loading is reliably a third faster. The whole-cycle figure moves around
by several seconds between cycles - `links + passengers` varies with what the
world is doing - so treat it as "roughly 60s, was roughly 72s" rather than a
precise number.

### Two dead queries removed

`loadAirportsByQueryString` ran a `SELECT` on `airline_appeal` whose loop body
was commented out, and one on `airport_image` whose two uses were also
commented out. Both read rows and discarded them, once per airport - about
7,600 wasted round trips per cycle.

Worth recording that this barely moved the clock: 18.2s to 18.0s. The queries
were cheap; the cost was in the *checkout* before each one, which is why the
pool change mattered so much more. A good reminder that the profiler, not
intuition, decides what to work on next.

### Asset loading batched — 6.4s to 2.7s

`loadAirportAssetsByAirport` was called once per airport and each call cost
three connection checkouts and three queries: one for the blueprints, one
inside `CycleSource.loadCycle()`, one for the built assets. Across 3 800
airports that is over ten thousand round trips for data that fits in three.

`loadAirportAssetsByAirports` now loads the lot in one pass, and the cycle
number is passed in rather than re-queried — it cannot change while a cycle is
being computed.

The per-airport call sat in the middle of the airport loop, so the loading had
to move to after it. That is safe here: `initAssets` only populates fields, and
nothing between that point and the end of the loop reads them — in particular
the appeal computation does not.

Verified before trusting it, over 400 airports: 601 assets loaded by the old
path, 601 by the new, and the blueprint ids match airport for airport with
zero mismatches.

### Where it stands

Cumulatively, on the same world:

|                    | baseline | now   |
|--------------------|----------|-------|
| whole cycle        | 73.3s    | 58.5s |
| load airports      | 18.2s    | 7.9s  |
| .. of which assets | 10.7s    | 2.7s  |

Airport loading is down 57%, assets 75%. It is no longer the second most
expensive phase — `airports` (the simulation, 11.2s) has overtaken it.

### What the sub-phase numbers say now

Inside `load airports`, after both changes:

```
  .. assets               6.4s
  .. runways              1.0s
  .. loyalists+bonuses    1.1s
  .. lounges              1.0s
  .. features             1.0s
  .. bases                0.9s
```

The remaining five are around 1s each and all have the same shape: one query
per airport for runways, lounges, features, bases and loyalists. Batching them
the same way should take most of that 4.6s, using `loadAirportAssetsByAirports`
as the pattern.

After that, the big one is `links + passengers` at ~39s — but that is the
game's actual simulation rather than loading, so it changes what players see.
Not something to do without tests passing and a deliberate decision.

---

## Suggested order

1. **Fix the 11 inherited test failures**, so the suite can be trusted as a
   safety net. Everything below is a refactor of live game logic; doing it
   without a working suite is how a private game quietly starts paying wrong
   salaries.
2. **Attack `load airports`.** Biggest ratio of payoff to risk: it is loading,
   not logic, so behaviour should be unchanged if it is done right, and the
   profiler tells you immediately whether it worked.
3. **Then `links + passengers`**, with the profiler open. This is the game's
   heart — change it slowly and check the numbers the players see, not just
   the clock.
4. **The MySQL connector**, only if you actually want MySQL 8. It buys nothing
   for gameplay.

Take a backup before each step: `./scripts/backup-db.sh`.


---

## Things that look broken and are not

Notes from chasing symptoms that turned out to be the game working as designed.
Worth keeping so the next person does not re-investigate them.

### An empty departures board

The board at an airport shows only the next **24 hours**, and route frequency
is per **week**. A route at frequency 1 has a single fixed slot somewhere in
the week, so most days it contributes nothing.

Six routes out of Lisbon at frequencies 1,1,1,1,1,5 give ten departures a week
- about 1.4 a day - and the slots are deterministic, computed from distance and
airline id in `Scheduling.getLinkSchedule`. A day with none is ordinary.

Check before assuming a fault:

```sql
SELECT a.name, l.frequency, l.capacity_economy, ap.iata
FROM link l JOIN airline a ON a.id = l.airline
JOIN airport ap ON ap.id = l.to_airport
WHERE l.from_airport = (SELECT id FROM airport WHERE iata = 'LIS');
```

`frequency = 0` would be a real problem. Anything above that is just a quiet
schedule.

### Cycle timings on the target machine

For reference, an i3-7100 with the optimisations applied:

```
cycle 53 spent 49 secs
cycle 54 spent 55 secs
```

Better than the 2-2.5 minutes originally predicted for a 2-core machine, and
close to the 4-core figure - which fits, since the wins were in database round
trips rather than computation, and a slower machine was paying more for them.
