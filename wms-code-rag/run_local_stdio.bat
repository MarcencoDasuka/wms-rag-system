@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================================
echo Запуск локального MCP Server в режиме stdio (для агента)
echo ========================================================
py src/mcp_server.py --transport stdio
