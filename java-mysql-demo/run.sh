#!/bin/sh
set -eu
SQLI_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SQLI_SCRIPT_DIR/code"

SQLI_JAVA=java
SQLI_JAVAC=javac
if [ -n "${JAVA_HOME:-}" ]; then
    SQLI_JAVA="$JAVA_HOME/bin/java"
    SQLI_JAVAC="$JAVA_HOME/bin/javac"
fi
if ! "$SQLI_JAVAC" -version >/dev/null 2>&1; then
    printf '%s\n' 'A JDK is required. Install JDK 17 and set JAVA_HOME for this terminal.' >&2
    exit 1
fi
SQLI_JAVA_VERSION=$("$SQLI_JAVA" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
case "$SQLI_JAVA_VERSION" in
    17|18|19|20|21) ;;
    *) printf '%s\n' 'Select JDK 17-21 for this terminal; JDK 17 is recommended.' >&2; exit 1 ;;
esac

: "${SQLI_HTTP_PORT:=8081}"
export SQLI_HTTP_PORT
printf 'Starting native demo at http://127.0.0.1:%s\n' "$SQLI_HTTP_PORT"
printf '%s\n' 'Keep this terminal open. Ctrl+C stops the web and preserves database data.'
printf '%s\n' 'The first run downloads Maven and dependencies from the Internet.'
exec sh ./mvnw -DskipTests -Dspring-boot.run.main-class=com.example.sqliwebdemo.SqliWebDemoApplication spring-boot:run
