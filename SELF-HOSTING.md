# Self-hosting Airline Club

A private instance of [patsonluk/airline](https://github.com/patsonluk/airline),
vendored at upstream commit `77647f1` (master, "Replaced the deprecated google
heatmap with deck.gl").

The upstream README is written for the old `activator` tooling and says "JDK 8"
and "MySQL 5.x". The tree it describes has moved on: the build is now sbt 1.9.9,
Scala 2.13.14, Play 3.0.5 and Pekko, and it runs fine on **JDK 21**. This
document describes what actually works.

---

## 1. Architecture

Four moving parts. Only the first two are stateful.

| Component | What it is | Required |
|---|---|---|
| **MySQL 5.7** | All game state. ~113 tables. | Yes |
| **Elasticsearch 7.x** | Backs the airport/country/airline search boxes only. | No |
| **`airline-data`** | The backend simulation (`MainSimulation`). Advances game cycles, moves passengers, pays income. | Yes |
| **`airline-web`** | The Play front-end on port 9000. | Yes |

`airline-web` depends on `airline-data` as a **published library**, not as an
sbt subproject — so `airline-data` must be `publishLocal`'d before the web
module will resolve. This trips people up.

The two JVM processes talk over Pekko remoting on port 2552 (`localhost`), which
is how live simulation updates reach the browser.

### Why MySQL 5.7 and not 8.x

The app pins `mysql-connector-java` **5.1.49** and asks for the legacy
`com.mysql.jdbc.Driver`. MySQL 8 defaults to the `caching_sha2_password` auth
plugin, which that connector cannot speak — you get a login failure that looks
like a wrong password. If you must use 8.x, create the user with
`IDENTIFIED WITH mysql_native_password`. MariaDB 10.11 also works unmodified
and is what this instance was validated against.

`character_set_server=utf8mb4` is **not optional**. Without it the world import
dies with `Incorrect string value` on the first city with non-Latin characters
(upstream issue #267).

---

## 2. Quick start

```bash
git clone https://github.com/choso08/airlineclubprivado.git
cd airlineclubprivado

cp .env.example .env
${EDITOR:-nano} .env          # set the passwords and the app secret

docker compose up -d          # MySQL + Elasticsearch
docker compose ps             # wait for both to report healthy

./scripts/build.sh            # compiles airline-data and publishes it locally
./scripts/init-database.sh    # generates the world - takes ~4 minutes
./scripts/run-simulation.sh   # leave running in its own terminal
./scripts/run-web.sh          # leave running in its own terminal
```

Then open <http://localhost:9000>.

`init-database.sh` is **destructive** — it truncates and rebuilds airports,
cities and countries. Run it once at setup, and again only when you change the
world data (as with the Beja addition below).

### Google Maps key

The world map tiles need `AIRLINE_GOOGLE_MAP_KEY` in `.env`. Everything else
works without it. It is a **paid** API — set a budget cap and a quota limit
before you put a key in, because overruns are billed.

---

## 3. Where airports come from

This is the part worth understanding before changing anything.

`MainInit` (`airline-data/src/main/scala/com/patson/init/MainInit.scala`) is
tiny — it delegates to `GeoDataGenerator.mainFlow()`, which is the real world
builder:

```
Meta.createSchema()                  // ~113 tables
GeoDataGenerator.mainFlow()          // cities, airports, runways, countries
AirplaneModelInitializer.mainFlow()  // the aircraft catalogue
GenericTransitGenerator...           // short surface links (e.g. LGW <-> LHR)
```

### The data files

`GeoDataGenerator` reads **plain files from the current working directory** —
which is why the scripts `cd` into `airline-data` before running sbt. They are
committed to the repo, so nothing is downloaded at init time.

| File | Source | Format |
|---|---|---|
| `cities500.txt` | [GeoNames](http://download.geonames.org/export/dump/) | Tab-separated. Col 1 name, 4 lat, 5 lon, 8 country, 14 population |
| `airports.csv` | [OurAirports](https://ourairports.com/data/) | CSV. Col 0 id, 2 type, 3 name, 4 lat, 5 lon, 7 continent, 8 country, 10 municipality, **11 scheduled_service**, 12 ICAO, 13 IATA |
| `runways.csv` | OurAirports | CSV keyed by col 1 = the airport's csv id. Col 3 length **in feet**, 5 surface, 6 lighted |
| `country-code.txt` | — | `name,iso2,iso3` |
| `income-data.txt` | World Bank GNI | Tab-separated; scans cols 12→2 for the most recent non-`..` value |

Patch files layered on top: `additional-airports.csv`,
`additional-cities.csv`, `removal-airports.csv`, `runway-patch-2022-dec.csv`,
`special-airport-names.csv`.

### How a CSV row becomes a playable airport

1. **Parsed** — `getAirport()` maps `small/medium/large_airport` to a base size
   of 1 / 2 / 3. Anything else gets 0.

2. **Filtered** — this is the gate that matters:

   ```scala
   airport.iata != "" && scheduledService && airport.size > 0 &&
     !removalAirportIatas.contains(airport.iata)
   ```

   `scheduledService` is `"yes".equals(col 11)`. **An airport with
   `scheduled_service = "no"` never enters the game, no matter how large its
   runway is.** Roughly 3 800 of the ~74 000 rows survive this filter.

3. **Sized by runway** — `adjustAirportByRunway` counts only **lighted**
   runways: ≥10 000 ft adds +3, ≥9 000 ft +2, ≥7 000 ft +1.

4. **Overridden** — `AirportSizeAdjust.sizeList` hard-codes sizes for ~160 major
   hubs (`ATL` → 10, `LHR` → 9 …).

5. **Given a catchment** — every city is assigned to nearby airports **in the
   same country** within `airportRadius`: 100 km at size 1, 150 km at size 2,
   250 km at size ≥3. When several airports compete, each gets a share weighted
   by `distanceFactor × size²`, where `distanceFactor` is 30 / 20 / 8 / 2 / 1 at
   ≤25 / ≤50 / ≤100 / ≤200 / more km. Only the ten strongest are kept.

6. **Reduced to `population` and `income`**, the two numbers the demand model
   actually consumes. `DemandGenerator` returns zero demand for any airport with
   `population == 0`, so catchment is what makes an airport real.

---

## 4. The Beja addition

Beja (BYJ / LPBJ) was **already present in the upstream data** and already had
its real runways — it was excluded by exactly one field:

```
4441,"LPBJ","medium_airport","Beja Airport / Airbase",38.078899,-7.9324,...,"Beja","no","LPBJ","BYJ",...
                                                                                  ^^^^
```

`scheduled_service = "no"` — historically accurate, since Beja has almost no
commercial traffic. So the change is a single field in `airline-data/airports.csv`:

```diff
-...,"Beja","no","LPBJ","BYJ",...
+...,"Beja","yes","LPBJ","BYJ",...
```

**Why not `additional-airports.csv`?** Because airports added through that file
are appended *after* runway assignment, so they get `runway_length = 0` and you
would have to invent a size by hand. Flipping `scheduled_service` runs Beja
through the real pipeline instead, and it picks up its genuine runways from
`runways.csv` (11 319 ft = 3 450 m concrete, lighted).

Re-run `./scripts/init-database.sh` and Beja is in the game:

| | Value |
|---|---|
| IATA / ICAO | **BYJ** / **LPBJ** |
| Coordinates | 38.0789, −7.9324 |
| Runway | **3 450 m** concrete, lighted |
| Size | **5** (base 2 for `medium_airport`, +3 for the ≥10 000 ft lighted runway) |
| Covered population | **562 519** |
| Income | 22 132 |

Size 5 gives it a 250 km catchment, which reaches Lisbon, Setúbal, Évora and the
Algarve. The population is genuine redistribution, not a fabricated number —
enabling Beja moved Lisbon from 3 635 744 to 3 277 595 and Faro from 427 931 to
340 246, because those cities now split their weight with a nearer airport.

That makes Beja the **third-largest airport in mainland Portugal** by catchment,
ahead of Faro — comfortably enough to generate real demand, so no artificial
population boost was needed.

| IATA | City | Size | Population | Runway |
|---|---|---|---|---|
| LIS | Lisbon | 6 | 3 277 595 | 3 805 m |
| OPO | Porto | 6 | 3 067 041 | 3 480 m |
| **BYJ** | **Beja** | **5** | **562 519** | **3 450 m** |
| FAO | Faro | 4 | 340 246 | 2 490 m |
| CAT | Cascais | 2 | 313 765 | 1 210 m |

The 3 450 m runway takes anything up to and including widebodies, which matches
Beja's real airframe-maintenance and diversion role.

### Verified demand

Weekly outbound passenger demand, computed with the game's own
`DemandGenerator.computeDemandBetweenAirports` against the generated world:

| Route | Business | Tourist | Total / week |
|---|---|---|---|
| BYJ → LHR | 179 | 62 | **241** |
| BYJ → CDG | 150 | 78 | **228** |
| BYJ → MAD | 132 | 46 | **178** |
| BYJ → AMS | 119 | 51 | **170** |
| BYJ → FRA | 93 | 30 | **123** |
| BYJ → BCN | 77 | 43 | **120** |
| BYJ → OPO | 82 | 32 | **114** |

241 passengers a week to London is enough to sustain a small narrowbody on a
few weekly rotations, so Beja is a genuinely playable base rather than a
decorative pin on the map.

### Tuning it

If you want Beja larger or smaller, the intended lever is
`AirportSizeAdjust.sizeList` in
`airline-data/src/main/scala/com/patson/init/AirportSizeAdjust.scala`:

```scala
"BYJ" -> 6,   // forces size 6 regardless of the runway heuristic
```

Size drives both the catchment radius and the `size²` weight, so it moves
population sharply. Re-run `init-database.sh` afterwards.

---

## 5. Exposing it to friends over Tailscale

Keep port 9000 off the public internet. Tailscale gives your friends a private
address without opening a firewall port.

```bash
# On the server
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --hostname=airline
```

Have each friend install Tailscale and join the same tailnet (invite them from
the admin console at <https://login.tailscale.com/admin/users>). The game is then
at `http://airline:9000` — or use MagicDNS/HTTPS to avoid the port:

```bash
sudo tailscale serve --bg 9000
# -> https://airline.<your-tailnet>.ts.net
```

`tailscale serve` terminates TLS with a real certificate, which is worth doing:
Play sets session cookies, and `AIRLINE_APP_SECRET` protects them in transit
only if the transit is encrypted.

Two things to get right:

- **Do not use `tailscale funnel`** unless you genuinely want the game on the
  public internet. `serve` is tailnet-only; `funnel` is world-readable.
- Bind the backing services to localhost. The provided `docker-compose.yml`
  already publishes MySQL and Elasticsearch on `127.0.0.1` only, so joining a
  tailnet does not expose your database.

`play.filters.hosts.allowed = ["."]` in `airline-web/conf/application.conf`
already accepts any Host header, so the Tailscale hostname works with no extra
configuration.

### Prefer this over port forwarding

Tailscale never needs an inbound port opened: both ends dial out, and when a
direct path cannot be established the traffic is relayed. That means it keeps
working behind **CGNAT** and on **IPv6-only** connections, where port
forwarding is simply not available.

If you are on a connection that still gives you a public IPv4 address, it is
tempting to forward port 9000 instead. Don't — it puts the game on the open
internet, and it breaks the day the connection changes. Building on Tailscale
from the start means an ISP change is a non-event: the address your friends
use stays the same.

### Accounts and saved progress

Everything a player builds — airline, routes, aircraft, balance — lives in the
database, so progress persists across restarts on its own. Each player just
registers their own account at `/signup`; there is no invite system and no
email verification, and users are created `ACTIVE` immediately.

Two consequences worth knowing:

- Anyone who can reach the site can register. That is fine on a tailnet, where
  only people you invited can reach it at all.
- **`init-database.sh` erases player accounts.** `MainInit` calls
  `Meta.createSchema()`, which `DROP`s and recreates every table — `user`,
  `user_secret`, `user_airline`, `airline`, `airplane` and `link` included. It
  is a fresh start, not a world refresh.

So settle your world changes — extra airports, adjusted sizes — **before**
anyone registers. Afterwards, treat re-initialising as "everyone starts over".

There is no safe way to swap the world out from under a running game:
`GeoDataGenerator` deletes and re-inserts airports, so they come back with new
auto-increment ids, while every saved route, aircraft and airline still points
at the old ones. The script now refuses to run without an explicit `ERASE`
confirmation when it finds registered players.

---

## 6. Configuration

Two files, split by what they hold:

| File | Holds | In git? |
|---|---|---|
| **`game-settings.env`** | How the game plays — pace, signup, backups | Yes |
| **`.env`** | Passwords and the session secret | No |

`game-settings.env` is the one to edit when you want to change the game.
Precedence is: anything exported on the command line beats `.env`, which
beats `game-settings.env` — so you can try a value for one run without
editing anything:

```bash
AIRLINE_CYCLE_SECONDS=120 ./scripts/run-simulation.sh
```

### Game pace

One cycle is one in-game week. Upstream ships 30 minutes per cycle, which
means a game year takes about 26 real hours — sensible for a public server,
painfully slow for friends playing an evening.

`AIRLINE_CYCLE_SECONDS` controls it. The floor is how long a cycle takes to
compute, which the simulation logs every tick:

```
cycle 2 spent 66 secs
```

Measured on a 4-core box: **66–77 seconds** per cycle. Most of that is the
global passenger simulation over ~3800 airports, which barely grows with the
number of players. On a 2-core i3 expect 2–2.5 minutes, so 300 seconds
(5 minutes) leaves comfortable headroom. Set it too low and cycles queue up
behind each other and the game drifts.

### Everything the environment can override

| Variable | Default | Used for |
|---|---|---|
| `AIRLINE_DB_HOST` | `localhost:3306` | MySQL host:port |
| `AIRLINE_DB_SCHEMA` | `airline_v2_1` | Schema name |
| `AIRLINE_DB_USER` | `sa` | DB user |
| `AIRLINE_DB_PASSWORD` | `admin` | DB password |
| `AIRLINE_APP_SECRET` | `changeme` | Play session signing key |
| `AIRLINE_GOOGLE_MAP_KEY` | — | Map tiles |
| `AIRLINE_GOOGLE_API_KEY` | — | Airport image search |
| `AIRLINE_SBT_HEAP` | `6G` | Build/init heap |
| `AIRLINE_SBT_REPOS_FILE` | — | Restricted-egress resolver list |
| `AIRLINE_CYCLE_SECONDS` | `1800` | Real seconds per in-game week |
| `AIRLINE_RECAPTCHA_ENABLED` | `false` | Anti-bot check on signup |
| `AIRLINE_WEB_PORT` | `9000` | Port the site listens on |
| `AIRLINE_BACKUP_KEEP` | `14` | Nightly backups retained |
| `AIRLINE_LANGUAGE` | `en` | Interface language for a player who has not chosen one (`en`, `pt`) |
| `AIRLINE_RENAME_COOLDOWN_DAYS` | `0` | Wait between airline renames; upstream enforces 30 |
| `AIRLINE_MAP_THEME` | `dark` | Which map theme a new player opens on |
| `AIRLINE_WEATHER_API_KEY` | upstream's | OpenWeatherMap key, or `off` |

---

## 6b. Updating without stopping the game

```bash
./scripts/update.sh              # backup, pull, build, restart
./scripts/update.sh --web-only   # front-end fixes only
```

The two halves are independent processes sharing a database, which is what
makes low-disruption updates possible:

- **Restarting the web site does not pause the game.** The clock keeps
  ticking, flights keep flying, income keeps accruing. Players get a dead page
  for ~15 seconds. Most fixes — pages, buttons, displayed numbers — need only
  this, so reach for `--web-only`.
- **Restarting the simulation** is the disruptive one, so the script waits for
  the running cycle to finish before it does. Killing it mid-cycle can leave a
  cycle half-applied.
- **The new version compiles before anything stops.** A build that fails costs
  no downtime at all — the old version is still serving and untouched.

The one thing that genuinely cannot be done live is a change to the world data
(§ Accounts), because that reassigns airport ids under existing routes.

**Change `AIRLINE_APP_SECRET` before letting anyone in.** With the default,
anyone who knows it can forge a session cookie for any account.

---

## 7. Deviations from upstream

Kept deliberately small, so upstream changes stay easy to merge.

1. **`airline-data/airports.csv`** — Beja's `scheduled_service` flipped to
   `yes` (one field, one line).
2. **`project/plugins.sbt` and `airline-web/project/plugins.sbt`** —
   `sbt-coffeescript` commented out. The project has **zero** `.coffee` files,
   and the plugin is published only to `repo.scala-sbt.org`, so the build breaks
   on any host that cannot reach that repo. Purely a build fix.
3. **Both `application.conf` files** — `${?ENV}` overrides added for DB
   credentials, the Play secret and the Google keys. Defaults unchanged, so
   upstream behaviour is preserved when nothing is set.
4. **`README.md`** — a pointer to this document, since upstream's instructions
   describe the older `activator` tooling.
5. **`SignUp.scala`, `signup.scala.html`, `signup.js`** — reCAPTCHA is now
   configurable and off by default. Upstream hardcodes the keys for
   airline-club.com, so on any other hostname Google rejects the token and
   **nobody can register**. The same change removes the FullStory session
   recorder, which streamed player sessions to upstream's analytics account.
6. **`SearchUtil.java`** — Elasticsearch failures are no longer fatal. See
   below; this is the difference between "search is disabled" and "no one can
   create an account".
7. **`WeatherUtil.java` and `Application.getWeatherError`** — a failed weather
   lookup no longer takes the departure board with it. See below.

Added, not modified: `docker-compose.yml`, `.env.example`, `scripts/`,
`deploy/systemd/`, `conf/sbt-repositories`, `.gitattributes`, `.gitignore`,
`WINDOWS-SETUP.md` and this file.

Everything else is upstream as-is.

### Elasticsearch was not actually optional

Upstream documents Elasticsearch as optional, but `SearchUtil` ran
`checkInit()` from a **static initialiser** and only caught `IOException`. A
refused connection surfaces as `ElasticsearchException`, which is unchecked,
so it escaped the initialiser and permanently poisoned the class for the
lifetime of the JVM. Because the signup flow calls `SearchUtil.addAirline()`
*after* committing the user, registration would write the account to the
database and then return HTTP 500 with no session — the player sees a crash,
retries, and is told the username is already taken.

Both that initialiser and `addAirline` now catch `Exception` and log a
warning. Verified: with no Elasticsearch running at all, signup returns 303
and login succeeds.

### The weather could take the departure board down

`/airports/:id/departures/...` asks for the airport's weather *before* it
builds the list of flights, and two things made a failed lookup fatal:

- `WeatherUtil`'s Guava `LoadingCache` refuses to store `null`, so "no
  forecast" arrived as `InvalidCacheLoadException` — a `RuntimeException`,
  while `getWeather` caught only `ExecutionException`. The failure went
  straight through.
- `getWeatherError` then read `weather.getWindSpeed()` with no null check, so
  even a clean `null` produced a `NullPointerException` for any airport whose
  flights had consumption data.

Either way the request answered HTTP 500 and the board came up **completely
empty** — every day, every airline, no message saying why. Which is easy to
mistake for "my routes are not flying".

The lookup fails more readily than it looks: the OpenWeatherMap key is
hardcoded in upstream's source, so it is public, shared by every copy of the
game, and rate-limited accordingly.

`getWeather` now returns `null` on any failure, the cache holds an `Optional`
so a failure is remembered rather than retried on every request, and
`getWeatherError` treats a missing forecast as no weather delays. The key is
configurable (`AIRLINE_WEATHER_API_KEY`, or `off` to skip the lookup).

---

## 8. Troubleshooting

**`Error downloading com.typesafe.sbt:sbt-coffeescript`**
Your resolver list cannot reach `repo.scala-sbt.org`. The plugin is already
disabled here; if you re-enabled it, disable it again, or add the sbt Ivy repo.

**`not found: default:airline-data_2.13:2.1`**
`airline-web` resolves `airline-data` from the local Ivy cache. Run
`./scripts/build.sh` first.

**`Incorrect string value: '\xF0\x9F...'` during init**
MySQL is not on utf8mb4. Fix the server charset and re-run — see §1.

**Login failure against MySQL 8 with a password you know is right**
`caching_sha2_password` vs connector 5.1.49. See §1.

**Init finishes but airports are missing**
Almost always the `scheduled_service` filter (§3, step 2), not a bug.

**Port 9000 already in use**
`AIRLINE_WEB_PORT=9001 ./scripts/run-web.sh`.
