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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Cursor;

/**
 * Servidor Distribuído de Quiz Competitivo com Multicast e Tolerância a Falhas
 * - Descoberta automática de servidores via Multicast
 * - Heartbeat para detecção de falhas
 * - Eleição automática de coordenador
 * - Replicação de estado
 */
public class DistributedQuizServer extends JFrame {
    // Multicast Configuration
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int HEARTBEAT_INTERVAL = 2000; // 2 segundos
    private static final int HEARTBEAT_TIMEOUT = 6000; // 6 segundos (3 heartbeats perdidos)
    
    // Configurações de rede
    private final int serverId;
    private final int clientPort;
    private final int serverPort;
    private final Map<Integer, ServerInfo> activeServers = new ConcurrentHashMap<>();
    
    // Servidores TCP/UDP
    private ServerSocket clientListener;
    private ServerSocket serverListener;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    
    // Estado do servidor
    private volatile boolean isCoordinator = false;
    private volatile int coordinatorId = -1;
    private int lamportClock = 0;
    private final Object clockLock = new Object();
    private volatile boolean running = true;
    
    // Clientes e outros servidores
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<Integer, ServerConnection> servers = new ConcurrentHashMap<>();
    
    // Estado do jogo (replicado)
    private final Map<String, Integer> globalScoreboard = new ConcurrentHashMap<>();
    private List<Question> questions = new ArrayList<>();
    private volatile int currentQuestionIndex = 0;
    private volatile boolean gameActive = false;
    
    // Ricart-Agrawala para exclusão mútua
    private final Queue<MutexRequest> requestQueue = new PriorityQueue<>();
    private volatile boolean requestingCS = false;
    private int requestTimestamp = 0;
    private final Set<Integer> replyReceived = ConcurrentHashMap.newKeySet();
    
    // Correção 1: Race condition na eleição
    private final Object electionLock = new Object();
    private volatile boolean electionInProgress = false;
    
    // Correção 2: Timer gerenciado
    private Timer currentQuestionTimer = null;
    
    // Correção 4: Estado da pergunta atual
    private QuestionState currentQuestionState = null;
    
    // Correção 5: Scoreboard sincronizado
    private final Object scoreboardLock = new Object();
    
    // GUI
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel coordLabel;
    private JLabel playersLabel;
    private JLabel clockLabel;
    private JLabel serversLabel;
    private JButton startGameButton;
    private JButton electButton;
    
    // Heartbeat tracking
    private final Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private Timer heartbeatTimer;
    private Timer failureDetectionTimer;
    
