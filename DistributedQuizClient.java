import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**
 * Cliente Inteligente para o Sistema de Quiz Distribuído
 * - Descoberta automática de servidores via Multicast
 * - Reconexão automática em caso de falha
 * - Mantém estado do jogador (nome e pontuação)
 */
public class DistributedQuizClient extends JFrame {
    // Multicast Configuration
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    
    private Socket tcpSocket;
    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    
    private String serverIP;
    private int serverPort;
    private String playerName;
    private int currentScore = 0;
    private boolean connected = false;
    private volatile boolean running = true;
    private int currentServerId = -1;
    
    // Multicast discovery
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private volatile int coordinatorId = -1;
    private volatile String coordinatorIP = null;
    private volatile int coordinatorPort = -1;
    
    // GUI Components
    private JLabel statusLabel;
    private JLabel connectionLabel;
    private JLabel scoreLabel;
    private JTextField playerNameField;
    private JButton connectButton;
    private JLabel questionLabel;
    private JButton[] answerButtons;
    private JTextArea scoreboardArea;
    private JPanel gamePanel;
    private JPanel connectionPanel;
    
    // Game state
    private String currentQuestion = "";
    private String[] currentOptions = new String[4];
    private boolean canAnswer = false;
    
    private final Color[] buttonColors = {
        new Color(229, 57, 53),  // Vermelho
        new Color(67, 160, 71),  // Verde
        new Color(255, 193, 7),  // Amarelo
        new Color(30, 136, 229)  // Azul
    };
    
    public DistributedQuizClient() {
        setupGUI();
        startMulticastDiscovery();
    }
    
