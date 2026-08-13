# Let the rest of the house reach the game.
#
#   Right-click -> Run with PowerShell   (as Administrator)
#   or:  powershell -ExecutionPolicy Bypass -File scripts\lan-access.ps1
#
# The game runs inside WSL2, which has a network of its own: services in there
# answer Windows on localhost and nobody else. This forwards the Windows port
# to the WSL one and opens the firewall for it, so anyone on your own network
# can play by typing your machine's address.
#
# It has to be re-run after a reboot, because WSL2 takes a new address every
# time Windows starts and the forward then points at nothing. -Install sets up
# a task that does that for you at logon:
#
#   powershell -ExecutionPolicy Bypass -File scripts\lan-access.ps1 -Install
#
# What this exposes: port 9000, to your own network only, and nothing else.
# Not the machine, not your other work, and nothing to the internet - your
# router does not forward anything inbound unless you tell it to.
param(
    [int]$Port = 9000,
    [switch]$Install,
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
$rule = 'Airline Club LAN'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Write-Host "This needs to run as Administrator - the port forward and the" -ForegroundColor Red
        Write-Host "firewall rule both do." -ForegroundColor Red
        exit 1
    }
}

Assert-Administrator

if ($Remove) {
    netsh interface portproxy delete v4tov4 listenport=$Port listenaddress=0.0.0.0 | Out-Null
    Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Unregister-ScheduledTask -TaskName 'Airline Club LAN' -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Removed. The game is reachable from this machine only again." -ForegroundColor Yellow
    exit 0
}

# WSL reports every address it has; the first is the one Windows talks to.
$wslAddress = (wsl hostname -I).Trim().Split(' ')[0]
if (-not $wslAddress) {
    Write-Host "Could not find WSL's address. Is the Ubuntu window open, or WSL running?" -ForegroundColor Red
    exit 1
}

# Delete before adding: netsh keeps the old entry otherwise, and the old one is
# the stale address that made this stop working.
netsh interface portproxy delete v4tov4 listenport=$Port listenaddress=0.0.0.0 2>$null | Out-Null
netsh interface portproxy add v4tov4 listenport=$Port listenaddress=0.0.0.0 connectport=$Port connectaddress=$wslAddress | Out-Null

# Private only. On a network Windows has marked Public - a cafe, a hotel, a
# phone's hotspot - this rule deliberately does not apply.
if (-not (Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $rule -Direction Inbound -Protocol TCP `
        -LocalPort $Port -Action Allow -Profile Private | Out-Null
}

if ($Install) {
    # At logon rather than at boot: WSL is not up before somebody signs in, so
    # a boot task would read an address that does not exist yet.
    $script = $MyInvocation.MyCommand.Path
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' `
        -Argument "-ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" -Port $Port"
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    Register-ScheduledTask -TaskName 'Airline Club LAN' -Action $action -Trigger $trigger `
        -RunLevel Highest -Force | Out-Null
    Write-Host "Set to run itself at every logon." -ForegroundColor Green
}

$lan = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '172.*' -and
                       $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1).IPAddress

Write-Host ""
Write-Host "  Forwarding port $Port to WSL at $wslAddress" -ForegroundColor Green
Write-Host ""
if ($lan) {
    Write-Host "  Everyone in the house opens:  http://${lan}:$Port" -ForegroundColor Cyan
} else {
    Write-Host "  Run ipconfig to find this machine's address; that plus :$Port is the link."
}
Write-Host ""
Write-Host "  Re-run this after a reboot, or use -Install once and forget it."
Write-Host "  To undo everything:  -Remove"
Write-Host ""
