@echo off
echo ======================================
echo   Quiz Distribuido - Modo Minimo
echo   2 Servidores + 2 Clientes
echo ======================================
echo.

javac -encoding UTF-8 DistributedQuizServer.java DistributedQuizClient.java 2>nul
if errorlevel 1 (
    echo Erro na compilacao!
    pause
    exit /b 1
)

echo Compilado! Iniciando...
echo.

start "" javaw DistributedQuizServer 1 5001 6001
timeout /t 2 /nobreak >nul

start "" javaw DistributedQuizServer 2 5002 6002
timeout /t 3 /nobreak >nul

start "" javaw DistributedQuizClient
timeout /t 2 /nobreak >nul

start "" javaw DistributedQuizClient

echo.
echo Sistema iniciado:
echo   - Servidor #1: 5001
echo   - Servidor #2: 5002
echo   - Cliente #1 e #2
echo.
echo Feche esta janela apos conectar os clientes.
timeout /t 5 /nobreak >nul