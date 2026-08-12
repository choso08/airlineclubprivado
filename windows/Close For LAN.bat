@echo off
REM ===========================================================================
REM  Undoes "Open For LAN and Radmin.bat".
REM
REM  Removes the port forwarding and the firewall opening, so the game goes
REM  back to being reachable only from this PC (and from Tailscale, if you
REM  installed it inside Linux - that route does not use any of this).
REM
REM  MUST BE RUN AS ADMINISTRATOR.
REM ===========================================================================
setlocal

set "PORT=9000"

title Airline Club - close to the outside

echo.

net session >nul 2>&1
if errorlevel 1 (
  echo   ERROR: this needs Administrator rights.
  echo   Right-click the file and choose "Run as administrator".
  echo.
  pause
  exit /b 1
)

echo   Removing the port forwarding...
netsh interface portproxy delete v4tov4 listenport=%PORT% listenaddress=0.0.0.0 >nul 2>&1

echo   Removing the firewall opening...
netsh advfirewall firewall delete rule name="Airline Club %PORT%" >nul 2>&1

echo.
echo   Closed. Port %PORT% is no longer reachable from the LAN or Radmin.
echo.
echo   What is still forwarded, if anything:
netsh interface portproxy show v4tov4
echo.
echo   What is listening on this PC beyond localhost (check for surprises):
netstat -ano ^| findstr LISTENING ^| findstr /v "127.0.0.1 [::1]"
echo.
pause
exit /b 0