    private void setupGUI() {
        setTitle("Quiz Client - Sistema Distribuído (Auto-Discovery)");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
                System.exit(0);
            }
        });
        setLayout(new CardLayout());
        
        setupConnectionPanel();
        setupGamePanel();
        
        add(connectionPanel, "CONNECTION");
        add(gamePanel, "GAME");
        
        showConnectionPanel();
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void setupConnectionPanel() {
        connectionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        connectionPanel.setBackground(new Color(46, 125, 50));
        
        JLabel titleLabel = new JLabel("Quiz Competitivo Distribuído");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        statusLabel = new JLabel("Procurando servidores via Multicast...");
        statusLabel.setForeground(Color.YELLOW);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        connectionLabel = new JLabel("Aguardando descoberta...");
        connectionLabel.setForeground(Color.WHITE);
        
        JLabel nameLabel = new JLabel("Seu Nome:");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        playerNameField = new JTextField(20);
        playerNameField.setFont(new Font("Arial", Font.PLAIN, 14));
        
        connectButton = new JButton("Conectar ao Coordenador");
        connectButton.setEnabled(false);
        connectButton.setBackground(new Color(76, 175, 80));
        connectButton.setForeground(Color.WHITE);
        connectButton.setFont(new Font("Arial", Font.BOLD, 14));
        connectButton.addActionListener(e -> connectToCoordinator());
        
        scoreLabel = new JLabel("Pontuação: 0");
        scoreLabel.setForeground(Color.WHITE);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        connectionPanel.add(titleLabel, gbc);
        
        gbc.gridy = 1;
        connectionPanel.add(statusLabel, gbc);
        
        gbc.gridy = 2;
        connectionPanel.add(connectionLabel, gbc);
        
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        connectionPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        connectionPanel.add(playerNameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        connectionPanel.add(connectButton, gbc);
        
        gbc.gridy = 5;
        connectionPanel.add(scoreLabel, gbc);
        
        // Instruções
        JTextArea instructions = new JTextArea(
            "INSTRUÇÕES (Modo Automático):\n\n" +
            "1. O sistema está procurando servidores automaticamente\n" +
            "2. Quando um coordenador for encontrado, o botão será habilitado\n" +
            "3. Digite seu nome e clique em 'Conectar ao Coordenador'\n" +
            "4. Se a conexão cair, o sistema reconectará automaticamente\n" +
            "5. Sua pontuação será mantida na reconexão"
        );
        instructions.setEditable(false);
        instructions.setBackground(new Color(46, 125, 50));
        instructions.setForeground(Color.WHITE);
        instructions.setFont(new Font("Arial", Font.PLAIN, 11));
        instructions.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.WHITE, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        connectionPanel.add(instructions, gbc);
    }
    
    private void setupGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(new Color(33, 150, 243));
        
        // Topo
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(25, 118, 210));
        
        JLabel titleLabel = new JLabel("Quiz em Andamento", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        infoPanel.setBackground(new Color(25, 118, 210));
        
        JLabel serverInfoLabel = new JLabel("Servidor Atual: ");
        serverInfoLabel.setForeground(Color.WHITE);
        serverInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JLabel serverIdLabel = new JLabel("#?");
        serverIdLabel.setForeground(Color.YELLOW);
        serverIdLabel.setFont(new Font("Arial", Font.BOLD, 12));
        this.connectionLabel = serverIdLabel;
        
        infoPanel.add(serverInfoLabel);
        infoPanel.add(serverIdLabel);
        
        topPanel.add(titleLabel, BorderLayout.CENTER);
        topPanel.add(infoPanel, BorderLayout.SOUTH);
        
        // Centro - Pergunta e Respostas
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(33, 150, 243));
        
        questionLabel = new JLabel("<html><div style='text-align: center;'>Aguardando pergunta...</div></html>");
        questionLabel.setFont(new Font("Arial", Font.BOLD, 18));
        questionLabel.setForeground(Color.WHITE);
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        questionLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.WHITE, 2),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        questionLabel.setOpaque(true);
        questionLabel.setBackground(new Color(21, 101, 192));
        
        // Painel de respostas
        JPanel answersPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        answersPanel.setBackground(new Color(240, 240, 240));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        answerButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            final int answerIndex = i;
            answerButtons[i] = new JButton("Opção " + (i + 1));
            answerButtons[i].setFont(new Font("Arial", Font.BOLD, 16));
            answerButtons[i].setBackground(buttonColors[i]);
            answerButtons[i].setForeground(Color.WHITE);
            answerButtons[i].setPreferredSize(new Dimension(200, 80));
            answerButtons[i].setOpaque(true);
            answerButtons[i].setContentAreaFilled(true);
            answerButtons[i].setBorderPainted(false);
            answerButtons[i].addActionListener(e -> selectAnswer(answerIndex));
            answerButtons[i].setEnabled(false);
            answersPanel.add(answerButtons[i]);
        }
        
        centerPanel.add(questionLabel, BorderLayout.NORTH);
        centerPanel.add(answersPanel, BorderLayout.CENTER);
        
        // Placar
        scoreboardArea = new JTextArea(20, 20);
        scoreboardArea.setEditable(false);
        scoreboardArea.setBackground(new Color(48, 63, 159));
        scoreboardArea.setForeground(Color.WHITE);
        scoreboardArea.setFont(new Font("Monospaced", Font.BOLD, 13));
        
        JScrollPane scoreScrollPane = new JScrollPane(scoreboardArea);
        scoreScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.WHITE), "Placar Global",
            0, 0, new Font("Arial", Font.BOLD, 14), Color.WHITE
        ));
        
        gamePanel.add(topPanel, BorderLayout.NORTH);
        gamePanel.add(centerPanel, BorderLayout.CENTER);
        gamePanel.add(scoreScrollPane, BorderLayout.EAST);
    }
    
    // ==================== MULTICAST DISCOVERY ====================
    
    private void startMulticastDiscovery() {
        new Thread(() -> {
            try {
                multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                
                // Join multicast group
                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                if (netIf == null) {
                    netIf = NetworkInterface.getNetworkInterfaces().nextElement();
                }
                
                multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, MULTICAST_PORT), netIf);
                log("Escutando grupo Multicast: " + MULTICAST_ADDRESS);
                
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
    }
    
    private void processMulticastMessage(String message, InetAddress from) {
        String[] parts = message.split("\\|");
        
        if (parts[0].equals("HEARTBEAT")) {
            int serverId = Integer.parseInt(parts[1]);
            int clientPort = Integer.parseInt(parts[2]);
            boolean isCoord = Boolean.parseBoolean(parts[4]);
            
            if (isCoord) {
                boolean coordinatorChanged = (coordinatorId != serverId);
                coordinatorId = serverId;
                coordinatorIP = from.getHostAddress();
                coordinatorPort = clientPort;
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Coordenador encontrado!");
                    statusLabel.setForeground(Color.GREEN);
                    connectionLabel.setText("Servidor #" + coordinatorId + " em " + 
                        coordinatorIP + ":" + coordinatorPort);
                    
                    if (!connected && playerNameField.getText().trim().isEmpty()) {
                        connectButton.setEnabled(true);
                    }
                });
                
                // Se estávamos conectados mas perdemos a conexão, reconectar
                if (!connected && playerName != null && !playerName.isEmpty()) {
                    log("Reconectando ao coordenador #" + coordinatorId + "...");
                    reconnect();
                }
                // Se coordenador mudou e estamos conectados, reconectar ao novo
                else if (connected && coordinatorChanged && currentServerId != coordinatorId) {
                    log("Coordenador mudou de #" + currentServerId + " para #" + coordinatorId);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Mudança de coordenador detectada. Reconectando...");
                        statusLabel.setForeground(Color.YELLOW);
                    });
                    // Fechar conexão atual e reconectar
                    closeCurrentConnection();
                    reconnect();
                }
            }
        } else if (parts[0].equals("COORDINATOR_ANNOUNCE")) {
            int newCoordId = Integer.parseInt(parts[1]);
            
            // Coordenador anunciado, aguardar heartbeat para obter detalhes
            if (coordinatorId != newCoordId) {
                log("Novo coordenador anunciado: #" + newCoordId);
                coordinatorId = newCoordId;
            }
        }
    }
    
    // ==================== CONNECTION MANAGEMENT ====================
    
    private void connectToCoordinator() {
        playerName = playerNameField.getText().trim();
        
        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite seu nome!");
            return;
        }
        
        if (coordinatorIP == null || coordinatorPort == -1) {
            JOptionPane.showMessageDialog(this, "Nenhum coordenador disponível!");
            return;
        }
        
        connectButton.setEnabled(false);
        playerNameField.setEnabled(false);
        
        connectToServer(coordinatorIP, coordinatorPort);
    }
    
    private void connectToServer(String ip, int port) {
        new Thread(() -> {
            try {
                log("Conectando a " + ip + ":" + port);
                
                tcpSocket = new Socket(ip, port);
                tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
                tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);
                
                // Enviar nome do jogador
                tcpOut.println("JOIN|" + playerName);
                
                SwingUtilities.invokeLater(() -> {
                    connected = true;
                    statusLabel.setText("Conectado!");
                    statusLabel.setForeground(Color.GREEN);
                });
                
                // Thread para escutar mensagens TCP
                startTCPListener();
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    log("Erro ao conectar: " + e.getMessage());
                    statusLabel.setText("Erro na conexão. Procurando outro servidor...");
                    statusLabel.setForeground(Color.RED);
                    connected = false;
                    
                    // Tentar reconectar após 3 segundos
                    new Timer(3000, evt -> {
                        ((Timer)evt.getSource()).stop();
                        if (!connected && coordinatorIP != null) {
                            reconnect();
                        }
                    }).start();
                });
            }
        }).start();
    }
    
    private void reconnect() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Reconectando ao servidor #" + coordinatorId + "...");
            statusLabel.setForeground(Color.YELLOW);
        });
        
        if (coordinatorIP != null && coordinatorPort != -1) {
            connectToServer(coordinatorIP, coordinatorPort);
        }
    }
    
    private void closeCurrentConnection() {
        connected = false;
        try {
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
        } catch (IOException e) {
            log("Erro fechando conexão: " + e.getMessage());
        }
    }
    
    private void startTCPListener() {
        new Thread(() -> {
            try {
                String inputLine;
                while ((inputLine = tcpIn.readLine()) != null) {
                    processTCPMessage(inputLine);
                }
            } catch (IOException e) {
                if (connected) {
                    log("Conexão perdida com o servidor #" + currentServerId);
                    connected = false;
                    
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Conexão perdida. Procurando novo coordenador...");
                        statusLabel.setForeground(Color.RED);
                    });
                    
                    // Aguardar 1 segundo e tentar reconectar
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        // Ignore
                    }
                    
                    if (!connected && coordinatorIP != null && coordinatorPort != -1) {
                        log("Tentando reconectar automaticamente...");
                        reconnect();
                    }
                }
            }
        }, "TCPListener").start();
    }
    
    private void processTCPMessage(String message) {
        String[] parts = message.split("\\|");
        
        log("Mensagem recebida: " + parts[0]);
        
        SwingUtilities.invokeLater(() -> {
            switch (parts[0]) {
                case "JOINED":
                    currentServerId = Integer.parseInt(parts[2]);
                    log("Conectado ao servidor #" + currentServerId);
                    
                    showGamePanel();
                    questionLabel.setText("<html><div style='text-align: center; padding: 20px;'>" +
                        "Bem-vindo, " + parts[1] + "!<br><br>" +
                        "Conectado ao Servidor #" + currentServerId + "<br>" +
                        "Aguardando o jogo começar...</div></html>");
                    
                    if (connectionLabel != null) {
                        connectionLabel.setText("#" + currentServerId);
                    }
                    break;
                
                case "QUESTION":
                    log("Nova pergunta recebida: " + parts[1]);
                    currentQuestion = parts[1];
                    currentOptions = Arrays.copyOfRange(parts, 2, 6);
                    displayQuestion();
                    break;
                
                case "SCOREBOARD":
                    displayScoreboard(parts);
                    break;
                
                case "GAME_END":
                    JOptionPane.showMessageDialog(this, 
                        "Jogo finalizado!\n" +
                        "Obrigado por participar!\n" +
                        "Confira o placar final.");
                    canAnswer = false;
                    for (JButton button : answerButtons) {
                        button.setEnabled(false);
                    }
                    questionLabel.setText("<html><div style='text-align: center; padding: 20px;'>" +
                        "JOGO FINALIZADO<br><br>" +
                        "Confira o placar ao lado →</div></html>");
                    break;
            }
        });
    }
    
    private void displayQuestion() {
        questionLabel.setText("<html><div style='text-align: center; padding: 20px;'>" +
            currentQuestion + "</div></html>");
        
        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText("<html><div style='text-align: center; padding: 10px;'>" +
                currentOptions[i] + "</div></html>");
            answerButtons[i].setBackground(buttonColors[i]);
            answerButtons[i].setEnabled(true);
        }
        
        canAnswer = true;
    }
    
    private void selectAnswer(int answerIndex) {
        if (!canAnswer || !connected) return;
        
        // Desabilitar todos os botões após responder
        canAnswer = false;
        for (JButton button : answerButtons) {
            button.setEnabled(false);
        }
        
        // Destacar resposta selecionada
        answerButtons[answerIndex].setBackground(answerButtons[answerIndex].getBackground().darker());
        
        // Enviar resposta via TCP
        if (tcpOut != null) {
            tcpOut.println("ANSWER|" + answerIndex);
        }
        
        questionLabel.setText("<html><div style='text-align: center; padding: 20px;'>" +
            "Resposta enviada! ✓<br><br>" +
            "Aguardando outras respostas...</div></html>");
    }
    
    private void displayScoreboard(String[] parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════╗\n");
        sb.append("║    PLACAR GLOBAL           ║\n");
        sb.append("║    (Sincronizado)          ║\n");
        sb.append("╚════════════════════════════╝\n\n");
        
        for (int i = 1; i < parts.length; i++) {
            String[] playerData = parts[i].split(":");
            if (playerData.length == 2) {
                String name = playerData[0];
                String points = playerData[1];
                
                // Atualizar pontuação própria
                if (name.equals(playerName)) {
                    currentScore = Integer.parseInt(points);
                    scoreLabel.setText("Pontuação: " + currentScore);
                    sb.append("► ");
                } else {
                    sb.append("  ");
                }
                
                sb.append(i).append("º  ")
                  .append(String.format("%-15s", name))
                  .append("  ")
                  .append(String.format("%4s", points))
                  .append(" pts\n");
            }
        }
        
        scoreboardArea.setText(sb.toString());
    }
    
    private void showConnectionPanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "CONNECTION");
        setTitle("Quiz Client - Procurando Servidores...");
    }
    
    private void showGamePanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "GAME");
        setTitle("Quiz Client - " + playerName + " [Servidor #" + currentServerId + "]");
    }
    
    private void log(String message) {
        System.out.println("[Cliente] " + message);
    }
    
    private void shutdown() {
        running = false;
        
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
            if (tcpSocket != null) tcpSocket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new DistributedQuizClient());
    }
}
