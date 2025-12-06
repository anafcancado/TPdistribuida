import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Servidor Distribuído de Quiz Competitivo
 * Implementa: Eleição Bully, Ricart-Agrawala, Consistência de Réplicas, Relógios de Lamport
 */
public class DistributedQuizServer extends JFrame {
    // Configurações de rede
    private final int serverId;
    private final int clientPort;
    private final int serverPort;
    private final Map<Integer, String> serverAddresses = new ConcurrentHashMap<>();
    
    // Servidores TCP/UDP
    private ServerSocket clientListener;
    private ServerSocket serverListener;
    private DatagramSocket udpSocket;
    
    // Estado do servidor
    private boolean isCoordinator = false;
    private int coordinatorId = -1;
    private int lamportClock = 0;
    private final Object clockLock = new Object();
    
    // Clientes e outros servidores
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<Integer, ServerConnection> servers = new ConcurrentHashMap<>();
    
    // Estado do jogo (replicado)
    private final Map<String, Integer> globalScoreboard = new ConcurrentHashMap<>();
    private java.util.List<Question> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private boolean gameActive = false;
    
    // Ricart-Agrawala para exclusão mútua
    private final Queue<MutexRequest> requestQueue = new PriorityQueue<>();
    private boolean requestingCS = false;
    private int requestTimestamp = 0;
    private final Set<Integer> replyReceived = ConcurrentHashMap.newKeySet();
    
    // GUI
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel coordLabel;
    private JLabel playersLabel;
    private JLabel clockLabel;
    private JButton startGameButton;
    private JButton electButton;
    
    public DistributedQuizServer(int serverId, int clientPort, int serverPort) {
        this.serverId = serverId;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        
        initializeQuestions();
        setupGUI();
        startServer();
    }
    
    private void initializeQuestions() {
        questions.add(new Question("Qual é a capital do Brasil?",
            new String[]{"São Paulo", "Rio de Janeiro", "Brasília", "Belo Horizonte"}, 2));
        questions.add(new Question("Quantos planetas há no sistema solar?",
            new String[]{"7", "8", "9", "10"}, 1));
        questions.add(new Question("Qual é o maior oceano do mundo?",
            new String[]{"Atlântico", "Pacífico", "Índico", "Ártico"}, 1));
        questions.add(new Question("Em que ano o Brasil foi descoberto?",
            new String[]{"1500", "1501", "1499", "1502"}, 0));
        questions.add(new Question("Qual linguagem é mais usada para web?",
            new String[]{"Python", "Java", "JavaScript", "C++"}, 2));
    }
    
    private void setupGUI() {
        setTitle("Servidor Distribuído #" + serverId + " - Porta Clientes: " + clientPort);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Painel superior
        JPanel topPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Status do Servidor"));
        
        statusLabel = new JLabel("Status: Iniciando...");
        coordLabel = new JLabel("Coordenador: Desconhecido");
        playersLabel = new JLabel("Jogadores: 0");
        clockLabel = new JLabel("Relógio Lamport: 0");
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        startGameButton = new JButton("Iniciar Jogo");
        startGameButton.setEnabled(false);
        startGameButton.addActionListener(e -> initiateGameStart());
        
        electButton = new JButton("Forçar Eleição");
        electButton.addActionListener(e -> startElection());
        
        buttonPanel.add(startGameButton);
        buttonPanel.add(electButton);
        
        topPanel.add(statusLabel);
        topPanel.add(coordLabel);
        topPanel.add(playersLabel);
        topPanel.add(clockLabel);
        topPanel.add(buttonPanel);
        
        // Área de log
        logArea = new JTextArea(25, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log do Servidor"));
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        
        log("Servidor #" + serverId + " iniciado!");
    }
    
    private void startServer() {
        // Thread para aceitar clientes
        new Thread(() -> {
            try {
                clientListener = new ServerSocket(clientPort);
                log("Escutando clientes na porta " + clientPort);
                while (true) {
                    Socket socket = clientListener.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                log("Erro no listener de clientes: " + e.getMessage());
            }
        }).start();
        
        // Thread para aceitar outros servidores
        new Thread(() -> {
            try {
                serverListener = new ServerSocket(serverPort);
                log("Escutando servidores na porta " + serverPort);
                while (true) {
                    Socket socket = serverListener.accept();
                    handleServerConnection(socket);
                }
            } catch (IOException e) {
                log("Erro no listener de servidores: " + e.getMessage());
            }
        }).start();
        
        // Socket UDP para broadcasts
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(serverPort + 1000);
                log("UDP inicializado na porta " + (serverPort + 1000));
                while (true) {
                    byte[] buffer = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    processUDPMessage(msg, packet.getAddress());
                }
            } catch (IOException e) {
                log("Erro no UDP: " + e.getMessage());
            }
        }).start();
        
