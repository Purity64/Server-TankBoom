import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientIP;
    private String Hwid;

    public ClientHandler(Socket socket, String clientIP, String Hwid, BufferedReader sharedReader) {
        this.socket = socket;
        this.clientIP = clientIP;
        this.Hwid = Hwid;
        this.in = sharedReader; // ใช้ reader เดียวกับตอน accept กันข้อมูลหาย
    }

    public String getClientIP() {
        return clientIP;
    }

    public String getHwid() {
        return Hwid;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                if (message.trim().isEmpty()) continue;
                System.out.println("[" + clientIP + "]: " + message);

                // split แบบ limit=3: data ตัวที่ 3 จะมี ":" อะไรก็ได้ ไม่ทำให้พัง
                String[] row = message.split(":", 3);
                if (row.length < 2) continue;

                String type = row[0];
                String method = row[1];
                String data = row.length > 2 ? row[2] : "";

                if (type.equalsIgnoreCase("server")) {
                    handleServerMessage(method, data);
                } else {
                    // ข้อความเกม (move/shoot) — ส่งต่อให้คนอื่น
                    Server.sendAll(message, this);
                }
            }
        } catch (IOException e) {
            // ปกติเมื่อ client ปิดการเชื่อมต่อ
        } finally {
            System.out.println("[DISCONNECT] " + Hwid);
            Server.onPlayerDisconnected(Hwid);
            Server.getAllClients().remove(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleServerMessage(String method, String data) {
        String status = Server.getServerStatus();

        // ---- ทำได้ทุกสถานะ ----
        if (method.equalsIgnoreCase("LeaveGame")) {
            Server.leaveGame(data);
            return;
        }

        // ---- ตอนกำลังเล่น ----
        if (ServerConfig.GameStatus_start.equals(status)) {
            if (method.equalsIgnoreCase("playerkill")) {
                // data = "<victim>[,<killer>]"
                String victim = data;
                String killer = "";
                int comma = data.indexOf(',');
                if (comma >= 0) {
                    victim = data.substring(0, comma);
                    killer = data.substring(comma + 1);
                }
                Server.onPlayerKilled(victim.trim(), killer.trim());
            } else if (method.equalsIgnoreCase("myhp")) {
                try {
                    Server.updatePlayerHp(Hwid, Integer.parseInt(data.trim()));
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // ---- ตอนจบเกม ----
        if (ServerConfig.GameStatus_end.equals(status)) {
            if (method.equalsIgnoreCase("ReStartGame")) {
                if (Server.isHost(Hwid)) {
                    Server.GameReStart();
                } else {
                    sendMessage("server:notHost:1");
                }
            }
            return;
        }

        // ---- ก่อนเริ่มเกม ----
        if (method.equalsIgnoreCase("createRoom")) {
            if (!ServerConfig.GameStatus_Nolobby.equals(status)) {
                sendMessage("server:returnJoin:no");
                return;
            }
            try {
                int max = Math.max(3, Math.min(4, Integer.parseInt(data.trim())));
                Server.SetMaxPlayer(max);
            } catch (NumberFormatException e) {
                Server.SetMaxPlayer(3);
            }
            Server.setServerStatus(ServerConfig.GameStatus_lobby);
            Server.setHost(Hwid);
            Server.addPlayer(Hwid);
            sendMessage("server:returnJoin:ok");
            Server.sendAllPlayerData();
            return;
        }

        if (ServerConfig.GameStatus_lobby.equals(status)) {
            if (method.equalsIgnoreCase("joinRoom")) {
                if (Server.countPlayers() >= Server.GetMaxPlayer()) {
                    sendMessage("server:returnJoin:no");
                } else if (Server.addPlayer(Hwid)) {
                    sendMessage("server:returnJoin:ok");
                    Server.sendAllPlayerData();
                } else {
                    sendMessage("server:returnJoin:no");
                }
            } else if (method.equalsIgnoreCase("UpDatePlayerData") || method.equalsIgnoreCase("UpDatePlayerDataHost")) {
                String[] parts = data.split(",", 2);
                if (parts.length == 2) {
                    Server.updatePlayerName(parts[0], parts[1].trim());
                } else {
                    Server.updatePlayerName(data, Hwid);
                }
                Server.sendAllPlayerData();
            } else if (method.equalsIgnoreCase("setTank")) {
                String[] parts = data.split(",", 2);
                try {
                    int tankNum = Integer.parseInt(parts[0].trim());
                    String hwid = parts.length > 1 ? parts[1].trim() : Hwid;
                    Server.updatePlayerTank(tankNum, hwid);
                } catch (NumberFormatException ignored) {}
            } else if (method.equalsIgnoreCase("GameStart")) {
                if (Server.isHost(Hwid)) {
                    if (Server.countPlayers() >= 3) {
                        Server.setServerStatus(ServerConfig.GameStatus_start);
                        Server.sendAll("server:GameStartCreate", null);
                        Server.GameStart();
                    } else {
                        sendMessage("server:needPlayers:" + Server.countPlayers());
                    }
                } else {
                    sendMessage("server:notHost:1");
                }
            } else if (method.equalsIgnoreCase("getHwid")) {
                Server.sendPlayerList();
            }
        }
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }
}
