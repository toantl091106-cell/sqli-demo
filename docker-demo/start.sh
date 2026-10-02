#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
docker compose up -d --build
docker compose ps
printf '\nOpen http://localhost:8080 or the APP_PORT configured in .env.\n'
