# Release notes

## v1.0.0 - first release of the self-hosted fork

The first release of this self-hosted fork of [patsonluk/airline](https://github.com/patsonluk/airline), taken at upstream commit `77647f1` and set up to run a private world for a handful of friends instead of a public server for thousands.

The full list of what changed, and why, is in **[CHANGES.md](CHANGES.md)**. In short:

### Runs on a current machine
- JDK 21, Scala 2.13, Play 3.0.5, sbt 1.9.9, MariaDB 10.11 with the MariaDB driver
- Elasticsearch removed: search and sign-up use the game's own index
- Fixed the bugs that stopped the simulation overnight and dropped the live connection every two seconds
- OpenStreetMap instead of Google Maps: no API key, no billing

### Paced for a few people
- A game week takes 3 minutes instead of 30, and 5 minutes at night
- Routes are bought instead of negotiated; delegates give a discount instead
- Bases, airport assets and lounges cost less; alliances need 2 airlines instead of 3

### New mechanics
- Aircraft leasing, and instalments that take build time like a purchase, with early payoff
- Freight contracts on routes you already fly
- Country tax on route profits, with VAT on aircraft reclaimed against it
- Regional subsidies for short, loss-making routes between small airports
- Seasons, world events, and incidents that depend on the state of your fleet
- Demand falls off gradually with price instead of hitting a wall
- A ceiling: nothing added can move demand or costs by more than 30% either way

Every one of these can be tuned or switched off in **`game-settings.env`**, which lists what upstream used next to each setting.

### Running it
There are no binaries to download: the game is built from source on the machine that runs it.

```bash
git clone https://github.com/choso08/airlineclubprivado.git airline
cd airline
./scripts/setup.sh
```

[SELF-HOSTING.md](SELF-HOSTING.md) covers installing and maintaining it, and [WINDOWS-SETUP.md](WINDOWS-SETUP.md) covers WSL2, Tailscale and access on your own network.

**Plan for the memory.** The game is two Java processes (the site and the simulation) plus a database. On a world of normal size the simulation alone reached 5.6 GB. Give WSL or the server at least 8 GB, and keep `AIRLINE_SBT_HEAP` at no more than half of what `free -h` reports.
