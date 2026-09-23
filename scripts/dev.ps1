$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$StateDir = if ($env:MY_TICKETS_STATE_DIR) { $env:MY_TICKETS_STATE_DIR } else { Join-Path $env:TEMP "my-tickets" }
$EnvFile = Join-Path $Root ".env"

$StartedServices = New-Object System.Collections.Generic.List[string]
$StartedPids = New-Object System.Collections.Generic.List[int]
$RunPidFiles = New-Object System.Collections.Generic.List[string]

function Write-Info($Message) { Write-Host "[INFO]  $Message" }
function Write-Success($Message) { Write-Host "[OK]    $Message" }
function Write-Warn($Message) { Write-Host "[WARN]  $Message" }
function Write-Fail($Message) { Write-Host "[FAIL]  $Message" }

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $Line = $_.Trim()
        if ($Line -and -not $Line.StartsWith("#")) {
            $Name, $Value = $Line -split "=", 2
            Set-Item -Path "Env:$Name" -Value $Value
        }
    }
}

$HttpsPort = if ($env:HTTPS_PORT) { $env:HTTPS_PORT } else { "55888" }
$ApiPort = if ($env:API_PORT) { $env:API_PORT } else { "15588" }
$WebPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { "51888" }
$PublicBaseUrl = if ($env:PUBLIC_BASE_URL) { $env:PUBLIC_BASE_URL.TrimEnd("/") } else { "https://localhost:$HttpsPort" }

$env:HTTPS_PORT = $HttpsPort
$env:API_PORT = $ApiPort
$env:PORT = $ApiPort
$env:WEB_PORT = $WebPort
$env:PUBLIC_BASE_URL = $PublicBaseUrl

if (-not $env:CLIENT_ORIGIN) {
    $env:CLIENT_ORIGIN = $PublicBaseUrl
}
if (-not $env:WEB_BASE_URL) {
    $env:WEB_BASE_URL = $PublicBaseUrl
}
if (-not $env:VITE_API_BASE_URL) {
    $env:VITE_API_BASE_URL = $PublicBaseUrl
}
if (-not $env:COOKIE_SECURE) {
    $env:COOKIE_SECURE = "true"
}
if (-not $env:FEISHU_REDIRECT_URI) {
    $env:FEISHU_REDIRECT_URI = "$PublicBaseUrl/api/auth/feishu/callback"
}

$CaddyVersion = (Get-Content (Join-Path $Root "tools\caddy\VERSION") -Raw).Trim()
$CaddyRuntimeRoot = Join-Path $Root ".local-tools\caddy\$CaddyVersion"
$env:XDG_DATA_HOME = Join-Path $CaddyRuntimeRoot "data"
$env:XDG_CONFIG_HOME = Join-Path $CaddyRuntimeRoot "config"
$Architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
switch ($Architecture) {
    "X64" { $CaddyPlatform = "windows_amd64" }
    "Arm64" { $CaddyPlatform = "windows_arm64" }
    default { throw "Unsupported Windows architecture: $Architecture" }
}
$Caddy = Join-Path $CaddyRuntimeRoot "$CaddyPlatform\caddy.exe"
$CaddyConfig = Join-Path $Root "tools\caddy\dev.Caddyfile"

function Ensure-StateDir {
    New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
}

function Rotate-Log($LogFile) {
    if (Test-Path $LogFile) {
        Move-Item -Force $LogFile "$LogFile.prev"
    }
}

function Get-PortProcessIds($Port) {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique)
    }

    $Rows = netstat -ano -p tcp | Select-String "LISTENING" | Select-String ":$Port "
    return @($Rows | ForEach-Object {
        $Parts = ($_ -split '\s+') | Where-Object { $_ }
        if ($Parts.Length -gt 0) { [int]$Parts[-1] }
    } | Select-Object -Unique)
}

