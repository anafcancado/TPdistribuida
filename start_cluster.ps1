# Script PowerShell para iniciar cluster de servidores
# Uso: .\start_cluster.ps1 [numero_de_servidores]

param(
    [int]$NumServers = 3
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Iniciando Cluster de Quiz Distribuído" -ForegroundColor Cyan
Write-Host "  Servidores: $NumServers" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Compilar arquivos Java
Write-Host "📦 Compilando arquivos Java..." -ForegroundColor Green
javac -encoding UTF-8 DistributedQuizServer.java DistributedQuizClient.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro na compilação!" -ForegroundColor Red
    Read-Host "Pressione Enter para sair"
    exit 1
}

Write-Host "✅ Compilação concluída!" -ForegroundColor Green
Write-Host ""

# Limpar arquivos antigos
Remove-Item "server*.pid" -ErrorAction SilentlyContinue
Remove-Item "server*_log.txt" -ErrorAction SilentlyContinue

Write-Host "🚀 Iniciando servidores..." -ForegroundColor Yellow
Write-Host ""

$jobs = @()

# Função para iniciar servidor
for ($i = 1; $i -le $NumServers; $i++) {
    $clientPort = 5000 + $i
    $serverPort = 6000 + $i
    
    Write-Host "Iniciando Servidor #$i..." -ForegroundColor Cyan
    Write-Host "  - Porta de clientes: $clientPort" -ForegroundColor Gray
    Write-Host "  - Porta de servidores: $serverPort" -ForegroundColor Gray
    
    # Construir string de conexões
    $connections = @()
    for ($j = 1; $j -lt $i; $j++) {
        $connPort = 6000 + $j
        $connections += "${j}:localhost:${connPort}"
    }
    $connString = $connections -join ";"
    
    # Criar comando
    if ($connString -eq "") {
        $command = "java -Dfile.encoding=UTF-8 DistributedQuizServer $i $clientPort $serverPort"
    } else {
        $command = "java -Dfile.encoding=UTF-8 DistributedQuizServer $i $clientPort $serverPort `"$connString`""
    }
    
    # Iniciar em nova janela
    $process = Start-Process cmd.exe -ArgumentList "/k", $command -WindowStyle Normal -PassThru
    
    Write-Host "  - PID: $($process.Id)" -ForegroundColor Green
    $process.Id | Out-File "server${i}.pid"
    
    Write-Host ""
    
    # Aguardar entre servidores
    Start-Sleep -Seconds 3
}

Write-Host "=========================================" -ForegroundColor Green
Write-Host "✅ Cluster iniciado com sucesso!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""

Write-Host "📊 Servidores em execução:" -ForegroundColor Cyan
for ($i = 1; $i -le $NumServers; $i++) {
    if (Test-Path "server${i}.pid") {
        $pid = Get-Content "server${i}.pid"
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "  ✅ Servidor #$i (PID: $pid) - Rodando" -ForegroundColor Green
        } else {
            Write-Host "  ❌ Servidor #$i (PID: $pid) - Não encontrado" -ForegroundColor Red
        }
    }
}
Write-Host ""

Write-Host "🎮 Para conectar clientes, use:" -ForegroundColor Yellow
for ($i = 1; $i -le $NumServers; $i++) {
    $clientPort = 5000 + $i
    Write-Host "  - Servidor #$i: localhost:$clientPort" -ForegroundColor Gray
}
Write-Host ""

Write-Host "🛑 Para parar o cluster: .\stop_cluster.ps1" -ForegroundColor Yellow
Write-Host ""

Write-Host "💡 Dica: Aguarde 5-10 segundos para os servidores se conectarem" -ForegroundColor Magenta
Write-Host "💡 Verifique os logs nas janelas de cada servidor" -ForegroundColor Magenta
Write-Host ""

Read-Host "Pressione Enter para continuar"