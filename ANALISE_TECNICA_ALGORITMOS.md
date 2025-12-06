# 📊 Análise Técnica dos Algoritmos Implementados

## 1. Algoritmo de Eleição Bully

### 📝 Descrição
O algoritmo Bully é usado para eleger um coordenador entre os servidores. O servidor com o maior ID sempre se torna o coordenador.

### 🔄 Funcionamento

```
Estado Inicial:
┌──────────┐  ┌──────────┐  ┌──────────┐
│Server #1 │  │Server #2 │  │Server #3 │
│  ID: 1   │  │  ID: 2   │  │  ID: 3   │
└──────────┘  └──────────┘  └──────────┘

Passo 1: Server #1 inicia eleição
─────────────────────────────────────
Server #1 envia ELECTION para todos com ID maior
    ELECTION|1|clock
         ↓
    [Server #2]
    [Server #3]

Passo 2: Servidores maiores respondem
─────────────────────────────────────
Server #2 → OK|2|clock → Server #1
Server #3 → OK|3|clock → Server #1

Passo 3: Servidores maiores iniciam própria eleição
────────────────────────────────────────────────────
Server #2 envia ELECTION para #3
Server #3 responde OK para #2

Passo 4: Server #3 (maior ID) se elege
───────────────────────────────────────
Server #3 → COORDINATOR|3|clock → Todos

Resultado Final:
┌──────────┐  ┌──────────┐  ┌──────────┐
│Server #1 │  │Server #2 │  │Server #3 │
│ Membro   │  │ Membro   │  │COORDENADOR│
└──────────┘  └──────────┘  └──────────┘
```

### 💻 Código Implementado

```java
private void startElection() {
    log("=== INICIANDO ELEIÇÃO BULLY ===");
    incrementClock(); // Incrementa relógio de Lamport
    
    boolean sentElection = false;
    // Envia ELECTION apenas para IDs maiores
    for (Integer otherId : serverAddresses.keySet()) {
        if (otherId > serverId) {
            sendToServer(otherId, "ELECTION|" + serverId + "|" + lamportClock);
            sentElection = true;
        }
    }
    
    if (!sentElection) {
        // Nenhum servidor com ID maior → sou o coordenador
        becomeCoordinator();
    } else {
        // Aguarda resposta OK por 2 segundos
        // Se timeout, me torno coordenador
    }
}
```

### 📊 Complexidade
- **Mensagens no pior caso**: O(n²)
- **Tempo de convergência**: O(n) × timeout
- **Espaço**: O(n) para armazenar IDs dos servidores

### ⚡ Vantagens
- Sempre elege o servidor com maior capacidade (maior ID)
- Simples de implementar
- Rápida convergência em cenários normais

### ⚠️ Desvantagens
- Muitas mensagens se eleições são frequentes
- Pode ter overhead se falhas são comuns
- Servidor com maior ID se torna ponto de contenção

---

## 2. Algoritmo Ricart-Agrawala (Exclusão Mútua)

### 📝 Descrição
Algoritmo distribuído para garantir que apenas um servidor acesse a seção crítica (placar) por vez, sem usar um coordenador central.

### 🔄 Funcionamento

