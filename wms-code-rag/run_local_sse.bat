@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo Запуск локального MCP Server в режиме SSE (HTTP :8000)
echo ========================================================
py src/mcp_server.py --transport sse --host 127.0.0.1 --port 8000 --auto-index
pause
