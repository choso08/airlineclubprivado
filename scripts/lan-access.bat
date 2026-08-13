@echo off
setlocal enabledelayedexpansion
rem ===========================================================================
rem  Let the rest of the house reach the game.
rem
rem    Double-click it.            forwards the port now
rem    lan-access.bat install      and does it again at every logon
rem    lan-access.bat remove       undoes all of it
rem
rem  The game runs inside WSL2, which has a network of its own: things in there
rem  answer Windows on localhost and nobody else. This forwards the Windows
rem  port to the WSL one and opens the firewall for it, so anybody on your own
rem  network can play by typing this machine's address.
rem
rem  It asks for Administrator because the port forward and the firewall rule
rem  both need it. It exposes port 9000 to your own network and nothing else -
rem  not the machine, not anything else on it, and nothing to the internet.
rem ===========================================================================

set PORT=9000
set RULE=Airline Club LAN
set TASK=Airline Club LAN

rem -- Administrator, or ask for it -------------------------------------------
rem  net session fails for a normal user and succeeds for an administrator; it
rem  is the check that needs nothing installed.
net session >nul 2>&1
if not "%errorlevel%"=="0" (
    echo Asking for Administrator...
    powershell -Command "Start-Process -FilePath '%~f0' -ArgumentList '%1' -Verb RunAs"
    exit /b
)

if /i "%1"=="remove" goto remove

rem -- where WSL is today ------------------------------------------------------
rem  WSL prints every address it holds; the first is the one Windows talks to.
rem  It is a new one after every reboot, which is why this has to be re-run.
set WSLIP=
for /f "tokens=1" %%a in ('wsl hostname -I 2^>nul') do set WSLIP=%%a

if "%WSLIP%"=="" (
    echo.
    echo   Could not find WSL's address.
    echo   Open the Ubuntu window once, then run this again.
    echo.
    pause
    exit /b 1
)

rem -- the forward -------------------------------------------------------------
rem  Deleted before it is added: netsh keeps the old entry otherwise, and the
rem  old entry holds the address WSL had before the last reboot. That stale
rem  entry is exactly what makes this look like it stopped working.
netsh interface portproxy delete v4tov4 listenport=%PORT% listenaddress=0.0.0.0 >nul 2>&1
netsh interface portproxy add v4tov4 listenport=%PORT% listenaddress=0.0.0.0 connectport=%PORT% connectaddress=%WSLIP% >nul

rem -- the firewall ------------------------------------------------------------
rem  Private profile only. On a network Windows has marked Public - a hotel, a
rem  phone's hotspot - this deliberately does not apply.
netsh advfirewall firewall delete rule name="%RULE%" >nul 2>&1
netsh advfirewall firewall add rule name="%RULE%" dir=in action=allow protocol=TCP localport=%PORT% profile=private >nul

rem -- this machine's address on the network ------------------------------------
rem  Skipping 172.* because that is WSL's own, and it is not the one to hand out.
set LANIP=
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set CANDIDATE=%%a
    set CANDIDATE=!CANDIDATE: =!
    if "!LANIP!"=="" (
        echo !CANDIDATE! | findstr /b "172. 127." >nul || set LANIP=!CANDIDATE!
    )
)

if /i "%1"=="install" (
    rem At logon rather than at boot: WSL is not up before somebody signs in,
    rem so a boot task would read an address that does not exist yet.
    schtasks /create /tn "%TASK%" /tr "\"%~f0\"" /sc onlogon /rl highest /f >nul
    echo   Set to run itself at every logon.
)

echo.
echo   Forwarding port %PORT% to WSL at %WSLIP%
echo.
if not "%LANIP%"=="" (
    echo   Everyone in the house opens:  http://%LANIP%:%PORT%
) else (
    echo   Run ipconfig to find this machine's address; that plus :%PORT% is the link.
)
echo.
echo   Run this again after a reboot, or once with:  lan-access.bat install
echo   To undo everything:                           lan-access.bat remove
echo.
pause
exit /b 0

:remove
netsh interface portproxy delete v4tov4 listenport=%PORT% listenaddress=0.0.0.0 >nul 2>&1
netsh advfirewall firewall delete rule name="%RULE%" >nul 2>&1
schtasks /delete /tn "%TASK%" /f >nul 2>&1
echo.
echo   Removed. The game is reachable from this machine only again.
echo.
pause
exit /b 0