        statusLabel.setText("Status: Aguardando outros servidores...");
    }
    
    // ==================== ELEIÇÃO BULLY ====================
    
    private void startElection() {
        log("=== INICIANDO ELEIÇÃO BULLY ===");
        incrementClock();
        
        boolean sentElection = false;
        for (Integer otherId : serverAddresses.keySet()) {
            if (otherId > serverId) {
                sendToServer(otherId, "ELECTION|" + serverId + "|" + lamportClock);
                sentElection = true;
            }
        }
        
        if (!sentElection) {
            // Sou o maior ID, me torno coordenador
            becomeCoordinator();
        } else {
            // Aguardar resposta OK por 2 segundos
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    // Se nenhum OK foi recebido, me torno coordenador
                    if (!isCoordinator && coordinatorId != serverId) {
                        becomeCoordinator();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    private void becomeCoordinator() {
        log(">>> ME TORNEI COORDENADOR <<<");
        isCoordinator = true;
        coordinatorId = serverId;
        updateCoordLabel();
        
        // Anunciar para todos os servidores com ID menor
        for (Integer otherId : serverAddresses.keySet()) {
            sendToServer(otherId, "COORDINATOR|" + serverId + "|" + lamportClock);
        }
        
        startGameButton.setEnabled(clients.size() > 0);
    }
    
    // ==================== RICART-AGRAWALA ====================
    
    private void requestCriticalSection(Runnable criticalSection) {
        new Thread(() -> {
            try {
                incrementClock();
                requestingCS = true;
                requestTimestamp = lamportClock;
                replyReceived.clear();
                
                log("Solicitando CS com timestamp " + requestTimestamp);
                
                // Enviar REQUEST para todos os servidores
                for (Integer otherId : serverAddresses.keySet()) {
                    sendToServer(otherId, "CS_REQUEST|" + serverId + "|" + requestTimestamp);
                }
                
                // Aguardar REPLY de todos
                int timeout = 0;
                while (replyReceived.size() < serverAddresses.size() && timeout < 50) {
                    Thread.sleep(100);
                    timeout++;
                }
                
                if (replyReceived.size() == serverAddresses.size()) {
                    log("CS concedida! Executando seção crítica...");
                    criticalSection.run();
                } else {
                    log("Timeout aguardando CS replies");
                }
                
                requestingCS = false;
                
                // Processar requisições enfileiradas
                processQueuedRequests();
                
            } catch (InterruptedException e) {
                log("Erro na CS: " + e.getMessage());
            }
        }).start();
    }
    
    private void processQueuedRequests() {
        synchronized (requestQueue) {
            while (!requestQueue.isEmpty()) {
                MutexRequest req = requestQueue.poll();
                sendToServer(req.senderId, "CS_REPLY|" + serverId + "|" + lamportClock);
            }
        }
    }
    
    // ==================== REPLICAÇÃO E CONSISTÊNCIA ====================
    
    private void replicateGameState(String action, String data) {
        incrementClock();
        String message = "REPLICATE|" + action + "|" + data + "|" + lamportClock;
        
        for (Integer otherId : serverAddresses.keySet()) {
            sendToServer(otherId, message);
        }
    }
    
    private void syncScoreboard(String playerName, int score) {
        requestCriticalSection(() -> {
            globalScoreboard.put(playerName, score);
            log("Placar atualizado: " + playerName + " = " + score);
            replicateGameState("SCORE_UPDATE", playerName + ":" + score);
        });
    }
    
    // ==================== COMUNICAÇÃO ENTRE SERVIDORES ====================
    
    public void connectToServer(int otherId, String address, int port) {
        serverAddresses.put(otherId, address + ":" + port);
        
        new Thread(() -> {
            try {
                Thread.sleep(500); // Dar tempo para o outro servidor iniciar
                Socket socket = new Socket(address, port);
                ServerConnection conn = new ServerConnection(socket, otherId);
                servers.put(otherId, conn);
                new Thread(conn).start();
                
                // Enviar apresentação
                sendToServer(otherId, "HELLO|" + serverId + "|" + clientPort + "|" + serverPort);
                log("Conectado ao servidor #" + otherId);
                
                // Iniciar eleição após conectar
                if (servers.size() == serverAddresses.size()) {
                    Thread.sleep(1000);
                    startElection();
                }
            } catch (Exception e) {
                log("Erro conectando ao servidor #" + otherId + ": " + e.getMessage());
            }
        }).start();
    }
    
    private void handleServerConnection(Socket socket) {
        log("Servidor conectou: " + socket.getRemoteSocketAddress());
        // A identificação será feita via mensagem HELLO
    }
    
    private void sendToServer(int serverId, String message) {
        ServerConnection conn = servers.get(serverId);
        if (conn != null) {
            conn.sendMessage(message);
        }
    }
    
    private void broadcastToServers(String message) {
        for (ServerConnection conn : servers.values()) {
            conn.sendMessage(message);
        }
    }
    
    private void processUDPMessage(String message, InetAddress from) {
        // Implementar se necessário para descoberta de servidores
    }
    
    // ==================== LÓGICA DO JOGO ====================
    
    private void initiateGameStart() {
        if (!isCoordinator) {
            JOptionPane.showMessageDialog(this, "Apenas o coordenador pode iniciar o jogo!");
            return;
        }
        
        log("=== INICIANDO JOGO ===");
        gameActive = true;
        currentQuestionIndex = 0;
        startGameButton.setEnabled(false);
        
        replicateGameState("GAME_START", "0");
        sendNextQuestion();
    }
    
    private void sendNextQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }
        
        Question q = questions.get(currentQuestionIndex);
        String questionData = "QUESTION|" + q.question + "|" + 
                              String.join("|", q.options);
        
        broadcastToClients(questionData);
        replicateGameState("QUESTION", currentQuestionIndex + "");
        
        log("Pergunta " + (currentQuestionIndex + 1) + " enviada");
        
        // Timer de 15 segundos
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                processQuestionEnd();
            }
        }, 15000);
    }
    
    private void processQuestionEnd() {
        Question q = questions.get(currentQuestionIndex);
        
        for (ClientHandler client : clients.values()) {
            if (client.hasAnswered() && client.getLastAnswer() == q.correctAnswer) {
                int points = 100;
                client.addPoints(points);
                syncScoreboard(client.getPlayerName(), client.getScore());
            }
            client.resetAnswer();
        }
        
        sendScoreboardToClients();
        
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                currentQuestionIndex++;
                sendNextQuestion();
            }
        }, 3000);
    }
    
    private void endGame() {
        gameActive = false;
        broadcastToClients("GAME_END");
        replicateGameState("GAME_END", "");
        log("Jogo finalizado!");
        startGameButton.setEnabled(true);
    }
    
    private void sendScoreboardToClients() {
        StringBuilder sb = new StringBuilder("SCOREBOARD");
        
        java.util.List<Map.Entry<String, Integer>> sorted = new ArrayList<>(globalScoreboard.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (Map.Entry<String, Integer> entry : sorted) {
            sb.append("|").append(entry.getKey()).append(":").append(entry.getValue());
        }
        
        broadcastToClients(sb.toString());
    }
    
    private void broadcastToClients(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }
    
    // ==================== RELÓGIO DE LAMPORT ====================
    
    private void incrementClock() {
        synchronized (clockLock) {
            lamportClock++;
            SwingUtilities.invokeLater(() -> 
                clockLabel.setText("Relógio Lamport: " + lamportClock));
        }
    }
    
    private void updateClock(int receivedTime) {
        synchronized (clockLock) {
            lamportClock = Math.max(lamportClock, receivedTime) + 1;
            SwingUtilities.invokeLater(() -> 
                clockLabel.setText("Relógio Lamport: " + lamportClock));
        }
    }
    
    // ==================== HANDLERS ====================
    
    private class ServerConnection implements Runnable {
        private Socket socket;
        private int otherId;
        private BufferedReader in;
        private PrintWriter out;
        
        public ServerConnection(Socket socket, int otherId) {
            this.socket = socket;
            this.otherId = otherId;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                log("Erro criando conexão com servidor: " + e.getMessage());
            }
        }
        
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    processServerMessage(line, otherId);
                }
            } catch (IOException e) {
                log("Servidor #" + otherId + " desconectou");
            }
        }
        
        public void sendMessage(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }
    }
    
    private void processServerMessage(String message, int fromId) {
        String[] parts = message.split("\\|");
        String type = parts[0];
        
        switch (type) {
            case "HELLO":
                int senderId = Integer.parseInt(parts[1]);
                if (!servers.containsKey(senderId)) {
                    // Conexão reversa foi estabelecida
                    log("Servidor #" + senderId + " identificado");
                }
                updateClock(Integer.parseInt(parts[3]));
                break;
                
            case "ELECTION":
                updateClock(Integer.parseInt(parts[2]));
                int candidateId = Integer.parseInt(parts[1]);
                if (candidateId < serverId) {
                    sendToServer(fromId, "OK|" + serverId + "|" + lamportClock);
                    startElection();
                }
                break;
                
            case "OK":
                updateClock(Integer.parseInt(parts[2]));
                log("Recebi OK do servidor #" + fromId);
                break;
                
            case "COORDINATOR":
                updateClock(Integer.parseInt(parts[2]));
                coordinatorId = Integer.parseInt(parts[1]);
                isCoordinator = (coordinatorId == serverId);
                log("Novo coordenador: #" + coordinatorId);
                updateCoordLabel();
                break;
                
            case "CS_REQUEST":
                updateClock(Integer.parseInt(parts[2]));
                int reqId = Integer.parseInt(parts[1]);
                int reqTime = Integer.parseInt(parts[2]);
                
                if (requestingCS && (reqTime < requestTimestamp || 
                    (reqTime == requestTimestamp && reqId < serverId))) {
                    synchronized (requestQueue) {
                        requestQueue.add(new MutexRequest(reqId, reqTime));
                    }
                } else {
                    sendToServer(reqId, "CS_REPLY|" + serverId + "|" + lamportClock);
                }
                break;
                
            case "CS_REPLY":
                updateClock(Integer.parseInt(parts[2]));
                replyReceived.add(fromId);
                break;
                
            case "REPLICATE":
                updateClock(Integer.parseInt(parts[3]));
                String action = parts[1];
                String data = parts[2];
                handleReplication(action, data);
                break;
        }
    }
    
    private void handleReplication(String action, String data) {
        switch (action) {
            case "GAME_START":
                gameActive = true;
                currentQuestionIndex = Integer.parseInt(data);
                log("Jogo replicado: iniciado");
                break;
                
            case "QUESTION":
                currentQuestionIndex = Integer.parseInt(data);
                log("Questão replicada: #" + currentQuestionIndex);
                break;
                
            case "SCORE_UPDATE":
                String[] scoreData = data.split(":");
                globalScoreboard.put(scoreData[0], Integer.parseInt(scoreData[1]));
                log("Placar replicado: " + scoreData[0] + " = " + scoreData[1]);
                break;
                
            case "GAME_END":
                gameActive = false;
                log("Jogo replicado: finalizado");
                break;
        }
    }
    
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String playerName;
        private int score = 0;
        private boolean answered = false;
        private int lastAnswer = -1;
        private String clientId;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = socket.getRemoteSocketAddress().toString();
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                log("Erro criando handler de cliente: " + e.getMessage());
            }
        }
        
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    processClientMessage(line);
                }
            } catch (IOException e) {
                log("Cliente desconectou: " + playerName);
            } finally {
                clients.remove(clientId);
                updatePlayerCount();
            }
        }
        
        private void processClientMessage(String message) {
            String[] parts = message.split("\\|");
            
            switch (parts[0]) {
                case "JOIN":
                    playerName = parts[1];
                    clients.put(clientId, this);
                    sendMessage("JOINED|" + playerName);
                    globalScoreboard.put(playerName, 0);
                    log("Jogador conectou: " + playerName);
                    updatePlayerCount();
                    replicateGameState("PLAYER_JOIN", playerName);
                    break;
                    
                case "ANSWER":
                    if (gameActive && !answered) {
                        lastAnswer = Integer.parseInt(parts[1]);
                        answered = true;
                        log("Resposta de " + playerName + ": " + lastAnswer);
                    }
                    break;
            }
        }
        
        public void sendMessage(String msg) {
            if (out != null) out.println(msg);
        }
        
        public String getPlayerName() { return playerName != null ? playerName : "Jogador"; }
        public int getScore() { return score; }
        public void addPoints(int p) { score += p; }
        public boolean hasAnswered() { return answered; }
        public int getLastAnswer() { return lastAnswer; }
        public void resetAnswer() { answered = false; lastAnswer = -1; }
    }
    
    // ==================== CLASSES AUXILIARES ====================
    
    private static class Question {
        String question;
        String[] options;
        int correctAnswer;
        
        Question(String q, String[] opts, int correct) {
            this.question = q;
            this.options = opts;
            this.correctAnswer = correct;
        }
    }
    
    private static class MutexRequest implements Comparable<MutexRequest> {
        int senderId;
        int timestamp;
        
        MutexRequest(int id, int ts) {
            this.senderId = id;
            this.timestamp = ts;
        }
        
        @Override
        public int compareTo(MutexRequest other) {
            if (this.timestamp != other.timestamp) {
                return this.timestamp - other.timestamp;
            }
            return this.senderId - other.senderId;
        }
    }
    
    // ==================== UTILITÁRIOS ====================
    
    private void updateCoordLabel() {
        SwingUtilities.invokeLater(() -> {
            String status = isCoordinator ? " (EU)" : "";
            coordLabel.setText("Coordenador: Servidor #" + coordinatorId + status);
        });
    }
    
    private void updatePlayerCount() {
        SwingUtilities.invokeLater(() -> {
            playersLabel.setText("Jogadores: " + clients.size());
            if (isCoordinator) {
                startGameButton.setEnabled(clients.size() > 0);
            }
        });
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    // ==================== MAIN ====================
    
    public static void main(String[] args) {
        // First, try to get configuration from command-line args or stdin (for scripts).
        // If none provided, fall back to interactive JOptionPane dialogs.
        String config = null;
        String connections = null;

        // If args are provided as: serverId clientPort serverPort [connections]
        if (args != null && args.length >= 3) {
            config = args[0] + "," + args[1] + "," + args[2];
            if (args.length >= 4) connections = args[3];
        } else {
            // Try to read two lines from stdin (config then connections)
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                if (br.ready()) {
                    config = br.readLine();
                    connections = br.readLine();
                }
            } catch (IOException e) {
                // ignore and fall back to dialogs
            }
        }

        final String cfg = config;
        final String conns = connections;

        SwingUtilities.invokeLater(() -> {
            if (cfg != null && !cfg.isEmpty()) {
                String[] parts = cfg.split(",");
                int serverId = Integer.parseInt(parts[0].trim());
                int clientPort = Integer.parseInt(parts[1].trim());
                int serverPort = Integer.parseInt(parts[2].trim());

                DistributedQuizServer server = new DistributedQuizServer(serverId, clientPort, serverPort);

                if (conns != null && !conns.isEmpty()) {
                    for (String conn : conns.split(";")) {
                        if (conn.trim().isEmpty()) continue;
                        String[] connParts = conn.split(":");
                        try {
                            int otherId = Integer.parseInt(connParts[0]);
                            String host = connParts[1];
                            int port = Integer.parseInt(connParts[2]);
                            server.connectToServer(otherId, host, port);
                        } catch (Exception e) {
                            // ignore malformed entries
                        }
                    }
                }
            } else {
                // Interactive fallback (original behavior)
                String cfgDlg = JOptionPane.showInputDialog(
                    "Configuração do servidor:\n" +
                    "Formato: serverID,clientPort,serverPort\n" +
                    "Exemplos:\n" +
                    "  Servidor 1: 1,5001,6001\n" +
                    "  Servidor 2: 2,5002,6002\n" +
                    "  Servidor 3: 3,5003,6003",
                    "1,5001,6001"
                );

                if (cfgDlg == null) return;

                String[] parts = cfgDlg.split(",");
                int serverId = Integer.parseInt(parts[0].trim());
                int clientPort = Integer.parseInt(parts[1].trim());
                int serverPort = Integer.parseInt(parts[2].trim());

                DistributedQuizServer server = new DistributedQuizServer(serverId, clientPort, serverPort);

                // Configurar conexões com outros servidores
                String connsDlg = JOptionPane.showInputDialog(
                    "Conectar a outros servidores:\n" +
                    "Formato: id:host:porta;id:host:porta;...\n" +
                    "Exemplo: 2:localhost:6002;3:localhost:6003\n" +
                    "Deixe vazio se for o primeiro servidor"
                );

                if (connsDlg != null && !connsDlg.isEmpty()) {
                    for (String conn : connsDlg.split(";")) {
                        String[] connParts = conn.split(":");
                        int otherId = Integer.parseInt(connParts[0]);
                        String host = connParts[1];
                        int port = Integer.parseInt(connParts[2]);
                        server.connectToServer(otherId, host, port);
                    }
                }
            }
        });
    }
}