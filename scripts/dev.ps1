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

$Api = Start-Process `
    -FilePath (Join-Path $Root "services/api/gradlew.bat") `
    -ArgumentList "bootRun" `
    -WorkingDirectory (Join-Path $Root "services/api") `
    -PassThru

$Web = Start-Process `
    -FilePath "npm.cmd" `
    -ArgumentList "run", "dev" `
    -WorkingDirectory (Join-Path $Root "apps/web") `
    -PassThru

try {
    Wait-Process -Id $Api.Id, $Web.Id
} finally {
    Stop-Process -Id $Api.Id, $Web.Id -Force -ErrorAction SilentlyContinue
}
