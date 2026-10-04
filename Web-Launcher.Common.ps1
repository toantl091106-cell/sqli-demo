$ErrorActionPreference = 'Stop'

function Get-DemoConfiguration {
    $demoPort = 8081
    if ($env:SQLI_HTTP_PORT -and
        (![int]::TryParse($env:SQLI_HTTP_PORT, [ref]$demoPort) -or $demoPort -lt 1 -or $demoPort -gt 65535)) {
        throw 'SQLI_HTTP_PORT must be an integer from 1 to 65535.'
    }
    $demoAddress = if ($env:SQLI_HTTP_ADDRESS) { $env:SQLI_HTTP_ADDRESS } else { '127.0.0.1' }
    if ($demoAddress -notin @('127.0.0.1', 'localhost', '::1')) {
        throw 'This local lab launcher uses a loopback address: 127.0.0.1, localhost or ::1.'
    }
    $demoHost = if ($demoAddress -eq '::1') { '[::1]' } else { $demoAddress }
    $demoRuntime = Join-Path $PSScriptRoot 'runtime'
    [pscustomobject]@{
        Port = $demoPort
        Address = $demoAddress
        Url = "http://${demoHost}:$demoPort"
        Code = Join-Path $PSScriptRoot 'java-mysql-demo\code'
        Jar = Join-Path $PSScriptRoot 'java-mysql-demo\code\target\sqli-web-demo-0.0.1-SNAPSHOT.jar'
        Runtime = $demoRuntime
        PidFile = Join-Path $demoRuntime "web-$demoPort.pid"
        Stdout = Join-Path $demoRuntime "web-$demoPort.stdout.log"
        Stderr = Join-Path $demoRuntime "web-$demoPort.stderr.log"
    }
}

function Invoke-DemoJavaProbe([string]$Executable, [string]$Arguments) {
    $demoProbe = New-Object System.Diagnostics.Process
    $demoProbe.StartInfo.FileName = $Executable
    $demoProbe.StartInfo.Arguments = $Arguments
    $demoProbe.StartInfo.UseShellExecute = $false
    $demoProbe.StartInfo.CreateNoWindow = $true
    $demoProbe.StartInfo.RedirectStandardOutput = $true
    $demoProbe.StartInfo.RedirectStandardError = $true
    try {
        $null = $demoProbe.Start()
        $demoOutput = $demoProbe.StandardOutput.ReadToEndAsync()
        $demoError = $demoProbe.StandardError.ReadToEndAsync()
        if (!$demoProbe.WaitForExit(10000)) {
            $demoProbe.Kill()
            throw 'Java version detection timed out.'
        }
        [pscustomobject]@{ ExitCode = $demoProbe.ExitCode; Text = $demoOutput.Result + $demoError.Result }
    } finally { $demoProbe.Dispose() }
}

function Find-DemoJdk {
    if ($env:JAVA_HOME) {
        $demoJdkHome = $env:JAVA_HOME.Trim('"')
    } else {
        $demoJavaCommand = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if (!$demoJavaCommand) { throw 'Install a JDK (17 recommended), then set JAVA_HOME or add its bin folder to PATH.' }
        $demoJavaInfo = Invoke-DemoJavaProbe $demoJavaCommand.Source '-XshowSettings:properties -version'
        $demoHomeMatch = [regex]::Match($demoJavaInfo.Text, '(?m)^\s*java\.home\s*=\s*(.+)$')
        if ($demoJavaInfo.ExitCode -ne 0 -or !$demoHomeMatch.Success) { throw 'Could not detect the JDK. Set JAVA_HOME to the installed JDK folder.' }
        $demoJdkHome = $demoHomeMatch.Groups[1].Value.Trim()
    }
    $demoJava = Join-Path $demoJdkHome 'bin\java.exe'
    $demoJavac = Join-Path $demoJdkHome 'bin\javac.exe'
    if (!(Test-Path -LiteralPath $demoJava) -or !(Test-Path -LiteralPath $demoJavac)) {
        throw 'JAVA_HOME must point to a JDK folder containing bin\java.exe and bin\javac.exe.'
    }
    $demoCompilerInfo = Invoke-DemoJavaProbe $demoJavac '-version'
    $demoVersionMatch = [regex]::Match($demoCompilerInfo.Text, 'javac\s+(?:1\.)?(\d+)')
    if ($demoCompilerInfo.ExitCode -ne 0 -or !$demoVersionMatch.Success -or [int]$demoVersionMatch.Groups[1].Value -lt 17) {
        throw 'This project requires a JDK version of at least 17. JDK 17 is recommended.'
    }
    [pscustomobject]@{ Home = $demoJdkHome; Java = $demoJava; Major = $demoVersionMatch.Groups[1].Value }
}

function Test-DemoProcess($DemoProcess, [string]$Jar) {
    if (!$DemoProcess -or $DemoProcess.Name -notin @('java.exe', 'javaw.exe') -or !$DemoProcess.CommandLine) { return $false }
    # Match the complete -jar argument, not a substring or a reused PID.
    $demoJarPattern = [regex]::Escape($Jar)
    return $DemoProcess.CommandLine -match ('(?i)(?:^|\s)-jar\s+(?:"' + $demoJarPattern + '"|' + $demoJarPattern + '(?=\s|$))')
}

function Get-DemoListener([int]$Port) {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Wait-DemoReady($Configuration, $DemoProcess, [int]$TimeoutSeconds) {
    $demoDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (!(Get-Process -Id $DemoProcess.ProcessId -ErrorAction SilentlyContinue)) {
            throw "Java stopped during startup. See $($Configuration.Stderr) and $($Configuration.Stdout)."
        }
        try { $demoData = Invoke-RestMethod -Uri "$($Configuration.Url)/api/data" -TimeoutSec 3 }
        catch { Start-Sleep -Seconds 1; continue }
        if ($demoData.error) {
            throw "Web cannot access MySQL: $($demoData.error). Start MySQL and follow java-mysql-demo/README.md (database setup). Logs: $($Configuration.Runtime)"
        }
        if ($null -ne $demoData.users -and $null -ne $demoData.posts) { return }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $demoDeadline)
    throw "Web/database not ready after $TimeoutSeconds seconds. See logs in $($Configuration.Runtime)."
}
