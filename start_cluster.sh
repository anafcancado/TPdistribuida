#!/bin/bash

# Script para iniciar cluster de servidores automaticamente
# Uso: ./start_cluster.sh [numero_de_servidores]

NUM_SERVERS=${1:-3}  # Padrão: 3 servidores

echo "========================================="
echo "  Iniciando Cluster de Quiz Distribuído"
echo "  Servidores: $NUM_SERVERS"
echo "========================================="
echo ""

# Compilar arquivos Java com UTF-8
echo "📦 Compilando arquivos Java (UTF-8)..."
javac -encoding UTF-8 DistributedQuizServer.java
javac -encoding UTF-8 DistributedQuizClient.java

if [ $? -ne 0 ]; then
    echo "❌ Erro na compilação!"
    exit 1
fi

echo "✅ Compilação concluída!"
echo ""

# Função para iniciar servidor
start_server() {
    local server_id=$1
    local client_port=$((5000 + server_id))
    local server_port=$((6000 + server_id))
    
    echo "🚀 Iniciando Servidor #$server_id..."
    echo "   - Porta de clientes: $client_port"
    echo "   - Porta de servidores: $server_port"
    
    # Construir string de conexões
    local connections=""
    for ((i=1; i<server_id; i++)); do
        if [ $i -gt 1 ]; then
            connections="$connections;"
        fi
        connections="${connections}${i}:localhost:$((6000 + i))"
    done
    
    # Criar arquivo de configuração temporário
    cat > "server${server_id}_config.txt" <<EOF
$server_id,$client_port,$server_port
$connections
EOF
    
    # Iniciar servidor em background
    java DistributedQuizServer < "server${server_id}_config.txt" > "server${server_id}_log.txt" 2>&1 &
    
    local pid=$!
    echo "   - PID: $pid"
    echo $pid > "server${server_id}.pid"
    
    # Aguardar um pouco antes de iniciar o próximo
    sleep 2
    echo ""
}

# Iniciar todos os servidores
for ((i=1; i<=NUM_SERVERS; i++)); do
    start_server $i
done

echo "========================================="
echo "✅ Cluster iniciado com sucesso!"
echo "========================================="
echo ""
echo "📊 Status dos Servidores:"
for ((i=1; i<=NUM_SERVERS; i++)); do
    if [ -f "server${i}.pid" ]; then
        pid=$(cat "server${i}.pid")
        if ps -p $pid > /dev/null 2>&1; then
            echo "  ✅ Servidor #$i (PID: $pid) - Rodando"
        else
            echo "  ❌ Servidor #$i (PID: $pid) - Falhou ao iniciar"
        fi
    fi
done
echo ""

echo "📝 Logs disponíveis em:"
for ((i=1; i<=NUM_SERVERS; i++)); do
    echo "  - server${i}_log.txt"
done
echo ""

echo "🎮 Para conectar clientes, use:"
for ((i=1; i<=NUM_SERVERS; i++)); do
    echo "  - Servidor #$i: localhost:$((5000 + i))"
done
echo ""

echo "🛑 Para parar o cluster: ./stop_cluster.sh"
echo ""

# Monitorar logs em tempo real (opcional)
read -p "Deseja monitorar os logs? (s/n): " monitor

if [ "$monitor" = "s" ] || [ "$monitor" = "S" ]; then
    echo ""
    echo "Monitorando logs (Ctrl+C para sair)..."
    tail -f server*_log.txt
fi