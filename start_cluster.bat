@echo off
REM Script para iniciar cluster de servidores no Windows
REM Uso: start_cluster.bat [numero_de_servidores]

setlocal enabledelayedexpansion

set NUM_SERVERS=%1
if "%NUM_SERVERS%"=="" set NUM_SERVERS=3

echo =========================================
echo   Iniciando Cluster de Quiz Distribuido
echo   Servidores: %NUM_SERVERS%
echo =========================================
echo.

REM Compilar arquivos Java
echo [32m Compilando arquivos Java com UTF-8...[0m
javac -encoding UTF-8 DistributedQuizServer.java
javac -encoding UTF-8 DistributedQuizClient.java

if errorlevel 1 (
    echo [31mErro na compilacao![0m
    pause
    exit /b 1
)

echo [32mCompilacao concluida![0m
echo.

REM Limpar arquivos antigos
del /Q server*.pid 2>nul
del /Q server*_config.txt 2>nul

REM Iniciar servidores
for /L %%i in (1,1,%NUM_SERVERS%) do (
    call :start_server %%i
    timeout /t 2 /nobreak >nul
)

echo.
echo =========================================
echo [32mCluster iniciado com sucesso![0m
echo =========================================
echo.

echo [36mPara conectar clientes, use:[0m
for /L %%i in (1,1,%NUM_SERVERS%) do (
    set /a client_port=5000+%%i
    echo   - Servidor #%%i: localhost:!client_port!
)
echo.

echo [33mPara parar o cluster: stop_cluster.bat[0m
echo.

pause
exit /b 0

:start_server
set server_id=%1
set /a client_port=5000+%server_id%
set /a server_port=6000+%server_id%

echo [36mIniciando Servidor #%server_id%...[0m
echo   - Porta de clientes: %client_port%
echo   - Porta de servidores: %server_port%

REM Construir string de conexoes
set connections=
set /a prev=%server_id%-1
for /L %%j in (1,1,!prev!) do (
    set /a conn_port=6000+%%j
    if defined connections (
        set connections=!connections!;%%j:localhost:!conn_port!
    ) else (
        set connections=%%j:localhost:!conn_port!
    )
)

REM Criar arquivo de configuracao
echo %server_id%,%client_port%,%server_port% > server%server_id%_config.txt
echo !connections! >> server%server_id%_config.txt

REM Iniciar servidor
start "Servidor #%server_id%" /MIN cmd /c "java DistributedQuizServer < server%server_id%_config.txt > server%server_id%_log.txt 2>&1"

echo   - [32mIniciado[0m
echo.

exit /b 0