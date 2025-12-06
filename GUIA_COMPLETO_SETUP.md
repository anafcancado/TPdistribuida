# 🎮 Sistema de Quiz Competitivo Distribuído

## 📋 Visão Geral do Projeto

Este projeto implementa um sistema de quiz estilo Kahoot totalmente distribuído, com múltiplos servidores cooperando para gerenciar o jogo. O sistema foi desenvolvido para a disciplina de Sistemas Distribuídos e implementa os seguintes algoritmos:

### ✅ Algoritmos Implementados

1. **Eleição Bully** - Eleição de coordenador entre servidores
2. **Ricart-Agrawala** - Exclusão mútua distribuída para acesso ao placar
3. **Consistência de Réplicas** - Sincronização de estado entre servidores
4. **Relógios Lógicos de Lamport** - Ordenação de eventos distribuídos

---

## 🏗️ Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────┐
│                   CLUSTER DE SERVIDORES                  │
│                                                          │
│  ┌───────────┐      ┌───────────┐      ┌───────────┐  │
│  │ Servidor 1│◄────►│ Servidor 2│◄────►│ Servidor 3│  │
│  │  (ID: 1)  │      │  (ID: 2)  │      │  (ID: 3)  │  │
│  │ Port:5001 │      │ Port:5002 │      │ Port:5003 │  │
│  └─────┬─────┘      └─────┬─────┘      └─────┬─────┘  │
│        │                  │                  │         │
└────────┼──────────────────┼──────────────────┼─────────┘
         │                  │                  │
         ▼                  ▼                  ▼
    ┌─────────┐        ┌─────────┐       ┌─────────┐
    │Cliente 1│        │Cliente 2│       │Cliente 3│
    └─────────┘        └─────────┘       └─────────┘
```

### Componentes:

- **Servidores Distribuídos**: Cooperam para gerenciar o jogo
- **Clientes**: Conectam-se a qualquer servidor do cluster
- **Coordenador**: Servidor eleito que gerencia o fluxo do jogo

---

## 🚀 Como Testar Virtualmente (Sem Máquinas Físicas)

### Pré-requisitos:
- Java JDK 8 ou superior
- IDE Java (Eclipse, IntelliJ, NetBeans) ou terminal

### Método 1: Testar na Mesma Máquina (Mais Fácil)

#### Passo 1: Compilar os arquivos
```bash
javac DistributedQuizServer.java
javac DistributedQuizClient.java
```

#### Passo 2: Iniciar Servidor 1
```bash
java DistributedQuizServer
```
**Configuração na janela:**
- ServerID: `1`
- Client Port: `5001`
- Server Port: `6001`
- Conectar a outros: *deixar vazio* (é o primeiro)

#### Passo 3: Iniciar Servidor 2
```bash
java DistributedQuizServer
```
**Configuração:**
- ServerID: `2`
- Client Port: `5002`
- Server Port: `6002`
- Conectar a outros: `1:localhost:6001`

#### Passo 4: Iniciar Servidor 3
```bash
java DistributedQuizServer
```
**Configuração:**
- ServerID: `3`
- Client Port: `5003`
- Server Port: `6003`
- Conectar a outros: `1:localhost:6001;2:localhost:6002`

#### Passo 5: Iniciar Clientes
```bash
java DistributedQuizClient
```
**Conectar clientes a diferentes servidores:**
- Cliente 1 → `localhost:5001`
- Cliente 2 → `localhost:5002`
- Cliente 3 → `localhost:5003`

---

### Método 2: Testar com Máquinas Virtuais

#### Usando VirtualBox/VMware:

1. **Criar 3 VMs** com Ubuntu/Debian
2. **Configurar rede em Bridge** para comunicação
3. **Instalar Java** em cada VM:
   ```bash
   sudo apt update
   sudo apt install default-jdk
   ```

4. **Copiar arquivos** para cada VM

5. **Descobrir IPs** de cada VM:
   ```bash
   hostname -I
   ```

6. **Iniciar servidores** em cada VM:
   - VM1: Servidor 1 (primeiro servidor)
   - VM2: Servidor 2 (conecta ao IP da VM1)
   - VM3: Servidor 3 (conecta aos IPs das VM1 e VM2)

---

### Método 3: Testar com Docker (Avançado)

#### Criar Dockerfile:
```dockerfile
FROM openjdk:11
WORKDIR /app
COPY *.java .
RUN javac DistributedQuizServer.java DistributedQuizClient.java
CMD ["java", "DistributedQuizServer"]
```

#### Criar docker-compose.yml:
```yaml
version: '3'
services:
  server1:
    build: .
    ports:
      - "5001:5001"
      - "6001:6001"
    environment:
      - SERVER_ID=1
      - CLIENT_PORT=5001
      - SERVER_PORT=6001
    networks:
      - quiz-network

  server2:
    build: .
    ports:
      - "5002:5002"
      - "6002:6002"
    environment:
      - SERVER_ID=2
      - CLIENT_PORT=5002
      - SERVER_PORT=6002
    networks:
      - quiz-network

  server3:
    build: .
    ports:
      - "5003:5003"
      - "6003:6003"
    environment:
      - SERVER_ID=3
      - CLIENT_PORT=5003
      - SERVER_PORT=6003
    networks:
      - quiz-network

