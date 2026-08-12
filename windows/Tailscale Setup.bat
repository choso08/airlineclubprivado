@echo off
REM ===========================================================================
REM  Sets up Tailscale INSIDE Linux, so friends can reach the game from
REM  anywhere without opening any router ports.
REM
REM  Run this once. After that it just reports the status.
REM
REM  Why inside Linux and not on Windows: the machine that joins your private
REM  network is then the Linux environment, which contains only the game.
REM  Anything else running on this PC is not reachable through it at all.
REM ===========================================================================
setlocal EnableDelayedExpansion

set "DISTRO=Ubuntu"
set "HOSTNAME=airline"
set "PORT=9000"

title Tailscale setup

echo.
echo   ===== Tailscale setup =====
echo.

REM --- WSL there? ------------------------------------------------------------
wsl -d %DISTRO% -e true >nul 2>&1
if errorlevel 1 (
  echo   ERROR: could not start the WSL distribution "%DISTRO%".
  echo   Check the name with:  wsl -l -v
  echo.
  pause
  exit /b 1
)

REM --- already installed? ----------------------------------------------------
wsl -d %DISTRO% -e bash -lc "command -v tailscale >/dev/null" >nul 2>&1
if errorlevel 1 (
  echo   Installing Tailscale inside Linux...
  echo   ^(this will ask for your LINUX password^)
  echo.
  wsl -d %DISTRO% -e bash -lc "curl -fsSL https://tailscale.com/install.sh | sh"
  if errorlevel 1 (
    echo.
    echo   ERROR: the install failed. Is the network blocking tailscale.com?
    echo.
    pause
    exit /b 1
  )
) else (
  echo   Tailscale is already installed.
)

REM --- is the background service alive? --------------------------------------
echo.
echo   Checking the Tailscale service...
wsl -d %DISTRO% -e bash -lc "systemctl is-active --quiet tailscaled" >nul 2>&1
if errorlevel 1 (
  echo   Starting it...
  wsl -d %DISTRO% -e bash -lc "sudo systemctl enable --now tailscaled" 2>nul
  wsl -d %DISTRO% -e bash -lc "systemctl is-active --quiet tailscaled" >nul 2>&1
  if errorlevel 1 (
    echo.
    echo   WARNING: tailscaled is not running under systemd.
    echo   Turn systemd on - see WINDOWS-SETUP.md, "Install WSL2 and Ubuntu".
    echo.
  )
) else (
  echo   Running.
)

REM --- already logged in? ----------------------------------------------------
echo.
wsl -d %DISTRO% -e bash -lc "tailscale status >/dev/null 2>&1" >nul 2>&1
if not errorlevel 1 (
  echo   Already signed in - skipping login.
  goto serve
)

echo   ------------------------------------------------------------------
echo    A LINK will appear below. Open it to sign in.
echo.
echo    If this PC's browser gives certificate errors, open the link on
echo    your PHONE instead - it works just the same.
echo   ------------------------------------------------------------------
echo.

REM --accept-dns=false: Tailscale's DNS handling fights with the way WSL2
REM rewrites /etc/resolv.conf, which can break name resolution inside Linux.
REM Your friends can still reach this machine by name from their own devices.
wsl -d %DISTRO% -e bash -lc "sudo tailscale up --hostname=%HOSTNAME% --accept-dns=false"
if errorlevel 1 (
  echo.
  echo   Login did not complete. Run this file again to retry.
  echo.
  pause
  exit /b 1
)

:serve
REM --- expose ONLY the game port --------------------------------------------
echo.
echo   Publishing just port %PORT% to your private network...
wsl -d %DISTRO% -e bash -lc "sudo tailscale serve --bg %PORT%" 2>nul

REM --- report ----------------------------------------------------------------
echo.
echo   ===== Done =====
echo.
echo   Your address ^(the https line below^):
wsl -d %DISTRO% -e bash -lc "tailscale serve status 2>/dev/null || echo '     (nothing published)'"
echo.
echo   Or by number, which always works:
wsl -d %DISTRO% -e bash -lc "tailscale ip -4 2>/dev/null | head -1 | sed 's|^|     http://|; s|$|:%PORT%|'"
echo.
echo   Give your friends these steps:
echo     1. install Tailscale     https://tailscale.com/download
echo     2. accept your invite    https://login.tailscale.com/admin/users
echo     3. open the address above and click Sign Up
echo.
echo   The game must be running for them to get in - Start Airline.bat
echo.
pause
exit /b 0
