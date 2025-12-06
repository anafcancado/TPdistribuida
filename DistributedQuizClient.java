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
        setMinimumSize(new Dimension(800, 600));
        
        setupConnectionPanel();
        setupGamePanel();
        
        add(connectionPanel, "CONNECTION");
        add(gamePanel, "GAME");
        
        showConnectionPanel();
        
        setSize(900, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void setupConnectionPanel() {
        connectionPanel = new JPanel(new BorderLayout(0, 0));
        connectionPanel.setBackground(new Color(250, 250, 250));
        
        // Painel central com formulário centralizado
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(new Color(250, 250, 250));
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(224, 224, 224), 1),
            BorderFactory.createEmptyBorder(40, 50, 40, 50)
        ));
        formPanel.setMaximumSize(new Dimension(500, 600));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        
        // Título
        JLabel titleLabel = new JLabel("Quiz Competitivo Distribuído");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(33, 33, 33));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 30, 0);
        formPanel.add(titleLabel, gbc);
        
        // Status de descoberta
        statusLabel = new JLabel("Procurando servidores via Multicast...");
        statusLabel.setForeground(new Color(255, 152, 0));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 8, 0);
        formPanel.add(statusLabel, gbc);
        
        // Label de conexão
        connectionLabel = new JLabel("Aguardando descoberta...");
        connectionLabel.setForeground(new Color(117, 117, 117));
        connectionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        connectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 25, 0);
        formPanel.add(connectionLabel, gbc);
        
        // Campo de nome
        JLabel nameLabel = new JLabel("Seu Nome:");
        nameLabel.setForeground(new Color(66, 66, 66));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 5, 0);
        formPanel.add(nameLabel, gbc);
        
        playerNameField = new JTextField(20);
        playerNameField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        playerNameField.setPreferredSize(new Dimension(300, 38));
        playerNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 189, 189), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 20, 0);
        formPanel.add(playerNameField, gbc);
        
        // Botão conectar
        connectButton = new JButton("Conectar ao Coordenador");
        connectButton.setEnabled(false);
        connectButton.setBackground(new Color(66, 165, 245));
        connectButton.setForeground(Color.WHITE);
        connectButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        connectButton.setPreferredSize(new Dimension(300, 44));
        connectButton.setBorderPainted(false);
        connectButton.setFocusPainted(false);
        connectButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        connectButton.addActionListener(e -> connectToCoordinator());
        gbc.gridy = 5;
        gbc.insets = new Insets(0, 0, 15, 0);
        formPanel.add(connectButton, gbc);
        
        // Score label
        scoreLabel = new JLabel("Pontuação: 0");
        scoreLabel.setForeground(new Color(76, 175, 80));
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 6;
        gbc.insets = new Insets(5, 0, 0, 0);
        formPanel.add(scoreLabel, gbc);
        
        // Adicionar formPanel no centro do wrapper
        GridBagConstraints wrapperGbc = new GridBagConstraints();
        wrapperGbc.gridx = 0;
        wrapperGbc.gridy = 0;
        wrapperGbc.weightx = 1.0;
        wrapperGbc.weighty = 1.0;
        wrapperGbc.anchor = GridBagConstraints.CENTER;
        centerWrapper.add(formPanel, wrapperGbc);
        
        // Painel de instruções no rodapé
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(new Color(245, 245, 245));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        
        JTextArea instructions = new JTextArea(
            "INSTRUÇÕES (Modo Automático)\n\n" +
            "• O sistema está procurando servidores automaticamente\n" +
            "• Quando um coordenador for encontrado, o botão será habilitado\n" +
            "• Digite seu nome e clique em 'Conectar ao Coordenador'\n" +
            "• Se a conexão cair, o sistema reconectará automaticamente\n" +
            "• Sua pontuação será mantida na reconexão"
        );
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        instructions.setBackground(new Color(245, 245, 245));
        instructions.setForeground(new Color(97, 97, 97));
        instructions.setFont(new Font("SansSerif", Font.PLAIN, 12));
        instructions.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        footerPanel.add(instructions, BorderLayout.CENTER);
        
        connectionPanel.add(centerWrapper, BorderLayout.CENTER);
        connectionPanel.add(footerPanel, BorderLayout.SOUTH);
    }
    
    private void setupGamePanel() {
        gamePanel = new JPanel(new BorderLayout(0, 0));
        gamePanel.setBackground(new Color(250, 250, 250));
        
        // Topo - Informações do servidor
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(63, 81, 181));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("Quiz em Andamento", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        infoPanel.setBackground(new Color(63, 81, 181));
        
        JLabel serverInfoLabel = new JLabel("Servidor Atual: ");
        serverInfoLabel.setForeground(new Color(200, 200, 200));
        serverInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        
        JLabel serverIdLabel = new JLabel("#?");
        serverIdLabel.setForeground(new Color(255, 235, 59));
        serverIdLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        this.connectionLabel = serverIdLabel;
        
        infoPanel.add(serverInfoLabel);
        infoPanel.add(serverIdLabel);
        
        topPanel.add(titleLabel, BorderLayout.CENTER);
        topPanel.add(infoPanel, BorderLayout.SOUTH);
        
        // Split entre área do jogo (esquerda) e placar (direita)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerSize(8);
        splitPane.setDividerLocation(600);
        splitPane.setBorder(null);
        
        // Centro - Pergunta e Respostas (ÁREA PRINCIPAL)
        JPanel centerPanel = new JPanel(new BorderLayout(0, 15));
        centerPanel.setBackground(new Color(250, 250, 250));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        
        // Pergunta
        questionLabel = new JLabel("<html><div style='text-align: center; padding: 10px;'>Aguardando pergunta...</div></html>");
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        questionLabel.setForeground(new Color(33, 33, 33));
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        questionLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 189, 189), 2),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        questionLabel.setOpaque(true);
        questionLabel.setBackground(Color.WHITE);
        
        // Painel de respostas com GridLayout responsivo
        JPanel answersPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        answersPanel.setBackground(new Color(250, 250, 250));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        answerButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            final int answerIndex = i;
            answerButtons[i] = new JButton("Opção " + (i + 1));
            answerButtons[i].setFont(new Font("SansSerif", Font.BOLD, 16));
            answerButtons[i].setBackground(buttonColors[i]);
            answerButtons[i].setForeground(Color.WHITE);
            answerButtons[i].setOpaque(true);
            answerButtons[i].setBorderPainted(false);
            answerButtons[i].setFocusPainted(false);
            answerButtons[i].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            answerButtons[i].addActionListener(e -> selectAnswer(answerIndex));
            answerButtons[i].setEnabled(false);
            
            // Hover effect
            final Color originalColor = buttonColors[i];
            answerButtons[i].addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent evt) {
                    if (answerButtons[answerIndex].isEnabled()) {
                        answerButtons[answerIndex].setBackground(originalColor.darker());
                    }
                }
                public void mouseExited(java.awt.event.MouseEvent evt) {
                    answerButtons[answerIndex].setBackground(originalColor);
                }
            });
            
            answersPanel.add(answerButtons[i]);
        }
        
        centerPanel.add(questionLabel, BorderLayout.NORTH);
        centerPanel.add(answersPanel, BorderLayout.CENTER);
        
        // Painel de Placar (LATERAL DIREITA)
        JPanel scoreboardPanel = new JPanel(new BorderLayout());
        scoreboardPanel.setBackground(new Color(245, 245, 245));
        scoreboardPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        scoreboardPanel.setPreferredSize(new Dimension(280, 500));
        
        JLabel scoreboardTitle = new JLabel("Placar Global", SwingConstants.CENTER);
        scoreboardTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        scoreboardTitle.setForeground(new Color(63, 81, 181));
        scoreboardTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        scoreboardArea = new JTextArea();
        scoreboardArea.setEditable(false);
        scoreboardArea.setBackground(Color.WHITE);
        scoreboardArea.setForeground(new Color(33, 33, 33));
        scoreboardArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        scoreboardArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane scoreScrollPane = new JScrollPane(scoreboardArea);
        scoreScrollPane.setBorder(BorderFactory.createLineBorder(new Color(224, 224, 224), 1));
        scoreScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        scoreboardPanel.add(scoreboardTitle, BorderLayout.NORTH);
        scoreboardPanel.add(scoreScrollPane, BorderLayout.CENTER);
        
        // Adicionar ao split pane
        splitPane.setLeftComponent(centerPanel);
        splitPane.setRightComponent(scoreboardPanel);
        
        gamePanel.add(topPanel, BorderLayout.NORTH);
        gamePanel.add(splitPane, BorderLayout.CENTER);
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
                    statusLabel.setForeground(new Color(76, 175, 80));
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
                        statusLabel.setForeground(new Color(255, 152, 0));
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
                    statusLabel.setForeground(new Color(76, 175, 80));
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
