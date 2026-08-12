@echo off
REM ===========================================================================
REM  Makes the game reachable from OUTSIDE this PC - your LAN, Radmin VPN,
REM  Hamachi, anything that gives this machine an IP address.
REM
REM  MUST BE RUN AS ADMINISTRATOR (right-click -> Run as administrator).
REM
REM  Why this is needed: the game runs inside WSL2, which sits behind its own
REM  private network. Windows forwards "localhost" into WSL2 automatically,
REM  but nothing else - so a friend connecting to your Radmin address reaches
REM  Windows and stops there. This forwards port 9000 onwards into Linux.
REM
REM  RE-RUN THIS AFTER EVERY RESTART. WSL2 gets a new internal IP each time it
REM  boots, so the forwarding rule goes stale. (Tailscale installed inside
REM  Linux avoids this problem entirely - see WINDOWS-SETUP.md section 6.)
REM
REM  ---------------------------------------------------------------------
REM  THINK BEFORE RUNNING THIS IF YOU RUN ANYTHING ELSE ON THIS PC.
REM
REM  This opens port 9000 on every network interface, and tools like Radmin
REM  VPN or Hamachi put this whole Windows machine on a shared network with
REM  your friends. Anything else listening here - a dashboard, a scraper, a
REM  test server, a file share - is then only as private as the Windows
REM  firewall makes it.
REM
REM  Tailscale installed INSIDE Linux is the safer option: the tailnet member
REM  is the Linux environment, not Windows, so nothing running on Windows is
REM  reachable through it at all.
REM
REM  Check what is exposed with:
REM     netstat -ano ^| findstr LISTENING ^| findstr /v "127.0.0.1 [::1]"
REM
REM  Undo everything this does with "Close For LAN.bat".
REM  ---------------------------------------------------------------------
REM ===========================================================================
setlocal EnableDelayedExpansion

set "DISTRO=Ubuntu"
set "PORT=9000"

title Airline Club - open for LAN / Radmin

echo.

REM --- must be admin ---------------------------------------------------------
net session >nul 2>&1
if errorlevel 1 (
  echo   ERROR: this needs Administrator rights.
  echo.
  echo   Close this, right-click the file and choose "Run as administrator".
  echo.
  pause
  exit /b 1
)

REM --- find the WSL2 address -------------------------------------------------
echo   Looking up the Linux address...
set "WSLIP="
for /f "usebackq tokens=1" %%i in (`wsl -d %DISTRO% -e hostname -I 2^>nul`) do (
  if not defined WSLIP set "WSLIP=%%i"
)

if not defined WSLIP (
  echo   ERROR: could not reach the "%DISTRO%" distribution.
  echo   Is it running? Try:  wsl -d %DISTRO% -e true
  echo.
  pause
  exit /b 1
)
echo   Linux is at !WSLIP!

REM --- redirect port 9000 into WSL2 -----------------------------------------
echo   Forwarding port %PORT% into Linux...
netsh interface portproxy delete v4tov4 listenport=%PORT% listenaddress=0.0.0.0 >nul 2>&1
netsh interface portproxy add v4tov4 listenport=%PORT% listenaddress=0.0.0.0 connectport=%PORT% connectaddress=!WSLIP! >nul
if errorlevel 1 (
  echo   ERROR: could not create the forwarding rule.
  pause
  exit /b 1
)

REM --- let it through the firewall ------------------------------------------
echo   Allowing it through the Windows firewall...
netsh advfirewall firewall delete rule name="Airline Club %PORT%" >nul 2>&1
netsh advfirewall firewall add rule name="Airline Club %PORT%" dir=in action=allow protocol=TCP localport=%PORT% >nul

echo.
echo   Done. Your friends can now reach the game at:
echo.

REM --- show the addresses they can use --------------------------------------
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  set "ADDR=%%a"
  set "ADDR=!ADDR: =!"
  echo        http://!ADDR!:%PORT%
)

echo.
echo   The Radmin one usually starts with 26.
echo.
echo   REMEMBER: run this again after restarting the PC.
echo.
pause
exit /b 0