networks:
  quiz-network:
    driver: bridge
```

#### Executar:
```bash
docker-compose up
```

---

## 🧪 Roteiro de Testes

### Teste 1: Eleição de Coordenador

1. Inicie os 3 servidores
2. Observe nos logs a eleição automática
3. O servidor com **maior ID** será eleito coordenador
4. Verifique no GUI qual servidor é o coordenador

**Resultado esperado:** Servidor #3 deve ser o coordenador

### Teste 2: Conectar Clientes

1. Inicie 3 clientes
2. Conecte cada um a um servidor diferente:
   - Cliente A → Servidor 1
   - Cliente B → Servidor 2
   - Cliente C → Servidor 3
3. Todos devem ver a mesma mensagem de boas-vindas

**Resultado esperado:** Todos os clientes conectam com sucesso

### Teste 3: Iniciar Jogo (Apenas Coordenador)

1. Tente iniciar o jogo no Servidor 1 (não-coordenador)
   - **Resultado:** Mensagem de erro
2. Inicie o jogo no Servidor 3 (coordenador)
   - **Resultado:** Jogo inicia para todos

### Teste 4: Sincronização de Perguntas

1. Coordenador envia pergunta
2. **Todos os clientes** devem receber a pergunta simultaneamente
3. Verifique que a pergunta é a mesma em todos

**Resultado esperado:** Sincronização perfeita

### Teste 5: Exclusão Mútua (Placar)

1. Múltiplos clientes respondem simultaneamente
2. Observe nos logs do servidor o uso de Ricart-Agrawala
3. Placar deve ser atualizado de forma consistente
4. **Todos os servidores** devem ter o mesmo placar

**Logs esperados:**
```
Solicitando CS com timestamp X
CS concedida! Executando seção crítica...
Placar atualizado: Jogador1 = 100
Placar replicado: Jogador1 = 100
```

### Teste 6: Tolerância a Falhas

1. Durante o jogo, **feche o Servidor 1** (não-coordenador)
2. Clientes conectados ao Servidor 1 perdem conexão
3. **Mas o jogo continua** nos outros servidores!
4. Clientes podem reconectar ao Servidor 2 ou 3

**Resultado esperado:** Sistema continua funcionando

### Teste 7: Falha do Coordenador

1. Durante o jogo, **feche o Servidor 3** (coordenador)
2. Observe nova eleição nos logs dos servidores restantes
3. Servidor #2 se torna o novo coordenador
4. Sistema continua operando

**Logs esperados:**
```
[Servidor 2] === INICIANDO ELEIÇÃO BULLY ===
[Servidor 2] >>> ME TORNEI COORDENADOR <<<
[Servidor 1] Novo coordenador: #2
```

### Teste 8: Consistência de Réplicas

1. Inicie jogo com 3 clientes em 3 servidores diferentes
2. Todos respondem perguntas
3. Após cada pergunta, verifique o placar em cada servidor
4. **Todos devem mostrar o mesmo placar**

### Teste 9: Relógios de Lamport

1. Observe o campo "Relógio Lamport" no GUI de cada servidor
2. Envie várias operações (perguntas, respostas, atualizações)
3. Verifique que os relógios sempre aumentam
4. Relógios de diferentes servidores devem sincronizar após mensagens

**Exemplo:**
```
Servidor 1: Clock = 15
Servidor 2: Clock = 12
Servidor 2 recebe mensagem do 1
Servidor 2: Clock = 16 (max(12, 15) + 1)
```

---

## 📊 Demonstrando os Algoritmos

### Para a Apresentação/Relatório:

#### 1. **Algoritmo Bully (Eleição)**

**Cenário:** Mostrar eleição ao iniciar sistema
```
1. Servidor 1 inicia → envia ELECTION para 2 e 3
2. Servidores 2 e 3 respondem OK
3. Servidor 3 (maior ID) vence
4. Servidor 3 envia COORDINATOR para todos
```

**Captura de tela:** Logs mostrando mensagens ELECTION, OK, COORDINATOR

#### 2. **Ricart-Agrawala (Exclusão Mútua)**

**Cenário:** 3 clientes respondem simultaneamente
```
1. Servidor recebe 3 respostas ao mesmo tempo
2. Servidor solicita CS (CS_REQUEST)
3. Aguarda CS_REPLY de todos
4. Atualiza placar (seção crítica)
5. Processa fila de requisições
```

**Captura de tela:** Logs mostrando CS_REQUEST, CS_REPLY, "CS concedida"

#### 3. **Consistência de Réplicas**

**Cenário:** Atualizar placar em múltiplos servidores
```
1. Cliente responde corretamente no Servidor 1
2. Servidor 1 atualiza placar local
3. Servidor 1 envia REPLICATE|SCORE_UPDATE
4. Servidores 2 e 3 recebem e replicam
5. Todos têm o mesmo estado
```

**Captura de tela:** Placares idênticos em 3 servidores

#### 4. **Relógios de Lamport**

**Cenário:** Ordenação de eventos
```
Evento A (S1, clock=5) → Pergunta enviada
Evento B (S2, clock=7) → Resposta recebida
Evento C (S3, clock=8) → Placar atualizado

