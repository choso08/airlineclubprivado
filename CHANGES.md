# What changed from the original game

This is a private fork of [patsonluk/airline](https://github.com/patsonluk/airline),
taken at commit `77647f1`, set up to run at home for five friends.

The original was written for a public server with thousands of players. Almost
everything below comes from that: prices, waits and requirements that make sense
with a thousand people and a year of play, but which with five people on a
weeknight either go unnoticed or turn into a wall.

Everything adjustable lives in one place — the **`game-settings.env`** file — and
each setting says there what it does, what it was in the original, and what it
is now.

---

## 1. Things that were broken and got fixed

These are not options: this was code that did not work, or that only fails on a
small server.

- **The game stopped advancing for a whole night.** The real reason: the game
  stores money in text columns and was writing `360000.0` into them; modern
  drivers refuse to read that. The simulation blew up halfway through every
  cycle. And there was no log at all — the simulation had nowhere to write its
  errors, so for an entire day there was not a single line saying what was
  happening. Both are fixed: the log first, the error after.
- **The connection to the page died every two seconds.** A query passed a Scala
  type the driver does not accept, which killed the websocket actor. The result
  was flickering charts, a clock that jumped, money turning yellow without
  having changed, and the button for the Olympics prize doing nothing. Fixed in
  76 places.
- **Elasticsearch.** The original needs a separate search engine just for the
  search boxes and for signing up. Without it you could not even create an
  account. It was removed: the searches now use the index the game already has.
- **Database driver.** It now talks to MariaDB with the MariaDB driver.
- **One bad row in the alliance table took down the whole alliances page.** A
  panel that fails no longer drags the others with it.
- **The live connection did not reconnect** after a drop — the page went deaf
  until you reloaded it.
- **City photographs**: they now come from Wikipedia, and a failed lookup is
  stored as failed instead of being retried forever.
- Several others: signing up, forming an alliance, the announcements panel, the
  airline logo (it resizes instead of refusing), the Vimeo call on the home
  page, the map covering the menus.

---

## 2. Pace — the game runs faster

| | Original | Here |
|---|---|---|
| One game week | 30 minutes | **3 minutes** |
| One game year | ~26 hours | **~2.6 hours** |

And also:

- **A "Force week" button** to run the next week now.
- **Holiday mode** (off): if nobody plays for half an hour, the world slows to a
  quarter speed, so that someone who misses two days does not come back to a
  world that ran for months without them. It returns to normal as soon as
  someone logs in.
- **Automatic watchdog**: if the week number does not move for two cycles, the
  simulation is restarted on its own and a notice stays inside the game saying
  that happened.
- **The simulation watches itself**: a week that blows up no longer takes the
  following ones with it, and if nothing happens for two weeks it forces one
  itself.

---

## 3. Rules that were tuned

All of this is in `game-settings.env` and can be put back at any time.

| Rule | Original | Here | Why |
|---|---|---|---|
| Buying routes | negotiation with delegates, dice rolls, weeks of waiting | **bought directly, no extra cost** | the negotiation is a wait, not a decision, when there are five people |
| Delegates | handle the negotiations | **each one takes 10% off the price of a route, up to 50%** | they were left with no work once routes became something you buy |
| Delegate wait | 12 weeks | **3** | |
| Changing a route again | 6 weeks | **2** | half an hour of real waiting to touch a route you have just opened |
| Delegate levels | 4 weeks / 1 year / 3 years / 10 years | **4x faster** | nobody reaches ten years of play |
| Building and running bases | 100% | **50%** | the price multiplies by 1.7 per level: at level 10 a base costs 40x what it costs at level 3 |
| Office staff per base | 100% | **200%** | this is what really limits how many routes you can have |
| Base specialisations | need level 8 to 14 | **4 to 7** | nobody on a small server reaches 8 |
| Airport assets (hotels, etc.) | 200 to 2000 million | **25% of that** | a mechanic that was built and unreachable |
| VIP lounges | 50 million per level | **40% of that** | same |
| Discount on an unpopular aircraft | up to 70% | **20%** | the thresholds are for hundreds of aircraft in circulation; with five people every aircraft was almost always at the maximum discount, and buying one turned a profit |
| Alliance | minimum 3 airlines, with approval | **2, you are in straight away** | 3 out of 5 is more than half of everybody; and an alliance whose leader was not logged in never accepted anyone |
| Changing the airline name | 30 days of waiting | **no wait** | |
| Logo and livery | needs 40 reputation | **from the start** | |
| Support features (Patreon) | locked | **open** | there is nobody to donate to on a private server |
| Minimum password | 6 | **4** | |
| Aircraft speed | the climb gain has fixed limits | **proportional to the aircraft** | below 700 km every aircraft above 700 km/h took exactly the same time, and paying for speed bought you nothing |

---

## 4. New mechanics

Things the original does not have at all.

### Seasons
The game has a calendar and ignored it: demand in January was August's. With
this, holidays follow the summer — which is a different month in each hemisphere
— and business travel drops when the offices empty out, at Christmas and in
August. Measured, at 30: London→Faro earns 0.75 in January and 1.26 at the end
of July; London→Sydney does the opposite; London→Lisbon (business) drops to 0.82
at Christmas.

### World events
Every so often something happens that everybody has to react to: a fuel crisis,
a tourism boom, a recession, or trouble at an airport. One at a time, a few weeks
long, announced to everyone. Without this, week 300 is the same as week 3 and the
only news is what you do to each other.

### Deals between airlines
A panel where you offer money and aircraft, ask for money, and the other side
accepts or refuses. It is for selling an aircraft, paying off a debt agreed in
the chat, buying someone out of a route, or making a gift. Before this, two
people could agree on whatever they liked and there was no way at all to move
anything.

### Leasing aircraft and paying in instalments
Buying was the only way to have an aircraft.

- **Leasing**: 10% down, 18% of the price a year, forever, and the aircraft is
  never yours. You hand it back the week you stop needing it.
- **Instalments**: 25% down, 104 weekly payments at a rate 3 points below the
  bank's, and when you pay the last one the aircraft becomes yours.

Leased, the aircraft arrives **right away** — it is somebody else's aircraft,
already built, and having it today is half the reason to lease. On instalments it
is a new aircraft bought on credit, so it **takes time to be built, like a normal
purchase** — otherwise instalments would simply be better than buying. Until it
is paid off it cannot be sold, swapped, given away in a deal, or counted as
assets.

### Demand that reacts to price
Every passenger stopped at exactly the same price, which made a wall: at 1.2x the
suggested price 4% flew, at 1.3x nobody flew. Now each group has its own limit
and demand falls off gradually — charging a lot became a choice (fewer people,
more per ticket) instead of a mistake.

### Two aircraft types on the same route
The original requires a route to be flown by a single model. Here they can be
mixed, with the range and runway checks done model by model.

### Firsts board
A short list of milestones — first to 100 million, first to reach X — each won
once and kept with the name of whoever got there first. A public server has a
world ranking; five friends had nothing to be first at.

### Incidents
Things that go wrong for **one** airline at a time: a breakdown (half the time —
you pay for the repair and the aircraft loses condition), an inspection (delays
for a few weeks) or a strike (cancellations). Everybody is told, because half the
fun is the others seeing it.

It is not luck falling on whoever it falls on: the odds come from **the state of
the fleet**. With new aircraft it is a third, with worn-out aircraft it is double
— maintenance becomes a decision instead of a bill. Measured over 200 weeks of
40 flights, with the fleet in good shape: normally 1.3 small delays and 0.2
cancellations a week; with an inspection 4.9 small and 2.0 large; with a strike
0.9 cancellations. A few per cent of the week's revenue.

### Regional subsidies
In the game every route has to pay for itself, so small airports stay empty
forever. Now a route **between two small airports** (size ≤ 3), **short**
(≤ 1500 km) and **running at a loss** gets back **half of that week's loss**, up
to 500,000.

It never makes the route profitable on its own, and a route that turns a profit
gets nothing — the idea is to make flying into the countryside possible, not to
turn it into a better business than flying to the capitals.

### Country tax
A route now pays tax to the country **it departs from** — which is always a base
of yours, because a route cannot start anywhere else. The rate follows the
country's income: 15% in an average country, eight points either way. Measured on
the game's real data: **Portugal 8.1%**, Germany 21.8%, Switzerland and Norway
23%, India and Morocco 7%.

Route by route, and only on profit: a route that loses money pays nothing and
cannot be used to offset another route's profit. Each country's rate shows up in
the airport panel, under the market openness — you can see it before building the
base. Where you put your bases became a question of country as well.

### VAT on aircraft, reclaimed against the tax
A company in Europe pays VAT on an aircraft and then gets it back, against what
it owes on its profits — which is why a company that has just invested spends a
while paying no tax.

Buying an aircraft creates a credit of **23% of the price**. Leasing or paying in
instalments creates it on the deposit and on **each weekly payment**, just like
the VAT on a lease rental. Every week the route tax is paid out of that credit
first, and only what is left over is actually charged. A notice goes in the log
when the credit is created and another when it runs out.

### Where you see all of this
- **Payments panel** (top): what leaves every week, **how much is still to pay
  in total**, the VAT still to reclaim, and each aircraft on a payment plan —
  how much you have paid, over how many weeks, how much is left and whether it
  is flying.
- **Income statement**: under *Asset expense* there are now three lines — *of
  which aircraft payments*, *of which country tax*, *of which freight*. Each
  disappears when it is zero, so anyone with these switched off sees the income
  statement exactly as it always was.

  The income statement's columns are fixed in the database and adding one would
  mean altering the table of a world already being played — and if that change
  failed, every week would start blowing up as it saved the accounts. So the
  three numbers are kept in a separate table, week by week, and added up over
  whichever period you are looking at (week, month or year).

### Freight
An aircraft's hold flew empty every week. Now there are **contracts**: somebody
offers so many tonnes a week between two airports, for so many weeks, at a price
— and the offers only show up on routes you already fly.

Accepting is a promise of space on a route you now have to keep flying, and
being paid every week whatever happens to the passengers. The hold is real: 0.02
tonnes per seat per flight, so a 180-seat aircraft flying 14 times carries 50
tonnes a week, and a contract asks for between a quarter and two thirds of that.
A week without space loses that week's payment, three cancel the contract, and
backing out on purpose costs four weeks.

A new panel at the top: **Cargo**.

### Slower nights
The server runs all night for people who are asleep — and at 3 minutes a week
that is 160 weeks between going to bed and waking up. From midnight to 10:00 a
week takes 5 minutes.

### A 30% ceiling
Nothing that was added can move demand or costs by more than 30% either way,
**however many things land in the same week**. Seasons, world events and the rest
are multiplied together and then held inside that band, in a single place. A boom
in a country where it is also summer cannot double a route.

### Compensation
A script for putting money into an airline by hand, when something goes wrong
through the server's fault (the game caches balances for 10 minutes, so the
script restarts the site too).

