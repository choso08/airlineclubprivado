@echo off
REM ===========================================================================
REM  Starts Airline Club and opens it in the browser.
REM  Double-click this file. Copy it to the Desktop if you like.
REM
REM  If the systemd services are installed (see WINDOWS-SETUP.md) it just
REM  waits for them; otherwise it launches the two scripts itself in
REM  minimised windows.
REM ===========================================================================
setlocal EnableDelayedExpansion

set "DISTRO=Ubuntu"
set "GAMEDIR=$HOME/airline"

title Airline Club - starting

echo.
echo   Starting Airline Club...
echo.

REM --- is WSL up and is the distro named what we think? ---------------------
wsl -d %DISTRO% -e true >nul 2>&1
if errorlevel 1 (
  echo   ERROR: could not start the WSL distribution "%DISTRO%".
  echo.
  echo   Check the exact name with:   wsl -l -v
  echo   Then edit the DISTRO line at the top of this file.
  echo.
  pause
  exit /b 1
)

REM --- does the game actually exist there? -----------------------------------
wsl -d %DISTRO% -e bash -lc "[ -x %GAMEDIR%/scripts/run-web.sh ]" >nul 2>&1
if errorlevel 1 (
  echo   ERROR: could not find the game at %GAMEDIR% inside Linux.
  echo.
  echo   Did the installer finish? Expected: ~/airline/scripts/run-web.sh
  echo.
  pause
  exit /b 1
)

REM --- if the services are installed, WSL already started them ---------------
set "USING_SERVICES="
wsl -d %DISTRO% -e bash -lc "systemctl is-enabled airline-web.service" >nul 2>&1
if not errorlevel 1 (
  set "USING_SERVICES=1"
  echo   Services are installed - they start with Linux, nothing to launch.
) else (
  echo   Starting the game clock...
  start "Airline - simulation" /min wsl -d %DISTRO% -e bash -lc "cd %GAMEDIR% && ./scripts/run-simulation.sh"
  echo   Starting the web site...
  start "Airline - web site" /min wsl -d %DISTRO% -e bash -lc "cd %GAMEDIR% && ./scripts/run-web.sh"
)

REM --- wait for the site to answer -------------------------------------------
echo.
echo   Waiting for the web site (first start can take a minute)...

set /a TRIES=0
:wait
timeout /t 3 /nobreak >nul
set /a TRIES+=1
curl -s -o nul -m 3 http://localhost:9000 >nul 2>&1
if not errorlevel 1 goto ready
if !TRIES! lss 60 (
  echo   ... still starting ^(!TRIES!/60^)
  goto wait
)

echo.
echo   Gave up after 3 minutes.
if defined USING_SERVICES (
  echo   Check the services:   wsl -d %DISTRO% -e bash -lc "systemctl status airline-web airline-sim"
) else (
  echo   Look at the two minimised windows - there is probably an error in one.
)
echo.
pause
exit /b 1

:ready
echo.
echo   Ready. Opening the browser.
start "" http://localhost:9000

REM Give the browser a moment so the window does not vanish first.
timeout /t 2 /nobreak >nul
exit /b 0
