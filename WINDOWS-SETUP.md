# Setting it up on Windows 10

Written for the HP 290 G2 (i3-7100, 16 GB RAM). That machine is comfortably
big enough — the game needs about 3 GB of RAM once everything is up.

The game is Linux software, so the plan is to run it inside **WSL2**, which is
a real Linux that Windows runs for you in the background.

---

## Short version

1. Install WSL2 + Ubuntu.
2. Install Java, MariaDB and the game inside Ubuntu.
3. Build the world once (about 4–10 minutes on this machine).
4. Install Tailscale **inside Ubuntu** so your friends can reach it.
5. Make it start by itself when the PC boots.

Skip Docker and skip Elasticsearch. You do not need either, and on this
machine they only cost you RAM and setup time. (Elasticsearch only powers the
search box; the game works fine without it.)

---

## 1. Install WSL2 and Ubuntu

In **PowerShell as Administrator**:

```powershell
wsl --install -d Ubuntu
```

Reboot when it asks. Ubuntu opens and asks you to pick a username and
password — this is your Linux account, nothing to do with Windows.

Then make sure WSL is the newer Store version, which is what gives you
automatic startup of background services:

```powershell
wsl --update
wsl --version
```

Everything from here happens **inside the Ubuntu window**.

Turn on the service manager so the game can start itself at boot:

```bash
sudo tee /etc/wsl.conf > /dev/null <<'EOF'
[boot]
systemd=true
EOF
```

Then, back in PowerShell, restart Linux once so that takes effect:

```powershell
wsl --shutdown
```

Reopen Ubuntu and check it worked — it should print a list of services, not
an error:

```bash
systemctl list-units --type=service --no-pager | head
```

> If it errors, your WSL is too old. Run `wsl --update` in PowerShell again.

---

## 2. Install what the game needs

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk mariadb-server git unzip
```

Start the database and let it come up at boot:

```bash
sudo systemctl enable --now mariadb
```

Make the database store text properly. Without this the world import crashes
on the first city with an accent or a non-Latin name:

```bash
sudo tee /etc/mysql/mariadb.conf.d/99-airline.cnf > /dev/null <<'EOF'
[mysqld]
character_set_server = utf8mb4
collation_server = utf8mb4_general_ci
bind-address = 127.0.0.1
max_connections = 300
innodb_buffer_pool_size = 2G
innodb_flush_log_at_trx_commit = 2
EOF

sudo systemctl restart mariadb
```

---

## 3. Get the game and create its database

**Important:** put it in your Linux home folder (`~`), *not* somewhere under
`/mnt/c/`. Files on the Windows side are very slow to read from Linux, and
the setup reads about 85 MB of data files.

```bash
cd ~
git clone https://github.com/choso08/airlineclubprivado.git airline
cd airline
```

Create the database and its user. Pick a password and use the same one in
both places:

```bash
DBPASS='choose-a-password-here'

sudo mariadb <<EOF
CREATE DATABASE IF NOT EXISTS airline_v2_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'sa'@'localhost' IDENTIFIED BY '$DBPASS';
GRANT ALL PRIVILEGES ON airline_v2_1.* TO 'sa'@'localhost';
FLUSH PRIVILEGES;
EOF
```

Now write the settings file:

```bash
cp .env.example .env
nano .env
```

Set these four lines (leave the rest as they are):

```
AIRLINE_DB_USER=sa
AIRLINE_DB_PASSWORD=the-same-password-you-just-picked
AIRLINE_APP_SECRET=paste-the-output-of-the-command-below
AIRLINE_RECAPTCHA_ENABLED=false
```

Generate the secret with:

```bash
openssl rand -base64 48
```

That secret is what stops someone forging a login for another player's
account, so give it a real value.

---

## 4. Build and load the world

```bash
./scripts/build.sh          # a few minutes, downloads the libraries
./scripts/init-database.sh  # builds the world - roughly 4-10 minutes
```

`init-database.sh` wipes and rebuilds the airports, cities and countries.
Run it now, and afterwards only when you change the world data. **It does not
touch player accounts, airlines or money.**

---

## 5. Start it

Two things need to run: the simulation (the game clock) and the web site.

```bash
./scripts/run-simulation.sh   # leave this window open
```

Open a second Ubuntu window:

```bash
cd ~/airline
./scripts/run-web.sh
```

Open <http://localhost:9000> in Windows — it just works, WSL2 forwards
localhost for you.

Click **Sign Up**, make your account and your airline. Do the same for each
friend. Everything is saved in the database, so progress survives restarts.

---

## 6. Let your friends in, with Tailscale

Install Tailscale **inside Ubuntu**, not on Windows. That way the game gets
its own address on your private network:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --hostname=airline
```