```
Cenário: 3 servidores querem atualizar o placar

Estado Inicial:
┌──────────┐  ┌──────────┐  ┌──────────┐
│Server #1 │  │Server #2 │  │Server #3 │
│ Clock: 5 │  │ Clock: 7 │  │ Clock: 3 │
└──────────┘  └──────────┘  └──────────┘

Passo 1: Server #1 solicita CS
───────────────────────────────
Server #1:
  - incrementClock() → clock = 6
  - requestTimestamp = 6
  - Envia CS_REQUEST|1|6 para todos

    CS_REQUEST|1|6
          ↓
    [Server #2]
    [Server #3]

Passo 2: Servidores decidem responder ou enfileirar
────────────────────────────────────────────────────
Server #2 (clock=7):
  - Não está em CS
  - Responde: CS_REPLY|2|8

Server #3 (clock=3):
  - Não está em CS
  - Responde: CS_REPLY|3|4

Passo 3: Server #1 recebe todos os REPLYs
──────────────────────────────────────────
Server #1:
  - Recebeu 2 REPLYs (de #2 e #3)
  - Total esperado: 2
  - ✅ Entra na seção crítica!
  - Atualiza placar
  - Sai da CS

Passo 4: Processa requisições enfileiradas
───────────────────────────────────────────
Se outros servidores enviaram REQUEST enquanto #1 
estava em CS, agora #1 responde a eles.

Caso de Conflito:
─────────────────
Server #1: REQUEST com timestamp 6
Server #2: REQUEST com timestamp 6 (mesmo timestamp!)

Desempate por ID:
  - Server #1 tem prioridade (ID menor)
  - Server #2 enfileira o REQUEST
  - Server #1 entra na CS primeiro
```

### 💻 Código Implementado

```java
private void requestCriticalSection(Runnable criticalSection) {
    new Thread(() -> {
        incrementClock();
        requestingCS = true;
        requestTimestamp = lamportClock;
        replyReceived.clear();
        
        // Enviar REQUEST para todos
        for (Integer otherId : serverAddresses.keySet()) {
            sendToServer(otherId, "CS_REQUEST|" + serverId + "|" + requestTimestamp);
        }
        
        // Aguardar REPLY de todos (com timeout)
        while (replyReceived.size() < serverAddresses.size() && timeout < 50) {
            Thread.sleep(100);
        }
        
        // Executar seção crítica
        criticalSection.run();
        
        requestingCS = false;
        processQueuedRequests(); // Responder pedidos enfileirados
    }).start();
}

// Ao receber CS_REQUEST
case "CS_REQUEST":
    int reqTime = Integer.parseInt(parts[2]);
    
    // Se estou pedindo CS e tenho prioridade, enfileiro
    if (requestingCS && (reqTime < requestTimestamp || 
        (reqTime == requestTimestamp && reqId < serverId))) {
        requestQueue.add(new MutexRequest(reqId, reqTime));
    } else {
        // Respondo imediatamente
        sendToServer(reqId, "CS_REPLY|" + serverId + "|" + lamportClock);
    }
```

### 📊 Complexidade
- **Mensagens por entrada na CS**: 2(n-1)
  - (n-1) REQUESTs
  - (n-1) REPLYs
- **Tempo de espera**: RTT (Round Trip Time)
- **Espaço**: O(n) para fila de requisições

### ⚡ Vantagens
- Não precisa de coordenador central
- Baixa latência (1 RTT)
- Fairness por timestamp (FIFO se relógios sincronizados)

### ⚠️ Desvantagens
- Precisa de comunicação com TODOS os servidores
- Um servidor falho bloqueia o sistema
- Overhead de mensagens aumenta com n²

---

## 3. Consistência de Réplicas

### 📝 Descrição
Garante que todos os servidores tenham o mesmo estado do jogo (placar, pergunta atual, status).

### 🔄 Funcionamento

```
Cenário: Cliente responde corretamente no Server #1

┌─────────┐
│Cliente A│───┐
└─────────┘   │ ANSWER|0
              ▼
        ┌──────────┐
        │Server #1 │
        │          │
        │ 1. Atualiza placar local
        │    globalScoreboard.put("Alice", 100)
        │
        │ 2. Replica para outros servidores
        │    REPLICATE|SCORE_UPDATE|Alice:100|clock
        │
        └────┬───────┘
             │
       ┌─────┴──────┐
       │            │
       ▼            ▼
  ┌──────────┐  ┌──────────┐
  │Server #2 │  │Server #3 │
  │          │  │          │
  │ Recebe:  │  │ Recebe:  │
  │ REPLICATE│  │ REPLICATE│
  │          │  │          │
  │ Atualiza:│  │ Atualiza:│
  │ Alice=100│  │ Alice=100│
  └──────────┘  └──────────┘

Resultado: Estado consistente em todos!
┌──────────┐  ┌──────────┐  ┌──────────┐
│Server #1 │  │Server #2 │  │Server #3 │
│Alice: 100│  │Alice: 100│  │Alice: 100│
└──────────┘  └──────────┘  └──────────┘
```

