@echo off
REM ===========================================================================
REM  Shows whether the game is running, and how fast the simulation is going.
REM  Useful when something looks stuck.
REM ===========================================================================
setlocal

set "DISTRO=Ubuntu"
set "GAMEDIR=$HOME/airline"

title Airline Club - status

echo.
echo   ===== Airline Club status =====
echo.

wsl -d %DISTRO% -e true >nul 2>&1
if errorlevel 1 (
  echo   Linux is not running.
  echo.
  pause
  exit /b 1
)

echo   --- Database ---
wsl -d %DISTRO% -e bash -lc "systemctl is-active mariadb 2>/dev/null || (pgrep -x mariadbd >/dev/null && echo 'running (no systemd)' || echo 'STOPPED')"

echo.
echo   --- Game clock (simulation) ---
wsl -d %DISTRO% -e bash -lc "systemctl is-active airline-sim 2>/dev/null || (pgrep -f com.patson.MainSimulation >/dev/null && echo 'running (started by hand)' || echo 'STOPPED')"

echo.
echo   --- Web site ---
wsl -d %DISTRO% -e bash -lc "systemctl is-active airline-web 2>/dev/null || (pgrep -f 'universal/stage/bin/airline-web' >/dev/null && echo 'running (started by hand)' || echo 'STOPPED')"

echo.
echo   --- Is the site answering? ---
curl -s -o nul -m 5 http://localhost:9000 >nul 2>&1
if errorlevel 1 (
  echo   NO - http://localhost:9000 is not responding
  echo.
  echo   --- Why not: last errors from the web site ---
  REM "active" only means systemd is keeping it alive; with Restart=always a
  REM process that crashes on startup still reports active while it loops.
  wsl -d %DISTRO% -e bash -lc "journalctl -u airline-web -n 25 --no-pager 2>/dev/null | grep -iE 'error|exception|caused by|refused|failed|Address already in use' | tail -12 || echo '  (no service log - started by hand?)'"
  echo.
  echo   --- Last lines, whatever they are ---
  wsl -d %DISTRO% -e bash -lc "journalctl -u airline-web -n 12 --no-pager 2>/dev/null | tail -12 || echo '  (none)'"
) else (
  echo   YES - http://localhost:9000 is up
)

echo.
echo   --- Recent cycles (how long each in-game week takes to compute) ---
REM An empty list here with the service "active" means the simulation is not
REM completing cycles - either it only just started, or it is crash-looping.
wsl -d %DISTRO% -e bash -lc "journalctl -u airline-sim -n 400 --no-pager 2>/dev/null | grep 'spent' | tail -5 || echo '  (none)'"
wsl -d %DISTRO% -e bash -lc "journalctl -u airline-sim -n 400 --no-pager 2>/dev/null | grep -q 'spent' || { echo '  none yet - recent errors from the simulation:'; journalctl -u airline-sim -n 25 --no-pager 2>/dev/null | grep -iE 'error|exception|caused by|refused|failed' | tail -8; }"

echo.
echo   --- Automatic updates ---
wsl -d %DISTRO% -e bash -lc "systemctl list-timers 'airline-*' --no-pager 2>/dev/null | head -4 || echo '  (timers not installed - run ./scripts/install-services.sh)'"
wsl -d %DISTRO% -e bash -lc "grep -q '^AIRLINE_AUTO_UPDATE=off' %GAMEDIR%/game-settings.env 2>/dev/null && echo '  NOTE: AIRLINE_AUTO_UPDATE=off - updates are paused in game-settings.env' || true"
echo.
echo   --- Players registered ---
wsl -d %DISTRO% -e bash -lc "cd %GAMEDIR% 2>/dev/null && set -a && . ./.env 2>/dev/null && set +a && mariadb --user=\"$AIRLINE_DB_USER\" --password=\"$AIRLINE_DB_PASSWORD\" --skip-column-names --batch -e 'SELECT COUNT(*) FROM user;' \"$AIRLINE_DB_SCHEMA\" 2>/dev/null || echo '  (could not read the database)'"

echo.
pause
exit /b 0
