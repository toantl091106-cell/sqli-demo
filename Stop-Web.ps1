. (Join-Path $PSScriptRoot 'Web-Launcher.Common.ps1')
$demoConfiguration = Get-DemoConfiguration
try {
    $demoListener = Get-DemoListener $demoConfiguration.Port
    if ($demoListener) {
        $demoPid = $demoListener.OwningProcess
    } elseif (Test-Path -LiteralPath $demoConfiguration.PidFile) {
        $demoPid = [int](Get-Content -LiteralPath $demoConfiguration.PidFile -Raw).Trim()
    } else {
        Write-Host 'This copy is already stopped.'
        exit 0
    }
    $demoProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$demoPid"
    if ($demoProcess) {
        if (!(Test-DemoProcess $demoProcess $demoConfiguration.Jar)) {
            throw 'The port/PID belongs to another program/copy; it was not stopped.'
        }
        Stop-Process -Id $demoPid
        Write-Host 'SQLi Demo web stopped. MySQL and database data are preserved.'
    } else { Write-Host 'This copy is already stopped.' }
    Remove-Item -LiteralPath $demoConfiguration.PidFile -Force -ErrorAction SilentlyContinue
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
