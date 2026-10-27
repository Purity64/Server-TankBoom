import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class ClientUI extends JFrame {
    private JTextField txtIp, txtPort, txtHwid, txtInput;
    private JTextArea txtLog;
    private JButton btnConnect, btnSend;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    public ClientUI() {
        setTitle("Tank Game Network Test Client");
        setSize(550, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 1. แถบตั้งค่าการเชื่อมต่อด้านบน (Top Panel)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        
        topPanel.add(new JLabel("IP:"));
        txtIp = new JTextField("127.0.0.1", 8);
        topPanel.add(txtIp);

        topPanel.add(new JLabel("Port:"));
        txtPort = new JTextField("50101", 5);
        topPanel.add(txtPort);

        topPanel.add(new JLabel("HWID:"));
        // สุ่ม HWID ตั้งต้นเพื่อให้กดทดสอบได้หลายหน้าจอพร้อมกัน
        txtHwid = new JTextField("TANK_" + (int)(Math.random() * 1000), 8);
        topPanel.add(txtHwid);

        btnConnect = new JButton("Connect");
        topPanel.add(btnConnect);
        add(topPanel, BorderLayout.NORTH);

        // 2. กล่องแสดงข้อความ/Log ตรงกลาง (Center Panel)
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(txtLog);
        add(scrollPane, BorderLayout.CENTER);

        // 3. แถบพิมพ์ข้อความส่งด้านล่าง (Bottom Panel)
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        txtInput = new JTextField();
        txtInput.setEnabled(false); // ปิดไว้ก่อนจนกว่าจะต่อ Server ติด
        btnSend = new JButton("Send");
        btnSend.setEnabled(false);

        bottomPanel.add(txtInput, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Event Listeners ---

        // กดปุ่ม Connect/Disconnect
        btnConnect.addActionListener(e -> {
            if (!isConnected) {
                connectToServer();
            } else {
                disconnect();
            }
        });

        // กดปุ่ม Send หรือกดปุ่ม Enter ในช่องพิมพ์
        btnSend.addActionListener(e -> sendMessage());
        txtInput.addActionListener(e -> sendMessage());

        // ปิด Socket ให้สะอาดเมื่อปิดหน้าต่าง UI
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    // ระบบเชื่อมต่อเข้า Server
    private void connectToServer() {
        String ip = txtIp.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String hwid = txtHwid.getText().trim();

        if (hwid.isEmpty()) {
            JOptionPane.showMessageDialog(this, "กรุณากรอก HWID");
            return;
        }

        // เปิด Thread แยกเพื่อไม่ให้ UI ค้างระหว่างกำลังเชื่อมต่อ Network
        new Thread(() -> {
            try {
                log("[CONNECTING] กำลังเชื่อมต่อไปยัง " + ip + ":" + port + "...");
                socket = new Socket(ip, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 🟢 1. ส่ง HWID บรรทัดแรกไปให้ Server ตรวจสอบตามเงื่อนไขของ Server.java
                out.println(hwid);

                isConnected = true;

                // อัปเดตสถานะปุ่มบน UI
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setText("Disconnect");
                    txtIp.setEnabled(false);
                    txtPort.setEnabled(false);
                    txtHwid.setEnabled(false);
                    txtInput.setEnabled(true);
                    btnSend.setEnabled(true);
                });

                log("[SUCCESS] เชื่อมต่อสำเร็จ! (ส่ง HWID: " + hwid + ")");

                // 🟢 2. วนลูปฟังข้อมูลจาก Server (การ "รับ" ข้อมูล)
                String incomingMessage;
                while (isConnected && (incomingMessage = in.readLine()) != null) {
                    String msg = incomingMessage;
                    log("[RECEIVED]: " + msg);

                    // 💡 ตรงนี้สามารถนำข้อความไปส่งต่อให้เกม เช่น สั่งให้ Tank คันอื่นขยับ
                    // parseCommand(msg);
                }

            } catch (Exception e) {
                log("[ERROR] การเชื่อมต่อมีปัญหาหรือถูกปฏิเสธ: " + e.getMessage());
            } finally {
                disconnect();
            }
        }).start();
    }

    // ระบบส่งข้อความ (การ "ส่ง" ข้อมูล)
    private void sendMessage() {
        String msg = txtInput.getText().trim();
        if (!msg.isEmpty() && isConnected && out != null) {
            // 🟢 ส่งข้อความไปหา Server ผ่าน out.println()
            out.println(msg); 
            log("[SENT]: " + msg);
            txtInput.setText(""); // เคลียร์ช่องพิมพ์
        }
    }

    // ระบบตัดการเชื่อมต่อ
    private void disconnect() {
        if (!isConnected && socket == null) return;

        isConnected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            btnConnect.setText("Connect");
            txtIp.setEnabled(true);
            txtPort.setEnabled(true);
            txtHwid.setEnabled(true);
            txtInput.setEnabled(false);
            btnSend.setEnabled(false);
            log("[DISCONNECTED] ตัดการเชื่อมต่อแล้ว");
        });
    }

    // พิมพ์ Log ออกหน้าจอ UI พร้อมเลื่อนลงล่างสุดอัตโนมัติ
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientUI().setVisible(true);
        });
    }
}