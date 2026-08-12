@echo off
REM ===========================================================================
REM  Stops Airline Club.
REM
REM  Stopping is safe at any time: everything lives in the database, so no
REM  progress is lost. At worst the in-game week currently being calculated is
REM  redone on the next start.
REM ===========================================================================
setlocal

set "DISTRO=Ubuntu"

title Airline Club - stopping

echo.
echo   Stopping Airline Club...
echo.

wsl -d %DISTRO% -e true >nul 2>&1
if errorlevel 1 (
  echo   Linux is not running - nothing to stop.
  echo.
  timeout /t 3 /nobreak >nul
  exit /b 0
)

REM Services first, if they are in use.
wsl -d %DISTRO% -e bash -lc "systemctl is-enabled airline-web.service" >nul 2>&1
if not errorlevel 1 (
  echo   Stopping the services ^(asks for your Linux password^)...
  wsl -d %DISTRO% -e bash -lc "sudo systemctl stop airline-web airline-sim"
) else (
  echo   Stopping the scripts...
  REM pkill -f matches the ENTIRE command line, which includes the shell that
  REM is running this very command - so a plain pattern makes it kill itself
  REM before it finishes. Wrapping the first letter in a character class keeps
  REM the regex matching the real processes while no longer matching the
  REM literal text of our own command line.
  wsl -d %DISTRO% -e bash -lc "pkill -f '[u]niversal/stage/bin/airline-web' ; pkill -f '[c]om.patson.MainSimulation' ; pkill -f '[s]bt-launch.jar' ; true"
)

echo.
echo   Stopped.
timeout /t 3 /nobreak >nul
exit /b 0
