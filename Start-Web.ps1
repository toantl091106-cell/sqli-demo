param(
    [switch]$NoBrowser,
    [ValidateRange(5, 180)][int]$StartupTimeoutSeconds = 60
)

. (Join-Path $PSScriptRoot 'Web-Launcher.Common.ps1')
$demoConfiguration = Get-DemoConfiguration
$demoListener = Get-DemoListener $demoConfiguration.Port
$demoStartedProcess = $null
try {
    if ($demoListener) {
        $demoProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($demoListener.OwningProcess)"
        if (!(Test-DemoProcess $demoProcess $demoConfiguration.Jar)) {
            throw "Port $($demoConfiguration.Port) belongs to another program/copy. Choose another SQLI_HTTP_PORT; no process was stopped."
        }
        Write-Host 'This copy is already running. Opening its web page.'
    } else {
        $demoJdk = Find-DemoJdk
        $env:JAVA_HOME = $demoJdk.Home
        $env:PATH = (Join-Path $demoJdk.Home 'bin') + ';' + $env:PATH
        $env:SQLI_HTTP_ADDRESS = $demoConfiguration.Address
        $env:SQLI_HTTP_PORT = [string]$demoConfiguration.Port
        Write-Host "Using JDK $($demoJdk.Major). Building the web from source..."
        Write-Host 'The first build needs Internet access to download Maven and dependencies.'
        Push-Location -LiteralPath $demoConfiguration.Code
        try {
            & '.\mvnw.cmd' '-DskipTests' 'package'
            if ($LASTEXITCODE -ne 0) { throw 'Maven build failed. Read the build errors above; web was not started.' }
        } finally { Pop-Location }
        if (!(Test-Path -LiteralPath $demoConfiguration.Jar)) { throw 'Maven did not produce the expected web JAR.' }
        New-Item -ItemType Directory -Path $demoConfiguration.Runtime -Force | Out-Null
        $demoStartedProcess = Start-Process -FilePath $demoJdk.Java -ArgumentList @('-jar', ('"' + $demoConfiguration.Jar + '"')) -WorkingDirectory $demoConfiguration.Code -WindowStyle Hidden -RedirectStandardOutput $demoConfiguration.Stdout -RedirectStandardError $demoConfiguration.Stderr -PassThru
        $demoStartedProcess.Id | Set-Content -LiteralPath $demoConfiguration.PidFile
        $demoProcess = [pscustomobject]@{ ProcessId = $demoStartedProcess.Id }
        Write-Host 'Waiting for the web and MySQL connection...'
    }
    Wait-DemoReady $demoConfiguration $demoProcess $StartupTimeoutSeconds
    Write-Host "Web ready: $($demoConfiguration.Url)"
    Write-Host 'The web runs in the background. Use Dung-web.cmd to stop it; database data is preserved.'
    if (!$NoBrowser) { Start-Process -FilePath $demoConfiguration.Url -WindowStyle Hidden }
} catch {
    if ($demoStartedProcess -and !( $demoStartedProcess.HasExited )) {
        Stop-Process -Id $demoStartedProcess.Id -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $demoConfiguration.PidFile -Force -ErrorAction SilentlyContinue
    }
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