Ordem global: A → B → C
```

**Captura de tela:** Campo "Relógio Lamport" aumentando

---

## 🐛 Troubleshooting

### Problema: "Connection refused"
**Solução:** Verifique se o servidor está rodando e a porta está correta

### Problema: "Address already in use"
**Solução:** Porta já está em uso. Use outra porta ou feche o processo:
```bash
# Linux/Mac
lsof -i :5001
kill -9 <PID>

# Windows
netstat -ano | findstr :5001
taskkill /PID <PID> /F
```

### Problema: Clientes não recebem perguntas
**Solução:** Verifique se o coordenador iniciou o jogo (botão "Iniciar Jogo")

### Problema: Eleição não acontece
**Solução:** Certifique-se de que todos os servidores estão conectados entre si

---

## 📝 Checklist para o Relatório

- [ ] Descrição da arquitetura distribuída
- [ ] Explicação do algoritmo Bully com diagrama
- [ ] Explicação do Ricart-Agrawala com exemplo
- [ ] Demonstração de consistência de réplicas
- [ ] Uso de relógios lógicos de Lamport
- [ ] Testes de tolerância a falhas
- [ ] Capturas de tela dos testes
- [ ] Análise de desempenho (opcional)
- [ ] Conclusão sobre desafios e aprendizados

---

## 🎯 Critérios Atendidos

✅ **Sistema distribuído com múltiplos nós** (3+ servidores)  
✅ **Operações paralelizadas** (múltiplos clientes simultâneos)  
✅ **Tolerância a falhas** (sistema continua após falha de servidor)  
✅ **3 Algoritmos implementados manualmente:**
   - Eleição (Bully)
   - Exclusão mútua (Ricart-Agrawala)
   - Consistência de réplicas
✅ **Relógios lógicos** (Lamport) - bônus!

---

## 💡 Dicas Extras

1. **Para demonstrar melhor:** Use `Thread.sleep()` para simular delays e tornar os algoritmos mais visíveis nos logs

2. **Para o relatório:** Documente cada troca de mensagens com diagramas de sequência

3. **Para nota extra:** Implemente interface web com WebSocket para visualização em tempo real

4. **Para facilitar testes:** Crie script bash/bat que inicia todos os servidores automaticamente

---

## 📞 Suporte

Se tiver dúvidas:
1. Verifique os logs no console de cada servidor
2. Teste primeiro com 2 servidores apenas
3. Use `System.out.println` para debug adicional
4. Verifique firewalls que possam bloquear portas

**Boa sorte com o trabalho! 🚀**