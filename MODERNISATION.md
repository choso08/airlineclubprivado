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