It prints a link — open it and log in. Then invite your friends from
<https://login.tailscale.com/admin/users>. They install Tailscale on their own
PC or phone, join, and the game is at:

```
http://airline:9000
```

Nicer version, which gives you a proper padlock in the browser:

```bash
sudo tailscale serve --bg 9000
```

Now it is at `https://airline.<your-tailnet>.ts.net`, with no port number.

**Do not use `tailscale funnel`** — that one puts the game on the open
internet. `serve` keeps it private to you and your friends.

### Why this survives your move to Digi

You mentioned Digi gives you CGNAT and IPv6 only, so you would lose port
forwarding. That does not matter here: **Tailscale never needs port
forwarding**. It makes the connection outward from both sides and, when it
cannot connect the two directly, it relays the traffic. CGNAT and IPv6-only
connections are exactly what it is built for.

So do not set up port forwarding on your current Uzo connection either. If
you build things around Tailscale now, the day you switch to Digi nothing
changes — your friends keep using the same `airline` address.

---

## 7. Make it start on its own

So you do not have to open two windows every time.

```bash
sudo cp ~/airline/deploy/systemd/airline-*.service /etc/systemd/system/
sudo sed -i "s|/opt/airline|$HOME/airline|g; s|User=%i|User=$USER|" \
  /etc/systemd/system/airline-sim.service /etc/systemd/system/airline-web.service
sudo systemctl daemon-reload
sudo systemctl enable --now airline-sim airline-web
```

Check on them:

```bash
systemctl status airline-web
journalctl -u airline-sim -f      # live log, Ctrl+C to stop watching
```

Finally, make Windows start Linux at boot. In PowerShell **as
Administrator**:

```powershell
$action  = New-ScheduledTaskAction -Execute "wsl.exe" -Argument "-d Ubuntu -- exit"
$trigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -TaskName "Start WSL Airline" -Action $action -Trigger $trigger -RunLevel Highest -User "SYSTEM"
```

Without this, Linux only wakes up when you open the Ubuntu window.

---

## 8. Back it up

With six people playing, the database *is* the game. Everything anyone has
built lives only there.

```bash
./scripts/backup-db.sh
```

It writes a compressed copy into `backups/` and keeps the last 14. To do it
automatically every night at 4am, run `crontab -e` and add:

```
0 4 * * * cd $HOME/airline && ./scripts/backup-db.sh >> backups/backup.log 2>&1
```

To put a backup back:

```bash
gunzip < backups/airline-YYYYMMDD-HHMMSS.sql.gz | mariadb -u sa -p airline_v2_1
```

Take one before any change to the game's numbers.

---

## If something goes wrong

**`bad interpreter: No such file or directory`**
The scripts got Windows line endings. Fix with:
`sudo apt install -y dos2unix && dos2unix scripts/*.sh scripts/sbt`

**The web page will not load**
Check both parts are alive: `systemctl status airline-web airline-sim`.
The site needs the database up first: `systemctl status mariadb`.

**Signing up fails**
Make sure `AIRLINE_RECAPTCHA_ENABLED=false` is in `.env`. The anti-bot check
built into the original game only accepts the real airline-club.com address
and will reject your friends otherwise.

**Everything is very slow**
The game is probably on `/mnt/c/`. Move it into your Linux home folder.

**Nothing happens in the game / the clock is frozen**
The simulation is not running. That is `airline-sim`, separate from the web
site.