---

## 5. Interface

- **Map**: Google Maps swapped for OpenStreetMap (no key, no account, no bills),
  centred on Portugal, with a light/dark theme following the game's and place
  names in English.
- **Aircraft on the map**: they were a yellow dot. Now they are drawn aircraft,
  turned to the right heading, sized by type (20 to 52 px), flying along the
  curved line the map draws and at the timetable's speed.
- **Curved routes**: the lines are now great-circle arcs, like real routes,
  instead of straight lines.
- **Portuguese**: more than a thousand interface phrases translated. It is per
  player — one can read in Portuguese while the others stay in English — and the
  names of airports, cities and aircraft are left as they are.
- **Clock**: the on-screen calendar runs at half the speed of the cycles
  (`AIRLINE_GAME_TIME_SPEED=0.5`) and starts in 2026. This is only the clock and
  the aircraft drawn against it — the simulation still flies one week per cycle,
  and still pays by the week.
- **Filter boxes** on the long lists (airlines, countries, alliances, models).
- **Flight card** in the theme's colours, and the negotiation panel disappears
  when there is nothing to negotiate.
- **Update notices**: before the server restarts a notice appears, and the pages
  reload themselves afterwards.
- Background videos and clips turned off (they were calls out to the internet and
  did not load).

---

## 6. The server

