@echo off
setlocal
cd /d "%~dp0code" || exit /b 1

set "SQLI_JAVA=java"
set "SQLI_JAVAC=javac"
if defined JAVA_HOME (
    set "SQLI_JAVA=%JAVA_HOME%\bin\java.exe"
    set "SQLI_JAVAC=%JAVA_HOME%\bin\javac.exe"
)
"%SQLI_JAVAC%" -version >nul 2>&1
if errorlevel 1 (
    echo A JDK is required. Install JDK 17 and set JAVA_HOME for this terminal.
    exit /b 1
)

set "SQLI_VERSION_FILE=%TEMP%\sqli-native-java-%RANDOM%-%RANDOM%.txt"
"%SQLI_JAVA%" -version >"%SQLI_VERSION_FILE%" 2>&1
if errorlevel 1 (
    del /q "%SQLI_VERSION_FILE%" >nul 2>&1
    echo Java could not start. Check JAVA_HOME or PATH in this terminal.
    exit /b 1
)
set "SQLI_JAVA_VERSION="
for /f "usebackq tokens=3" %%V in ("%SQLI_VERSION_FILE%") do if not defined SQLI_JAVA_VERSION set "SQLI_JAVA_VERSION=%%~V"
del /q "%SQLI_VERSION_FILE%" >nul 2>&1
set "SQLI_JAVA_MAJOR="
for /f "tokens=1 delims=." %%V in ("%SQLI_JAVA_VERSION%") do set "SQLI_JAVA_MAJOR=%%V"
if not defined SQLI_JAVA_MAJOR (
    echo Could not determine the Java version. JDK 17 is recommended.
    exit /b 1
)
if %SQLI_JAVA_MAJOR% LSS 17 (
    echo This project needs JDK 17-21. JDK 17 is recommended.
    exit /b 1
)
if %SQLI_JAVA_MAJOR% GTR 21 (
    echo This launcher supports JDK 17-21. Select JDK 17 in this terminal.
    exit /b 1
)

if not defined SQLI_HTTP_PORT set "SQLI_HTTP_PORT=8081"
echo Starting native demo at http://127.0.0.1:%SQLI_HTTP_PORT%
echo Keep this window open. Press Ctrl+C to stop the web; database data is preserved.
echo The first run downloads Maven and dependencies from the Internet.
call mvnw.cmd -DskipTests -Dspring-boot.run.main-class=com.example.sqliwebdemo.SqliWebDemoApplication spring-boot:run
exit /b %ERRORLEVEL%
