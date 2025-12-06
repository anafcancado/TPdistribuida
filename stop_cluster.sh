#!/bin/bash

# Script para parar cluster de servidores
# Uso: ./stop_cluster.sh

echo "========================================="
echo "  Parando Cluster de Quiz Distribuído"
echo "========================================="
echo ""

stopped_count=0

# Parar todos os servidores
for pid_file in server*.pid; do
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        server_num=$(echo "$pid_file" | grep -o '[0-9]\+')
        
        if ps -p $pid > /dev/null 2>&1; then
            echo "🛑 Parando Servidor #$server_num (PID: $pid)..."
            kill $pid
            
            # Aguardar término
            sleep 1
            
            if ps -p $pid > /dev/null 2>&1; then
                echo "   ⚠️  Forçando término..."
                kill -9 $pid
            fi
            
            ((stopped_count++))
            echo "   ✅ Servidor #$server_num parado"
        else
            echo "ℹ️  Servidor #$server_num já estava parado"
        fi
        
        # Remover arquivo PID
        rm "$pid_file"
    fi
done

# Limpar arquivos temporários
echo ""
echo "🧹 Limpando arquivos temporários..."
rm -f server*_config.txt

echo ""
echo "========================================="
if [ $stopped_count -gt 0 ]; then
    echo "✅ $stopped_count servidor(es) parado(s)"
else
    echo "ℹ️  Nenhum servidor estava rodando"
fi
echo "========================================="
echo ""
echo "📝 Logs preservados:"
ls -1 server*_log.txt 2>/dev/null || echo "  (nenhum log encontrado)"
echo ""