@echo off
REM ===========================================================================
REM  Who is connected to your private network, and which address to give out.
REM ===========================================================================
setlocal

set "DISTRO=Ubuntu"
set "PORT=9000"

title Tailscale status

echo.
echo   ===== Tailscale =====
echo.

wsl -d %DISTRO% -e true >nul 2>&1
if errorlevel 1 (
  echo   Linux is not running.
  echo.
  pause
  exit /b 1
)

wsl -d %DISTRO% -e bash -lc "command -v tailscale >/dev/null" >nul 2>&1
if errorlevel 1 (
  echo   Tailscale is not installed. Run "Tailscale Setup.bat" first.
  echo.
  pause
  exit /b 1
)

echo   --- Service ---
wsl -d %DISTRO% -e bash -lc "systemctl is-active tailscaled 2>/dev/null || echo 'not running under systemd'"

echo.
echo   --- Connected devices ---
wsl -d %DISTRO% -e bash -lc "tailscale status 2>&1 || echo '  (not signed in - run Tailscale Setup.bat)'"

echo.
echo   --- What is published ---
wsl -d %DISTRO% -e bash -lc "tailscale serve status 2>/dev/null || echo '  (nothing published - only the raw port is reachable)'"

echo.
echo   --- Address to share ---
wsl -d %DISTRO% -e bash -lc "tailscale ip -4 2>/dev/null | head -1 | sed 's|^|     http://|; s|$|:%PORT%|' || echo '     (unknown)'"

echo.
echo   --- Is the game actually up? ---
curl -s -o nul -m 5 http://localhost:%PORT% >nul 2>&1
if errorlevel 1 (
  echo   NO - start it with Start Airline.bat, or nobody can connect.
) else (
  echo   YES - the game is running.
)

echo.
pause
exit /b 0