    public DistributedQuizServer(int serverId, int clientPort, int serverPort) {
        this.serverId = serverId;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        
        initializeQuestions();
        setupGUI();
        startServer();
        startMulticastDiscovery();
        startHeartbeat();
        startFailureDetection();
        
        // Iniciar eleição após 5 segundos se não houver coordenador
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (coordinatorId == -1) {
                    log("Nenhum coordenador detectado. Iniciando eleição...");
                    startElection();
                }
            }
        }, 5000);
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
        setTitle("Servidor Distribuído #" + serverId + " [MULTICAST]");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
                System.exit(0);
            }
        });
        setLayout(new BorderLayout(0, 0));
        setMinimumSize(new Dimension(900, 600));
        
        // Painel superior com informações do servidor
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(250, 250, 250));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        // Título e info principal
        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setBackground(new Color(250, 250, 250));
        
        JLabel titleLabel = new JLabel("Servidor #" + serverId + " - Sistema Distribuído");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(new Color(33, 33, 33));
        
        JPanel statusGrid = new JPanel(new GridLayout(2, 3, 15, 8));
        statusGrid.setBackground(new Color(250, 250, 250));
        
        statusLabel = new JLabel("Status: Iniciando...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(97, 97, 97));
        
        coordLabel = new JLabel("Coordenador: Desconhecido");
        coordLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        coordLabel.setForeground(new Color(97, 97, 97));
        
        serversLabel = new JLabel("Servidores Ativos: 0");
        serversLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        serversLabel.setForeground(new Color(97, 97, 97));
        
        playersLabel = new JLabel("Jogadores: 0");
        playersLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        playersLabel.setForeground(new Color(97, 97, 97));
        
        clockLabel = new JLabel("Relógio Lamport: 0");
        clockLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        clockLabel.setForeground(new Color(97, 97, 97));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setBackground(new Color(250, 250, 250));
        
        startGameButton = new JButton("Iniciar Jogo");
        startGameButton.setEnabled(false);
        startGameButton.setBackground(new Color(76, 175, 80));
        startGameButton.setForeground(Color.WHITE);
        startGameButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        startGameButton.setBorderPainted(false);
        startGameButton.setFocusPainted(false);
        startGameButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startGameButton.setPreferredSize(new Dimension(130, 35));
        startGameButton.addActionListener(e -> initiateGameStart());
        
        electButton = new JButton("Forçar Eleição");
        electButton.setBackground(new Color(255, 152, 0));
        electButton.setForeground(Color.WHITE);
        electButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        electButton.setBorderPainted(false);
        electButton.setFocusPainted(false);
        electButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        electButton.setPreferredSize(new Dimension(130, 35));
        electButton.addActionListener(e -> startElection());
        electButton.setVisible(false);
        
        buttonPanel.add(startGameButton);
        buttonPanel.add(electButton);
        
        statusGrid.add(statusLabel);
        statusGrid.add(coordLabel);
        statusGrid.add(serversLabel);
        statusGrid.add(playersLabel);
        statusGrid.add(clockLabel);
        statusGrid.add(buttonPanel);
        
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(statusGrid, BorderLayout.CENTER);
        
        topPanel.add(headerPanel, BorderLayout.CENTER);
        
        // Área de log com scroll responsivo
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        logArea.setLineWrap(false);
        logArea.setWrapStyleWord(false);
        logArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 15, 15, 15),
            BorderFactory.createLineBorder(new Color(189, 189, 189), 1)
        ));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setVisible(true);
        
        log("Servidor #" + serverId + " iniciado com Multicast!");
        log("Multicast: " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);
    }
    
    // ==================== MULTICAST DISCOVERY ====================
    
    // Correção 6: NetworkInterface seguro
    private NetworkInterface getSafeNetworkInterface() {
        try {
            NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            if (netIf != null && netIf.isUp() && netIf.supportsMulticast()) {
                return netIf;
            }
        } catch (Exception e) {
            log("Erro obtendo interface local: " + e.getMessage());
        }
        
        // Fallback: procurar primeira interface válida
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface netIf = interfaces.nextElement();
                if (netIf.isUp() && netIf.supportsMulticast() && !netIf.isLoopback()) {
                    return netIf;
                }
            }
        } catch (Exception e) {
            log("Erro procurando interface alternativa: " + e.getMessage());
        }
        
        return null;
    }
    
    private void startMulticastDiscovery() {
        new Thread(() -> {
            try {
                multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                
                // Join multicast group
                NetworkInterface netIf = getSafeNetworkInterface();
                if (netIf == null) {
                    log("ERRO: Nenhuma interface de rede válida encontrada!");
                    return;
                }
                
                multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, MULTICAST_PORT), netIf);
                log("Entrou no grupo Multicast: " + MULTICAST_ADDRESS);
                
                // Listen for multicast messages
                byte[] buffer = new byte[1024];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, packet.getLength());
                    processMulticastMessage(message, packet.getAddress());
                }
            } catch (IOException e) {
                if (running) {
                    log("Erro no Multicast: " + e.getMessage());
                }
            }
        }, "MulticastListener").start();
        
        statusLabel.setText("Status: Escutando Multicast");
    }
    
    private void startHeartbeat() {
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        }, 1000, HEARTBEAT_INTERVAL);
    }
    
    private void sendHeartbeat() {
        // Correção 7: Validar multicast socket
        if (multicastSocket == null || multicastSocket.isClosed()) {
            return;
        }
        
        try {
            incrementClock();
            String message = String.format("HEARTBEAT|%d|%d|%d|%b|%d",
                serverId, clientPort, serverPort, isCoordinator, lamportClock);
            
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, multicastGroup, MULTICAST_PORT);
            
            multicastSocket.send(packet);
            
        } catch (IOException e) {
            if (running) {
                log("Erro enviando heartbeat: " + e.getMessage());
            }
        }
    }
    
    private void startFailureDetection() {
        failureDetectionTimer = new Timer(true);
        failureDetectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                detectFailures();
            }
        }, HEARTBEAT_TIMEOUT, HEARTBEAT_INTERVAL);
    }
    
    private void detectFailures() {
        long now = System.currentTimeMillis();
        List<Integer> failedServers = new ArrayList<>();
        
        for (Map.Entry<Integer, Long> entry : lastHeartbeat.entrySet()) {
            if (now - entry.getValue() > HEARTBEAT_TIMEOUT) {
                failedServers.add(entry.getKey());
            }
        }
        
        for (Integer failedId : failedServers) {
            handleServerFailure(failedId);
        }
    }
    
    private void handleServerFailure(int failedId) {
        log("FALHA DETECTADA: Servidor #" + failedId + " não responde!");
        
        lastHeartbeat.remove(failedId);
        activeServers.remove(failedId);
        ServerConnection conn = servers.remove(failedId);
        if (conn != null) {
            conn.close();
        }
        
        updateServerCount();
        
        // Se o coordenador falhou, iniciar eleição
        if (failedId == coordinatorId) {
            log("COORDENADOR FALHOU! Iniciando eleição automática...");
            coordinatorId = -1;
            isCoordinator = false;
            updateCoordLabel();
            
            // Aguardar um pouco para garantir que todos detectaram
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    startElection();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    private void processMulticastMessage(String message, InetAddress from) {
        String[] parts = message.split("\\|");
        
        if (parts[0].equals("HEARTBEAT")) {
            int senderId = Integer.parseInt(parts[1]);
            
            // Ignorar próprio heartbeat
            if (senderId == this.serverId) return;
            
            int senderClientPort = Integer.parseInt(parts[2]);
            int senderServerPort = Integer.parseInt(parts[3]);
            boolean senderIsCoord = Boolean.parseBoolean(parts[4]);
            int senderClock = Integer.parseInt(parts[5]);
            
            updateClock(senderClock);
            
            // Atualizar informações do servidor
            lastHeartbeat.put(senderId, System.currentTimeMillis());
            
            ServerInfo info = activeServers.get(senderId);
            if (info == null) {
                info = new ServerInfo(senderId, from.getHostAddress(), 
                    senderClientPort, senderServerPort);
                activeServers.put(senderId, info);
                log("Novo servidor descoberto: #" + senderId + " em " + from.getHostAddress());
                
                // Conectar ao novo servidor
                connectToServer(senderId, from.getHostAddress(), senderServerPort);
                
                // Se não temos coordenador e o novo servidor é coordenador
                if (coordinatorId == -1 && senderIsCoord) {
                    coordinatorId = senderId;
                    updateCoordLabel();
                    
                    // Solicitar estado atual se houver jogo ativo
                    requestStateSync();
                }
                
                updateServerCount();
            } else {
                info.lastSeen = System.currentTimeMillis();
            }
            
            // Atualizar status do coordenador
            if (senderIsCoord && coordinatorId != senderId) {
                coordinatorId = senderId;
                isCoordinator = false;
                updateCoordLabel();
            }
        } else if (parts[0].equals("STATE_REQUEST")) {
            int requesterId = Integer.parseInt(parts[1]);
            updateClock(Integer.parseInt(parts[2]));
            if (isCoordinator) {
                sendStateSyncTo(requesterId);
            }
        } else if (parts[0].equals("COORDINATOR_ANNOUNCE")) {
            int newCoordId = Integer.parseInt(parts[1]);
            int clock = Integer.parseInt(parts[2]);
            updateClock(clock);
            coordinatorId = newCoordId;
            isCoordinator = (newCoordId == serverId);
            updateCoordLabel();
            log("Coordenador anunciado via Multicast: #" + newCoordId);
        }
    }
    
    private void requestStateSync() {
        if (coordinatorId != -1 && coordinatorId != serverId) {
            try {
                incrementClock();
                String message = "STATE_REQUEST|" + serverId + "|" + lamportClock;
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, multicastGroup, MULTICAST_PORT);
                multicastSocket.send(packet);
                log("Solicitando sincronização de estado do coordenador #" + coordinatorId);
            } catch (IOException e) {
                log("Erro solicitando estado: " + e.getMessage());
            }
        }
    }
    
    private void sendStateSyncTo(int targetId) {
        ServerConnection conn = servers.get(targetId);
        if (conn != null) {
            incrementClock();
            
            // Enviar estado do jogo
            String gameState = String.format("STATE_SYNC|%b|%d|%d",
                gameActive, currentQuestionIndex, lamportClock);
            conn.sendMessage(gameState);
            
            // Enviar scoreboard
            for (Map.Entry<String, Integer> entry : globalScoreboard.entrySet()) {
                conn.sendMessage("SCORE_SYNC|" + entry.getKey() + "|" + entry.getValue());
            }
            
            log("Estado sincronizado para servidor #" + targetId);
        }
    }
    
    // ==================== SERVER MANAGEMENT ====================
    
    private void startServer() {
        // Thread para aceitar clientes
        new Thread(() -> {
            try {
                clientListener = new ServerSocket(clientPort);
                log("Escutando clientes na porta " + clientPort);
                while (running) {
                    Socket socket = clientListener.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                if (running) {
                    log("Erro no listener de clientes: " + e.getMessage());
                }
            }
        }, "ClientListener").start();
        
        // Thread para aceitar outros servidores
        new Thread(() -> {
            try {
                serverListener = new ServerSocket(serverPort);
                log("Escutando servidores na porta " + serverPort);
                while (running) {
                    Socket socket = serverListener.accept();
                    handleIncomingServerConnection(socket);
                }
            } catch (IOException e) {
                if (running) {
                    log("Erro no listener de servidores: " + e.getMessage());
                }
            }
        }, "ServerListener").start();
    }
    
    private void handleIncomingServerConnection(Socket socket) {
        log("Servidor conectou de: " + socket.getRemoteSocketAddress());
        // A identificação será feita via mensagem HELLO
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                String hello = in.readLine();
                
                if (hello != null && hello.startsWith("HELLO|")) {
                    String[] parts = hello.split("\\|");
                    int otherId = Integer.parseInt(parts[1]);
                    
                    ServerConnection conn = new ServerConnection(socket, otherId);
                    servers.put(otherId, conn);
                    new Thread(conn).start();
                    
                    log("Servidor #" + otherId + " identificado e conectado");
                }
            } catch (IOException e) {
                log("Erro processando conexão de servidor: " + e.getMessage());
            }
        }).start();
    }
    
    private void connectToServer(int otherId, String address, int port) {
        // Evitar conexões duplicadas
        if (servers.containsKey(otherId)) return;
        
        new Thread(() -> {
            try {
                Thread.sleep(500); // Pequeno delay
                Socket socket = new Socket(address, port);
                
                // Enviar HELLO primeiro
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("HELLO|" + serverId + "|" + clientPort + "|" + serverPort);
                
                ServerConnection conn = new ServerConnection(socket, otherId);
                servers.put(otherId, conn);
                new Thread(conn).start();
                
                log("Conectado ao servidor #" + otherId);
                
            } catch (Exception e) {
                log("Erro conectando ao servidor #" + otherId + ": " + e.getMessage());
            }
        }).start();
    }
    
    // ==================== ELEIÇÃO BULLY ====================
    
    private void startElection() {
        // Correção 1: Prevenir race condition na eleição
        synchronized (electionLock) {
            if (electionInProgress) {
                log("Eleição já em andamento, ignorando nova solicitação");
                return;
            }
            electionInProgress = true;
        }
        
        try {
            log("=== INICIANDO ELEIÇÃO BULLY ===");
            incrementClock();
            
            boolean sentElection = false;
            for (Integer otherId : activeServers.keySet()) {
                if (otherId > serverId) {
                    sendToServer(otherId, "ELECTION|" + serverId + "|" + lamportClock);
                    sentElection = true;
                }
            }
            
            if (!sentElection) {
                // Sou o maior ID ativo
                becomeCoordinator();
            } else {
                // Aguardar resposta OK por 3 segundos
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        synchronized (electionLock) {
                            if (!isCoordinator && (coordinatorId == -1 || coordinatorId == serverId)) {
                                becomeCoordinator();
                            }
                            electionInProgress = false;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } finally {
            if (!electionInProgress) {
                // Já resolvido no bloco acima
            }
        }
    }
    
    private void becomeCoordinator() {
        log(">>> ME TORNEI COORDENADOR <<<");
        isCoordinator = true;
        coordinatorId = serverId;
        updateCoordLabel();
        
        // Marcar eleição como concluída
        synchronized (electionLock) {
            electionInProgress = false;
        }
        
        // Anunciar via Multicast
        try {
            incrementClock();
            String message = "COORDINATOR_ANNOUNCE|" + serverId + "|" + lamportClock;
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, multicastGroup, MULTICAST_PORT);
            multicastSocket.send(packet);
            log("Coordenador anunciado via Multicast");
        } catch (IOException e) {
            log("Erro anunciando coordenador: " + e.getMessage());
        }
        
        // Enviar para servidores via TCP também
        for (Integer otherId : activeServers.keySet()) {
            sendToServer(otherId, "COORDINATOR|" + serverId + "|" + lamportClock);
        }
        
        SwingUtilities.invokeLater(() -> {
            startGameButton.setEnabled(clients.size() > 0 && !gameActive);
        });
        
        // Retomar jogo se estava ativo quando assumimos coordenação
        resumeGameAsNewCoordinator();
    }
    
    private void resumeGameAsNewCoordinator() {
        if (gameActive && currentQuestionIndex < questions.size()) {
            log("=== RESUMINDO JOGO COMO NOVO COORDENADOR ===");
            log("Questão atual: " + currentQuestionIndex);
            
            // Cancelar timer anterior se existir
            if (currentQuestionTimer != null) {
                currentQuestionTimer.cancel();
                currentQuestionTimer = null;
            }
            
            // Criar novo estado para a questão atual
            currentQuestionState = new QuestionState();
            
            // Resetar respostas de todos os clientes para a questão atual
            for (ClientHandler client : clients.values()) {
                client.resetAnswer();
            }
            
            // Reenviar a questão atual para todos os clientes
            Question q = questions.get(currentQuestionIndex);
            String questionData = "QUESTION|" + q.question + "|" + 
                                  String.join("|", q.options);
            
            broadcastToClients(questionData);
            log("Questão " + (currentQuestionIndex + 1) + " reenviada aos clientes");
            
            // Reiniciar o timer de 15 segundos para esta questão
            currentQuestionTimer = new Timer();
            currentQuestionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (isCoordinator && gameActive) {
                        log("Timer expirado - processando respostas da questão " + (currentQuestionIndex + 1));
                        processQuestionEnd();
                    }
                }
            }, 15000);
            
        } else if (gameActive && currentQuestionIndex >= questions.size()) {
            // Se o jogo deveria ter terminado, terminar agora
            log("Jogo deveria ter terminado. Finalizando...");
            endGame();
        }
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
                
                // Enviar REQUEST para todos os servidores ativos
                for (Integer otherId : activeServers.keySet()) {
                    sendToServer(otherId, "CS_REQUEST|" + serverId + "|" + requestTimestamp);
                }
                
                // Aguardar REPLY de todos
                int timeout = 0;
                while (replyReceived.size() < activeServers.size() && timeout < 50) {
                    Thread.sleep(100);
                    timeout++;
                }
                
                if (replyReceived.size() == activeServers.size() || activeServers.isEmpty()) {
                    log("CS concedida! Executando seção crítica...");
                    criticalSection.run();
                } else {
                    log("Timeout aguardando CS replies (" + 
                        replyReceived.size() + "/" + activeServers.size() + ")");
                    // Executar mesmo assim se timeout
                    criticalSection.run();
                }
                
                requestingCS = false;
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
        
        for (Integer otherId : activeServers.keySet()) {
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
    
    // ==================== LÓGICA DO JOGO ====================
    
    private void initiateGameStart() {
        if (!isCoordinator) {
            JOptionPane.showMessageDialog(this, 
                "Apenas o coordenador pode iniciar o jogo!");
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
        
        // Cancelar timer anterior se existir
        if (currentQuestionTimer != null) {
            currentQuestionTimer.cancel();
            currentQuestionTimer = null;
        }
        
        // Criar novo estado para a questão atual
        currentQuestionState = new QuestionState();
        
        Question q = questions.get(currentQuestionIndex);
        String questionData = "QUESTION|" + q.question + "|" + 
                              String.join("|", q.options);
        
        broadcastToClients(questionData);
        replicateGameState("QUESTION", currentQuestionIndex + "");
        
        log("Pergunta " + (currentQuestionIndex + 1) + " enviada aos " + clients.size() + " clientes");
        
        // Timer de 15 segundos
        currentQuestionTimer = new Timer();
        currentQuestionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isCoordinator) {
                    processQuestionEnd();
                }
            }
        }, 15000);
    }
    
    private void processQuestionEnd() {
        Question q = questions.get(currentQuestionIndex);
        
        for (ClientHandler client : clients.values()) {
            if (client.hasAnswered() && client.getLastAnswer() == q.correctAnswer) {
                int points = 100;
                client.addPoints(points);
                // Usar método sincronizado para atualizar scoreboard global
                updatePlayerScore(client.getPlayerName(), points);
            }
            client.resetAnswer();
        }
        
        sendScoreboardToClients();
        
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                currentQuestionIndex++;
                sendNextQuestion();
            }
        }, 3000);
    }
    
    
    private void updatePlayerScore(String playerName, int pointsToAdd) {
        synchronized (scoreboardLock) {
            int currentScore = globalScoreboard.getOrDefault(playerName, 0);
            int newScore = currentScore + pointsToAdd;
            globalScoreboard.put(playerName, newScore);
            // Replicar atualização para outros servidores
            replicateGameState("SCORE_UPDATE", playerName + ":" + newScore);
            log("Score atualizado: " + playerName + " = " + newScore + " pontos");
        }
    }
    
    private void endGame() {
        gameActive = false;
        broadcastToClients("GAME_END");
        replicateGameState("GAME_END", "");
        log("Jogo finalizado!");
        SwingUtilities.invokeLater(() -> {
            startGameButton.setEnabled(isCoordinator && clients.size() > 0);
        });
    }
    
    private void sendScoreboardToClients() {
        StringBuilder sb = new StringBuilder("SCOREBOARD");
        
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(globalScoreboard.entrySet());
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
        private volatile boolean active = true;
        
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
                while (active && (line = in.readLine()) != null) {
                    processServerMessage(line, otherId);
                }
            } catch (IOException e) {
                if (active) {
                    log("Conexão TCP com servidor #" + otherId + " perdida");
                }
            }
        }
        
        public void sendMessage(String msg) {
            if (out != null && active) {
                out.println(msg);
            }
        }
        
        public void close() {
            active = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private void processServerMessage(String message, int fromId) {
        String[] parts = message.split("\\|");
        String type = parts[0];
        
        switch (type) {
            case "HELLO":
                int senderId = Integer.parseInt(parts[1]);
                updateClock(Integer.parseInt(parts[3]));
                log("Servidor #" + senderId + " identificado via TCP");
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
                log("Novo coordenador via TCP: #" + coordinatorId);
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
                
            case "STATE_SYNC":
                updateClock(Integer.parseInt(parts[4]));
                gameActive = Boolean.parseBoolean(parts[1]);
                currentQuestionIndex = Integer.parseInt(parts[2]);
                log("Estado do jogo sincronizado");
                break;
                
            case "SCORE_SYNC":
                String playerName = parts[1];
                int score = Integer.parseInt(parts[2]);
                globalScoreboard.put(playerName, score);
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
                
            case "PLAYER_JOIN":
                if (!globalScoreboard.containsKey(data)) {
                    globalScoreboard.put(data, 0);
                }
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
                    
                    // Verificar se já existe um cliente com esse nome conectado
                    ClientHandler existingClient = null;
                    for (ClientHandler client : clients.values()) {
                        if (client != this && playerName.equals(client.getPlayerName())) {
                            existingClient = client;
                            break;
                        }
                    }
                    
                    // Se já existe, fechar socket antigo e remover a conexão antiga
                    if (existingClient != null) {
                        String oldId = null;
                        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                            if (entry.getValue() == existingClient) {
                                oldId = entry.getKey();
                                break;
                            }
                        }
                        if (oldId != null) {
                            try {
                                existingClient.socket.close();
                            } catch (IOException e) {
                                // Ignora erro ao fechar socket
                            }
                            clients.remove(oldId);
                            log("Conexão anterior de " + playerName + " foi fechada");
                        }
                    }
                    
                    clients.put(clientId, this);
                    sendMessage("JOINED|" + playerName + "|" + serverId);
                    
                    // Restaurar pontuação se jogador já existia
                    if (globalScoreboard.containsKey(playerName)) {
                        score = globalScoreboard.get(playerName);
                        log("Jogador reconectou: " + playerName + " (Score: " + score + ")");
                    } else {
                        globalScoreboard.put(playerName, 0);
                        log("Novo jogador: " + playerName);
                        // Apenas replicar se for realmente novo
                        replicateGameState("PLAYER_JOIN", playerName);
                    }
                    
                    updatePlayerCount();
                    
                    // Enviar estado atual se jogo ativo
                    if (gameActive && currentQuestionIndex < questions.size()) {
                        Question q = questions.get(currentQuestionIndex);
                        String questionData = "QUESTION|" + q.question + "|" + 
                                              String.join("|", q.options);
                        sendMessage(questionData);
                    }
                    
                    // Enviar scoreboard atual
                    sendScoreboardToClients();
                    break;
                    
                case "ANSWER":
                    if (gameActive && !answered) {
                        lastAnswer = Integer.parseInt(parts[1]);
                        answered = true;
                        // Salvar resposta no estado da questão
                        if (currentQuestionState != null) {
                            currentQuestionState.pendingAnswers.put(playerName, lastAnswer);
                        }
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
    
    private static class QuestionState {
        Map<String, Integer> pendingAnswers = new ConcurrentHashMap<>();
    }
    
    private static class ServerInfo {
        int id;
        String address;
        int clientPort;
        int serverPort;
        long lastSeen;
        
        ServerInfo(int id, String addr, int cPort, int sPort) {
            this.id = id;
            this.address = addr;
            this.clientPort = cPort;
            this.serverPort = sPort;
            this.lastSeen = System.currentTimeMillis();
        }
    }
    
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
            coordLabel.setForeground(isCoordinator ? new Color(0, 150, 0) : Color.BLACK);
        });
    }
    
    private void updatePlayerCount() {
        SwingUtilities.invokeLater(() -> {
            playersLabel.setText("Jogadores: " + clients.size());
            if (isCoordinator) {
                startGameButton.setEnabled(clients.size() > 0 && !gameActive);
            }
        });
    }
    
    private void updateServerCount() {
        SwingUtilities.invokeLater(() -> {
            serversLabel.setText("Servidores Ativos: " + (activeServers.size() + 1));
        });
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    // ==================== SHUTDOWN ====================
    
    private void shutdown() {
        log("Encerrando servidor...");
        running = false;
        if (heartbeatTimer != null) heartbeatTimer.cancel();
        if (failureDetectionTimer != null) failureDetectionTimer.cancel();
        
        try {
            if (multicastSocket != null) {
                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                if (netIf == null) {
                    netIf = NetworkInterface.getNetworkInterfaces().nextElement();
                }
                multicastSocket.leaveGroup(
                    new InetSocketAddress(multicastGroup, MULTICAST_PORT), netIf);
                multicastSocket.close();
            }
            if (clientListener != null) clientListener.close();
            if (serverListener != null) serverListener.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    // ==================== MAIN ====================
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String config = null;
            
            // Se argumentos foram passados via linha de comando
            if (args != null && args.length >= 3) {
                config = args[0] + "," + args[1] + "," + args[2];
            } else {
                // Caso contrário, mostrar diálogo
                config = JOptionPane.showInputDialog(
                    "Configuração do servidor:\n" +
                    "Formato: serverID,clientPort,serverPort\n" +
                    "Exemplos:\n" +
                    "  Servidor 1: 1,5001,6001\n" +
                    "  Servidor 2: 2,5002,6002\n" +
                    "  Servidor 3: 3,5003,6003\n\n" +
                    "NOTA: Descoberta automática via Multicast ativada!",
                    "1,5001,6001"
                );
            }
            
            if (config == null || config.trim().isEmpty()) return;
            
            String[] parts = config.split(",");
            int serverId = Integer.parseInt(parts[0].trim());
            int clientPort = Integer.parseInt(parts[1].trim());
            int serverPort = Integer.parseInt(parts[2].trim());
            
            DistributedQuizServer server = new DistributedQuizServer(
                serverId, clientPort, serverPort);
            
            // Adicionar shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.shutdown();
            }));
        });
    }
}
