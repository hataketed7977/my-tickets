$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $Line = $_.Trim()
        if ($Line -and -not $Line.StartsWith("#")) {
            $Name, $Value = $Line -split "=", 2
            Set-Item -Path "Env:$Name" -Value $Value
        }
    }
}

$CaddyVersion = (Get-Content (Join-Path $Root "tools\caddy\VERSION") -Raw).Trim()
$ArchiveDir = Join-Path $Root "tools\caddy\v$CaddyVersion"
$RuntimeRoot = Join-Path $Root ".local-tools\caddy\$CaddyVersion"
$CaddySetupPort = if ($env:CADDY_SETUP_PORT) { $env:CADDY_SETUP_PORT } else { "54443" }
$env:XDG_DATA_HOME = Join-Path $RuntimeRoot "data"
$env:XDG_CONFIG_HOME = Join-Path $RuntimeRoot "config"

$Architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
switch ($Architecture) {
    "X64" { $Platform = "windows_amd64" }
    "Arm64" { $Platform = "windows_arm64" }
    default { throw "Unsupported Windows architecture: $Architecture" }
}

$ArchiveName = "caddy_${CaddyVersion}_${Platform}.zip"
$Archive = Join-Path $ArchiveDir $ArchiveName
$Checksums = Join-Path $ArchiveDir "caddy_${CaddyVersion}_checksums.txt"
$RuntimeDir = Join-Path $RuntimeRoot $Platform
$Caddy = Join-Path $RuntimeDir "caddy.exe"
$TrustConfig = Join-Path $Root "tools\caddy\trust.Caddyfile"

if (-not (Test-Path $Archive)) {
    throw "Missing bundled archive: $Archive"
}
if (-not (Test-Path $Checksums)) {
    throw "Missing bundled checksums: $Checksums"
}

$ChecksumLine = Get-Content $Checksums |
    Where-Object { $_ -match "\s+$([regex]::Escape($ArchiveName))$" } |
    Select-Object -First 1
if (-not $ChecksumLine) {
    throw "No checksum found for $ArchiveName"
}

$Expected = ($ChecksumLine -split "\s+")[0].ToLowerInvariant()
$Actual = (Get-FileHash -Algorithm SHA512 -Path $Archive).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) {
    throw "Checksum mismatch for $ArchiveName"
}

New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
New-Item -ItemType Directory -Force -Path $env:XDG_DATA_HOME | Out-Null
New-Item -ItemType Directory -Force -Path $env:XDG_CONFIG_HOME | Out-Null
$ExtractDir = Join-Path $RuntimeDir "extract"
Remove-Item -Recurse -Force $ExtractDir -ErrorAction SilentlyContinue
Expand-Archive -Path $Archive -DestinationPath $ExtractDir -Force
Copy-Item -Force (Join-Path $ExtractDir "caddy.exe") $Caddy
Copy-Item -Force (Join-Path $ExtractDir "LICENSE") (Join-Path $RuntimeDir "LICENSE")
Remove-Item -Recurse -Force $ExtractDir

$env:CADDY_SETUP_PORT = $CaddySetupPort

& $Caddy validate --config $TrustConfig --adapter caddyfile
if ($LASTEXITCODE -ne 0) {
    throw "Caddy trust configuration validation failed"
}

$Listener = Get-NetTCPConnection -LocalPort ([int]$CaddySetupPort) -State Listen -ErrorAction SilentlyContinue
if ($Listener) {
    throw "Port $CaddySetupPort is in use. Stop the listener and rerun this setup."
}

try {
    & $Caddy start --config $TrustConfig --adapter caddyfile
    if ($LASTEXITCODE -ne 0) {
        throw "Caddy failed to start"
    }

    & $Caddy trust --config $TrustConfig --adapter caddyfile
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to trust the local CA. Rerun PowerShell as Administrator."
    }

    $Client = [System.Net.Http.HttpClient]::new()
    try {
        $Response = $Client.GetAsync("https://localhost:$CaddySetupPort/").GetAwaiter().GetResult()
        $Response.Dispose()
    } finally {
        $Client.Dispose()
    }
} finally {
    & $Caddy stop *> $null
}

Write-Host "[OK] Local HTTPS trust is prepared for localhost."
Write-Host "[INFO] Restart browsers and desktop clients before testing."
