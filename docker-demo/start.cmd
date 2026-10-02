@echo off
setlocal
cd /d "%~dp0"
docker compose up -d --build
if errorlevel 1 exit /b 1
docker compose ps
echo.
echo Open http://localhost:8080 or the APP_PORT configured in .env.
echo View startup logs with: docker compose logs -f app