### 💻 Código Implementado

```java
private void syncScoreboard(String playerName, int score) {
    // Usa Ricart-Agrawala para exclusão mútua
    requestCriticalSection(() -> {
        // Atualiza localmente
        globalScoreboard.put(playerName, score);
        log("Placar atualizado: " + playerName + " = " + score);
        
        // Replica para outros servidores
        replicateGameState("SCORE_UPDATE", playerName + ":" + score);
    });
}

private void replicateGameState(String action, String data) {
    incrementClock();
    String message = "REPLICATE|" + action + "|" + data + "|" + lamportClock;
    
    // Broadcast para todos os servidores
    for (Integer otherId : serverAddresses.keySet()) {
        sendToServer(otherId, message);
    }
}

private void handleReplication(String action, String data) {
    switch (action) {
        case "SCORE_UPDATE":
            String[] scoreData = data.split(":");
            globalScoreboard.put(scoreData[0], Integer.parseInt(scoreData[1]));
            log("Placar replicado: " + scoreData[0] + " = " + scoreData[1]);
            break;
        // ... outros tipos de replicação
    }
}
```

### 📊 Modelo de Consistência
- **Tipo**: Consistência Sequencial
- **Garantia**: Todas as réplicas veem as operações na mesma ordem
- **Sincronização**: Combinado com Ricart-Agrawala para atomicidade

### ⚡ Vantagens
- Estado sempre sincronizado
- Clientes podem conectar a qualquer servidor
- Tolerância a falhas (estado replicado)

### ⚠️ Desvantagens
- Latência de replicação
- Overhead de mensagens
- Potencial inconsistência temporária

---

## 4. Relógios Lógicos de Lamport

### 📝 Descrição
Ordena eventos em sistema distribuído sem relógio físico sincronizado.

### 🔄 Funcionamento

```
Cenário: Troca de mensagens entre servidores

Server #1          Server #2          Server #3
Clock: 5           Clock: 3           Clock: 8
   │                  │                  │
   │ Evento local     │                  │
   │ clock++          │                  │
   │ clock = 6        │                  │
   │                  │                  │
   │ MSG|6            │                  │
   ├─────────────────►│                  │
   │                  │ Recebe MSG|6     │
   │                  │ clock = max(3,6)+1
   │                  │ clock = 7        │
   │                  │                  │
   │                  │ MSG|7            │
   │                  ├─────────────────►│
   │                  │                  │ Recebe MSG|7
   │                  │                  │ clock = max(8,7)+1
   │                  │                  │ clock = 9
   │                  │                  │
   │                  │      MSG|9       │
   │◄─────────────────┼──────────────────┤
   │ Recebe MSG|9     │                  │
   │ clock = max(6,9)+1                  │
   │ clock = 10       │                  │

Ordem Global de Eventos:
1. Server #1 envia (timestamp=6)
2. Server #2 recebe (timestamp=7)
3. Server #2 envia (timestamp=7)
4. Server #3 recebe (timestamp=9)
5. Server #3 envia (timestamp=9)
6. Server #1 recebe (timestamp=10)
```

### 💻 Código Implementado

```java
// Relógio de Lamport
private int lamportClock = 0;
private final Object clockLock = new Object();

// Incrementar ao gerar evento local
private void incrementClock() {
    synchronized (clockLock) {
        lamportClock++;
        updateClockDisplay();
    }
}

// Atualizar ao receber mensagem
private void updateClock(int receivedTime) {
    synchronized (clockLock) {
        lamportClock = Math.max(lamportClock, receivedTime) + 1;
        updateClockDisplay();
    }
}

// Uso em mensagens
private void sendMessage(String type, String data) {
    incrementClock(); // Evento: enviar mensagem
    String message = type + "|" + data + "|" + lamportClock;
    // ... enviar
}

private void receiveMessage(String message) {
    String[] parts = message.split("\\|");
    int receivedClock = Integer.parseInt(parts[parts.length - 1]);
    updateClock(receivedClock); // Atualizar relógio
    // ... processar mensagem
}
```

