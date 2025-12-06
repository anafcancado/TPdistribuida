import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**
 * Cliente para o Sistema de Quiz Distribuído
 * Conecta-se a qualquer servidor disponível no cluster
 */
public class DistributedQuizClient extends JFrame {
    private Socket tcpSocket;
    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    
    private String serverIP;
    private int serverPort;
    private String playerName;
    private boolean connected = false;
    
    // GUI Components
    private JTextField serverIPField;
    private JTextField serverPortField;
    private JTextField playerNameField;
    private JButton connectButton;
    private JLabel statusLabel;
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
    }
    
    private void setupGUI() {
        setTitle("Quiz Client - Sistema Distribuído");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        
        JLabel ipLabel = new JLabel("IP do Servidor:");
        ipLabel.setForeground(Color.WHITE);
        serverIPField = new JTextField("localhost", 15);
        
        JLabel portLabel = new JLabel("Porta do Servidor:");
        portLabel.setForeground(Color.WHITE);
        serverPortField = new JTextField("5001", 10);
        
        JLabel nameLabel = new JLabel("Seu Nome:");
        nameLabel.setForeground(Color.WHITE);
        playerNameField = new JTextField(15);
        
        connectButton = new JButton("Conectar");
        connectButton.setBackground(new Color(76, 175, 80));
        connectButton.setForeground(Color.WHITE);
        connectButton.addActionListener(e -> connectToServer());
        
        statusLabel = new JLabel("Digite o IP, porta e seu nome");
        statusLabel.setForeground(Color.WHITE);
        
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        connectionPanel.add(titleLabel, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        connectionPanel.add(ipLabel, gbc);
        gbc.gridx = 1;
        connectionPanel.add(serverIPField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        connectionPanel.add(portLabel, gbc);
        gbc.gridx = 1;
        connectionPanel.add(serverPortField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        connectionPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        connectionPanel.add(playerNameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        connectionPanel.add(connectButton, gbc);
        
        gbc.gridy = 5;
        connectionPanel.add(statusLabel, gbc);
        
        // Instruções
        JTextArea instructions = new JTextArea(
            "INSTRUÇÕES:\n" +
            "1. Digite o IP e porta de qualquer servidor do cluster\n" +
            "   (Exemplo: localhost:5001, localhost:5002, etc.)\n" +
            "2. Digite seu nome\n" +
            "3. Clique em Conectar\n" +
            "4. Aguarde o coordenador iniciar o jogo"
        );
        instructions.setEditable(false);
        instructions.setBackground(new Color(46, 125, 50));
        instructions.setForeground(Color.WHITE);
        instructions.setFont(new Font("Arial", Font.PLAIN, 11));
        instructions.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        gbc.gridy = 6;
        connectionPanel.add(instructions, gbc);
    }
    
    private void setupGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(new Color(33, 150, 243));
        
        // Topo
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.setBackground(new Color(25, 118, 210));
        
        JLabel titleLabel = new JLabel("Quiz em Andamento");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        topPanel.add(titleLabel);
        
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
    
    private void connectToServer() {
        serverIP = serverIPField.getText().trim();
        String portText = serverPortField.getText().trim();
        playerName = playerNameField.getText().trim();
        
        if (serverIP.isEmpty() || portText.isEmpty() || playerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha todos os campos!");
            return;
        }
        
        try {
            serverPort = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Porta inválida!");
            return;
        }
        
        connectButton.setEnabled(false);
        statusLabel.setText("Conectando...");
        
        new Thread(() -> {
            try {
                tcpSocket = new Socket(serverIP, serverPort);
                tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
                tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);
                
                // Enviar nome do jogador
                tcpOut.println("JOIN|" + playerName);
                
                SwingUtilities.invokeLater(() -> {
                    connected = true;
                    showGamePanel();
                    statusLabel.setText("Conectado! Aguardando o jogo começar...");
                });
                
                // Thread para escutar mensagens TCP
                startTCPListener();
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "Erro ao conectar: " + e.getMessage() + 
                        "\nVerifique se o servidor está rodando.");
                    connectButton.setEnabled(true);
                    statusLabel.setText("Erro na conexão");
                });
            }
        }).start();
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
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, 
                            "Conexão perdida com o servidor!\n" +
                            "O servidor pode ter falhado ou sido desconectado.");
                        showConnectionPanel();
                        connected = false;
                        connectButton.setEnabled(true);
                    });
                }
            }
        }).start();
    }
    
    private void processTCPMessage(String message) {
        String[] parts = message.split("\\|");
        
        SwingUtilities.invokeLater(() -> {
            switch (parts[0]) {
                case "JOINED":
                    questionLabel.setText("<html><div style='text-align: center; padding: 20px;'>" +
                        "Bem-vindo, " + parts[1] + "!<br><br>" +
                        "Você está conectado ao cluster distribuído.<br>" +
                        "Aguardando o coordenador iniciar o jogo...</div></html>");
                    break;
                
                case "QUESTION":
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
        if (!canAnswer) return;
        
        // Desabilitar todos os botões após responder
        canAnswer = false;
        for (JButton button : answerButtons) {
            button.setEnabled(false);
        }
        
        // Destacar resposta selecionada
        answerButtons[answerIndex].setBackground(answerButtons[answerIndex].getBackground().darker());
        
        // Enviar resposta via TCP
        tcpOut.println("ANSWER|" + answerIndex);
        
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
                
                // Destacar o próprio jogador
                if (name.equals(playerName)) {
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
        setTitle("Quiz Client - Conectar ao Servidor");
    }
    
    private void showGamePanel() {
        CardLayout cl = (CardLayout) getContentPane().getLayout();
        cl.show(getContentPane(), "GAME");
        setTitle("Quiz Client - " + playerName + " conectado");
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