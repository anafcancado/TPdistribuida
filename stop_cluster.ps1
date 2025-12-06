# Script PowerShell para parar cluster de servidores
# Uso: .\stop_cluster.ps1

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Parando Cluster de Quiz Distribuído" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

$stoppedCount = 0

# Parar todos os servidores
Get-ChildItem "server*.pid" -ErrorAction SilentlyContinue | ForEach-Object {
    $pidFile = $_.Name
    $serverNum = $pidFile -replace '[^0-9]'
    
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        
        try {
            $process = Get-Process -Id $pid -ErrorAction Stop
            Write-Host "🛑 Parando Servidor #$serverNum (PID: $pid)..." -ForegroundColor Yellow
            Stop-Process -Id $pid -Force
            $stoppedCount++
            Write-Host "   ✅ Servidor #$serverNum parado" -ForegroundColor Green
        } catch {
            Write-Host "ℹ️  Servidor #$serverNum já estava parado" -ForegroundColor Gray
        }
        
        # Remover arquivo PID
        Remove-Item $pidFile -Force
    }
}

Write-Host ""
Write-Host "🧹 Limpando arquivos temporários..." -ForegroundColor Cyan
Remove-Item "server*_config.txt" -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
if ($stoppedCount -gt 0) {
    Write-Host "✅ $stoppedCount servidor(es) parado(s)" -ForegroundColor Green
} else {
    Write-Host "ℹ️  Nenhum servidor estava rodando" -ForegroundColor Gray
}
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""

$logFiles = Get-ChildItem "server*_log.txt" -ErrorAction SilentlyContinue
if ($logFiles) {
    Write-Host "📝 Logs preservados:" -ForegroundColor Cyan
    $logFiles | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Gray }
} else {
    Write-Host "📝 (nenhum log encontrado)" -ForegroundColor Gray
}
Write-Host ""

Read-Host "Pressione Enter para sair"