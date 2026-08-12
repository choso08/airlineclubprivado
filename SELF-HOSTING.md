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

---

## 6. Configuration

Secrets are read from the environment, so nothing sensitive is committed. Both
`application.conf` files use `${?VAR}` overrides:

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

Added, not modified: `docker-compose.yml`, `.env.example`, `scripts/`,
`conf/sbt-repositories`, `.gitignore` and this file.

Everything else is upstream as-is.

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
