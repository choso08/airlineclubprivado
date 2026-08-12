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
if errorlevel 1 (echo   NO - http://localhost:9000 is not responding) else (echo   YES - http://localhost:9000 is up)

echo.
echo   --- Recent cycles (how long each in-game week takes to compute) ---
wsl -d %DISTRO% -e bash -lc "journalctl -u airline-sim -n 300 --no-pager 2>/dev/null | grep 'spent' | tail -5 || echo '  (no service logs - if you started it by hand, look at that window)'"

echo.
echo   --- Players registered ---
wsl -d %DISTRO% -e bash -lc "cd %GAMEDIR% 2>/dev/null && set -a && . ./.env 2>/dev/null && set +a && mariadb --user=\"$AIRLINE_DB_USER\" --password=\"$AIRLINE_DB_PASSWORD\" --skip-column-names --batch -e 'SELECT COUNT(*) FROM user;' \"$AIRLINE_DB_SCHEMA\" 2>/dev/null || echo '  (could not read the database)'"

echo.
pause
exit /b 0
