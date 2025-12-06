@echo off
REM Script automatico para iniciar sistema com Multicast e Auto-Discovery
REM 3 Servidores + 3 Clientes com descoberta automática

setlocal enabledelayedexpansion

echo =========================================
echo   Sistema Quiz Distribuido - MULTICAST
echo   Descoberta Automatica + Tolerancia a Falhas
echo =========================================
echo.

REM Compilar arquivos Java
echo [32mCompilando arquivos Java com UTF-8...[0m
javac -encoding UTF-8 DistributedQuizServer.java DistributedQuizClient.java

if errorlevel 1 (
    echo [31mErro na compilacao![0m
    pause
    exit /b 1
)

echo [32mCompilacao concluida![0m
echo.

REM ============================================
REM INICIAR SERVIDORES (Descoberta Automatica)
REM ============================================
echo.
echo [36m=== INICIANDO SERVIDORES (Multicast) ===[0m
echo.

REM Servidor 1
echo [36mIniciando Servidor #1...[0m
echo   - Porta de clientes: 5001
echo   - Porta de servidores: 6001
echo   - Multicast: 230.0.0.1:4446
start "Servidor #1" cmd /k "java DistributedQuizServer 1 5001 6001"
echo   - [32mIniciado[0m
echo.
timeout /t 3 /nobreak >nul

REM Servidor 2
echo [36mIniciando Servidor #2...[0m
echo   - Porta de clientes: 5002
echo   - Porta de servidores: 6002
echo   - Multicast: 230.0.0.1:4446
start "Servidor #2" cmd /k "java DistributedQuizServer 2 5002 6002"
echo   - [32mIniciado[0m
echo.
timeout /t 3 /nobreak >nul

REM Servidor 3
echo [36mIniciando Servidor #3...[0m
echo   - Porta de clientes: 5003
echo   - Porta de servidores: 6003
echo   - Multicast: 230.0.0.1:4446
start "Servidor #3" cmd /k "java DistributedQuizServer 3 5003 6003"
echo   - [32mIniciado[0m
echo.
timeout /t 4 /nobreak >nul

REM ============================================
REM INICIAR CLIENTES (Auto-Discovery)
REM ============================================
echo.
echo [36m=== INICIANDO CLIENTES (Auto-Discovery) ===[0m
echo.

REM Cliente 1 - Descoberta Automatica
echo [33mIniciando Cliente #1 (Auto-Discovery)...[0m
echo   - Procurando coordenador via Multicast...
start "Cliente #1 - Auto Discovery" cmd /k "java DistributedQuizClient"
echo   - [32mIniciado[0m
echo.
timeout /t 2 /nobreak >nul

REM Cliente 2 - Descoberta Automatica
echo [33mIniciando Cliente #2 (Auto-Discovery)...[0m
echo   - Procurando coordenador via Multicast...
start "Cliente #2 - Auto Discovery" cmd /k "java DistributedQuizClient"
echo   - [32mIniciado[0m
echo.
timeout /t 2 /nobreak >nul

REM Cliente 3 - Descoberta Automatica
echo [33mIniciando Cliente #3 (Auto-Discovery)...[0m
echo   - Procurando coordenador via Multicast...
start "Cliente #3 - Auto Discovery" cmd /k "java DistributedQuizClient"
echo   - [32mIniciado[0m
echo.

REM ============================================
REM RESUMO
REM ============================================
echo.
echo =========================================
echo [32mSISTEMA INICIADO COM SUCESSO![0m
echo =========================================
echo.
echo [36mNOVOS RECURSOS:[0m
echo   [32m✓[0m Descoberta automatica via Multicast (230.0.0.1:4446)
echo   [32m✓[0m Heartbeat a cada 2 segundos
echo   [32m✓[0m Deteccao de falhas (timeout 6 segundos)
echo   [32m✓[0m Eleicao automatica de coordenador
echo   [32m✓[0m Reconexao automatica de clientes
echo   [32m✓[0m Sincronizacao de estado entre servidores
echo.
echo [36mSERVIDORES:[0m
echo   - Servidor #1: localhost:5001
echo   - Servidor #2: localhost:5002
echo   - Servidor #3: localhost:5003
echo.
echo [33mCLIENTES:[0m
echo   - Todos os clientes encontrarao o coordenador automaticamente
echo   - Nao e necessario digitar IP ou porta manualmente
echo.
echo [36mTESTE DE TOLERANCIA A FALHAS:[0m
echo   1. Feche a janela do coordenador para simular falha
echo   2. Os servidores restantes elegerao um novo coordenador
echo   3. Os clientes reconectarao automaticamente
echo.
echo [33mPara parar o sistema:[0m
echo   - Feche todas as janelas de servidores e clientes
echo.
echo =========================================
echo.

pause