function Stop-ProcessTree($ProcessId) {
    if (-not $ProcessId) {
        return
    }

    if (Get-Command taskkill.exe -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $ProcessId /T /F *> $null
        return
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-PortListeners($Port) {
    $Processes = @(Get-PortProcessIds $Port | Where-Object { $_ })
    if ($Processes.Count -eq 0) {
        return
    }

    Write-Warn "Stopping process(es) on port ${Port}: $($Processes -join ' ')"
    foreach ($ProcessId in $Processes) {
        Stop-ProcessTree $ProcessId
    }

    for ($Attempt = 0; $Attempt -lt 20; $Attempt++) {
        $Remaining = @(Get-PortProcessIds $Port | Where-Object { $_ })
        if ($Remaining.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
}

function Test-ServiceRunning($Name) {
    for ($Index = 0; $Index -lt $StartedServices.Count; $Index++) {
        if ($StartedServices[$Index] -eq $Name) {
            return [bool](Get-Process -Id $StartedPids[$Index] -ErrorAction SilentlyContinue)
        }
    }

    return $false
}

function Write-LogTail($LogFile, $MaxLines) {
    if (-not (Test-Path $LogFile)) {
        return
    }

    Get-Content -Path $LogFile -Tail $MaxLines -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "        $_"
    }
}

function Wait-ForHttp($Url, $Label, $LogFile, $MaxSeconds) {
    $Elapsed = 0
    while ($Elapsed -lt $MaxSeconds) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            Write-Success "$Label is ready ($Url)"
            return
        } catch {
            if (-not (Test-ServiceRunning $Label)) {
                Write-Fail "$Label exited before becoming ready. Last log lines:"
                Write-LogTail $LogFile 30
                exit 1
            }

            Start-Sleep -Seconds 2
            $Elapsed += 2
        }
    }

    Write-Fail "$Label did not become ready in ${MaxSeconds}s ($Url). Last log lines:"
    Write-LogTail $LogFile 30
    exit 1
}

function New-CmdWrapper($Name, $Directory, $CommandLine, $LogFile) {
    $CmdFile = Join-Path $StateDir "$Name.cmd"
    $Lines = New-Object System.Collections.Generic.List[string]
    $Lines.Add("@echo off")
    $Lines.Add("cd /d `"$Directory`"")
    $Lines.Add("call $CommandLine > `"$LogFile`" 2>&1")
    Set-Content -Path $CmdFile -Value $Lines -Encoding ASCII
    return $CmdFile
}

function Start-ServiceProcess($Name, $Directory, $LogFile, $PidFile, $CommandLine) {
    Rotate-Log $LogFile

    Write-Info "Starting $Name..."
    $CmdFile = New-CmdWrapper $Name $Directory $CommandLine $LogFile
    $Process = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "`"$CmdFile`"") -WindowStyle Hidden -PassThru
    Set-Content -Path $PidFile -Value $Process.Id -Encoding ASCII

    $StartedServices.Add($Name) | Out-Null
    $StartedPids.Add($Process.Id) | Out-Null
    $RunPidFiles.Add($PidFile) | Out-Null
    Write-Success "$Name started (pid $($Process.Id), log $LogFile)"
}

function Cleanup-StartedServices {
    if ($StartedPids.Count -eq 0) {
        return
    }

    Write-Info "Stopping services started by this run..."

    for ($Index = $StartedPids.Count - 1; $Index -ge 0; $Index--) {
        $ProcessId = $StartedPids[$Index]
        $Name = $StartedServices[$Index]
        Write-Info "Stopping $Name (pid $ProcessId)"
        Stop-ProcessTree $ProcessId
    }

    for ($Index = 0; $Index -lt $RunPidFiles.Count; $Index++) {
        $PidFile = $RunPidFiles[$Index]
        $ProcessId = $StartedPids[$Index]
        if (Test-Path $PidFile) {
            $CurrentPid = Get-Content -Path $PidFile -Raw -ErrorAction SilentlyContinue
            if ($CurrentPid.Trim() -eq "$ProcessId") {
                Remove-Item -Force $PidFile
            }
        }
    }

    $StartedServices.Clear()
    $StartedPids.Clear()
    $RunPidFiles.Clear()
}

function Print-SuccessSummary($ApiLog, $WebLog, $CaddyLog, $ApiPidFile, $WebPidFile, $CaddyPidFile) {
    Write-Host ""
    Write-Success "ticket center local development is ready"
    Write-Host ""
    Write-Host "Services:"
    Write-Host "  Web: $PublicBaseUrl"
    Write-Host "  MCP: $PublicBaseUrl/mcp"
    Write-Host "  API (internal): http://localhost:$ApiPort"
    Write-Host "  Web (internal): http://localhost:$WebPort"
    Write-Host ""
    Write-Host "Logs:"
    Write-Host "  API: $ApiLog"
    Write-Host "  Web: $WebLog"
    Write-Host "  Caddy: $CaddyLog"
    Write-Host ""
    Write-Host "PID files:"
    Write-Host "  $ApiPidFile"
    Write-Host "  $WebPidFile"
    Write-Host "  $CaddyPidFile"
    Write-Host ""
}

function Supervise-Foreground {
    Write-Info "Foreground supervision is active. Press Ctrl+C to stop started services."

    while ($true) {
        for ($Index = 0; $Index -lt $StartedPids.Count; $Index++) {
            $ProcessId = $StartedPids[$Index]
            $Name = $StartedServices[$Index]
            $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
            if (-not $Process) {
                Write-Fail "$Name exited. See $(Join-Path $StateDir "$Name.log")"
                Cleanup-StartedServices
                exit 1
            }
        }
        Start-Sleep -Seconds 2
    }
}

function Main {
    Ensure-StateDir

    $ApiPidFile = Join-Path $StateDir "api.pid"
    $WebPidFile = Join-Path $StateDir "web.pid"
    $CaddyPidFile = Join-Path $StateDir "caddy.pid"
    $ApiLog = Join-Path $StateDir "api.log"
    $WebLog = Join-Path $StateDir "web.log"
    $CaddyLog = Join-Path $StateDir "caddy.log"
    $ApiDir = Join-Path $Root "services/api"
    $WebDir = Join-Path $Root "apps/web"

    if ($HttpsPort -eq $ApiPort -or $HttpsPort -eq $WebPort -or $ApiPort -eq $WebPort) {
        throw "HTTPS_PORT, API_PORT, and WEB_PORT must be different."
    }
    if (-not (Test-Path $Caddy)) {
        throw "Bundled Caddy is not prepared. Run .\scripts\setup-https.ps1 once."
    }
    & $Caddy validate --config $CaddyConfig --adapter caddyfile
    if ($LASTEXITCODE -ne 0) {
        throw "Caddy development configuration validation failed."
    }

    Stop-PortListeners ([int]$HttpsPort)
    Stop-PortListeners ([int]$ApiPort)
    Stop-PortListeners ([int]$WebPort)
    Remove-Item -Force $ApiPidFile, $WebPidFile, $CaddyPidFile -ErrorAction SilentlyContinue

    try {
        Start-ServiceProcess "api" $ApiDir $ApiLog $ApiPidFile ".\gradlew.bat bootRun"
        Wait-ForHttp "http://localhost:$ApiPort/api/auth/config" "api" $ApiLog 120

        Start-ServiceProcess "web" $WebDir $WebLog $WebPidFile "npm.cmd run dev"
        Wait-ForHttp "http://localhost:$WebPort/" "web" $WebLog 60

        $CaddyCommand = "`"$Caddy`" run --config `"$CaddyConfig`" --adapter caddyfile"
        Start-ServiceProcess "caddy" $Root $CaddyLog $CaddyPidFile $CaddyCommand
        Wait-ForHttp "$PublicBaseUrl/api/auth/config" "caddy" $CaddyLog 30

        Print-SuccessSummary $ApiLog $WebLog $CaddyLog $ApiPidFile $WebPidFile $CaddyPidFile
        Supervise-Foreground
    } catch {
        Write-Fail $_.Exception.Message
        Cleanup-StartedServices
        exit 1
    } finally {
        Cleanup-StartedServices
    }
}

Main
