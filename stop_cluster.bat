@echo off
REM Script para parar cluster de servidores no Windows
REM Uso: stop_cluster.bat

setlocal enabledelayedexpansion

echo =========================================
echo   Parando Cluster de Quiz Distribuido
echo =========================================
echo.

set stopped_count=0

REM Parar todas as janelas de servidor
for /f "tokens=2" %%a in ('tasklist /FI "WINDOWTITLE eq Servidor #*" /NH') do (
    set /a stopped_count+=1
    echo [33mParando processo %%a...[0m
    taskkill /PID %%a /F >nul 2>&1
)

if !stopped_count! gtr 0 (
    echo [32m!stopped_count! servidor^(es^) parado^(s^)[0m
) else (
    echo [36mNenhum servidor estava rodando[0m
)

echo.
echo [36mLimpando arquivos temporarios...[0m
del /Q server*_config.txt 2>nul
del /Q server*.pid 2>nul

echo.
echo =========================================
echo [32mCluster parado com sucesso![0m
echo =========================================
echo.

echo [36mLogs preservados:[0m
dir /B server*_log.txt 2>nul || echo   ^(nenhum log encontrado^)
echo.

pause