### 📊 Propriedades

**Propriedade 1: Causalidade**
```
Se evento A → B (A causa B), então LC(A) < LC(B)
```

**Propriedade 2: Ordenação Parcial**
```
Se LC(A) < LC(B), A pode ter causado B (mas não é certeza)
Se LC(A) = LC(B), eventos são concorrentes
```

**Uso no Sistema:**
1. Ordernar requisições de CS (Ricart-Agrawala)
2. Detectar causalidade entre eventos
3. Debug e logging ordenado

### ⚡ Vantagens
- Não precisa sincronizar relógios físicos
- Baixo overhead (apenas um inteiro)
- Captura causalidade entre eventos

### ⚠️ Desvantagens
- Não ordena eventos concorrentes
- Não representa tempo real
- Pode crescer indefinidamente

---

## 📊 Comparação dos Algoritmos

| Aspecto | Bully | Ricart-Agrawala | Réplicas | Lamport |
|---------|-------|-----------------|----------|---------|
| **Mensagens** | O(n²) | 2(n-1) | O(n) | 0 overhead |
| **Coordenador** | Sim | Não | Não | Não |
| **Tolerância a Falhas** | Média | Baixa | Alta | N/A |
| **Latência** | Alta | Baixa | Média | Zero |
| **Complexidade** | Baixa | Média | Alta | Baixa |
| **Uso** | Eleição | Mutex | Sincronização | Ordenação |

---

## 🎯 Integração dos Algoritmos

```
┌─────────────────────────────────────────┐
│         SISTEMA INTEGRADO               │
├─────────────────────────────────────────┤
│                                         │
│  1. Lamport Clock                       │
│     ↓ (Ordena todos os eventos)         │
│                                         │
│  2. Bully Election                      │
│     ↓ (Elege coordenador)               │
│                                         │
│  3. Ricart-Agrawala                     │
│     ↓ (Protege seção crítica)           │
│                                         │
│  4. Replicação                          │
│     ↓ (Mantém consistência)             │
│                                         │
│  Resultado: Sistema distribuído         │
│            confiável e consistente      │
└─────────────────────────────────────────┘
```

**Fluxo Típico:**
1. Sistema inicia → **Bully** elege coordenador
2. Cliente responde → Servidor usa **Lamport** para timestamp
3. Atualizar placar → **Ricart-Agrawala** garante exclusão mútua
4. Após atualização → **Replicação** sincroniza todos os servidores

---

## 🔬 Experimentos Sugeridos

### Experimento 1: Performance da Eleição
- Medir tempo de eleição com 2, 3, 5, 10 servidores
- Contar mensagens trocadas
- Analisar overhead

### Experimento 2: Contenção em CS
- Simular 3 servidores tentando CS simultaneamente
- Medir tempo de espera de cada um
- Verificar fairness (ordem FIFO?)

### Experimento 3: Latência de Replicação
- Medir tempo entre atualização local e réplica
- Testar com diferentes números de servidores
- Verificar impacto na experiência do usuário

### Experimento 4: Tolerância a Falhas
- Simular falha do coordenador durante jogo
- Medir tempo de recuperação
- Verificar perda de dados

---

## 💡 Melhorias Futuras

1. **Otimização do Bully**: Implementar anel lógico para reduzir mensagens
2. **Ricart-Agrawala com Quorum**: Apenas maioria precisa responder
3. **Replicação Assíncrona**: Melhorar performance com eventual consistency
4. **Vector Clocks**: Substituir Lamport para capturar mais causalidade
5. **Heartbeat**: Detectar falhas mais rapidamente