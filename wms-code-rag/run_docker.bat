@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo Запуск WMS Codebase RAG MCP Server в Docker
echo ========================================================
echo.
docker compose up -d --build
echo.
echo Сервер запущен!
echo SSE Endpoint: http://localhost:8000/sse
echo.
pause