None of this exists in the original, which assumes somebody knows what they are
doing at a console.

- **`scripts/setup.sh`** — installs everything from scratch on a fresh
  Ubuntu/WSL2 machine.
- **Proper services**: the site and the simulation start with the machine, and
  restart themselves if they fall over.
- **Automatic updates** every 5 minutes, with a notice to the players.
- **Daily backups** of the database, keeping the last 14, and a backup button
  inside the game.
- **A watchdog** (`watchdog.sh`) that checks whether the week number still moves.
- **Diagnostics** (`update-status.sh`) that say why the game is not updating,
  without changing anything.
- **Game clock** (`game-time.sh`) for moving the date forwards or backwards.
- **Access at home**: Windows shortcuts that open the game to the rest of the
  house (`lan-access.bat`), and Tailscale launchers.
- **Security**: Tailscale access is only inside your own private network
  (`serve`, never `funnel`), the firewall rule is for the private profile only,
  reCAPTCHA is off (the original's keys refuse any other domain) and the backup
  button only runs one fixed path, with nothing coming from a request.

---

## 7. Where you change all of this

```bash
nano ~/airline/game-settings.env      # everything adjustable, with explanations
./scripts/update.sh --no-pull         # applies it, warns the players, reloads
./scripts/backup-db.sh                # before anything risky
```

Related documents:

- **`SELF-HOSTING.md`** — installing and maintaining
- **`WINDOWS-SETUP.md`** — WSL2, Tailscale, LAN
- **`MODERNISATION.md`** — what was done to the simulation's speed, and why
