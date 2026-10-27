import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.awt.*;

public class ClientHandler extends Thread{
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientIP;
    private String Hwid;

    public ClientHandler(Socket socket, String clientIP , String Hwid) {
        this.socket = socket;
        this.clientIP = clientIP;
        this.Hwid = Hwid;
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
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("[" + clientIP + "]: " + message);

                String[] row = message.split(":");
                String type = row[0];
                String method = row[1];
                String data = row.length > 2 ? row[2] : "";
                if (type.equalsIgnoreCase("server")) {
                    System.out.println(Server.getServerStatus());
                    if (method.equalsIgnoreCase("getHwid")) {
                        Server.getHwid();
                    }else if (method.equalsIgnoreCase("joinRoom")) {
                            System.out.println(Server.GetMaxPlayer());
                            if (Server.GetCounPlayer() >= Server.GetMaxPlayer()  || Server.GetMaxPlayer() < 3) {
                                sendMessage("server:returnJoin:no");
                            }else{
                                int lastIndex = Server.getLastIndex();
                                sendMessage("server:returnJoin:ok");
                                Server.getplayHashMap().put(Hwid, true);
                                Server.getplayerHwid()[lastIndex] = Hwid;
                                lastIndex++;
                                Server.setLastIndex(lastIndex);
                                Server.getAllPlayerData();
                            }
                            
                    }
                    if (Server.getServerStatus().equalsIgnoreCase(ServerConfig.GameStatus_Nolobby)) {
                        if (method.equalsIgnoreCase("createRoom")) {
                            Server.setServerStatus(ServerConfig.GameStatus_lobby);
                            Server.SetMaxPlayer(Integer.parseInt(data));
                            int lastIndex = Server.getLastIndex();
                            Server.getplayHashMap().put(Hwid, true);
                            Server.getplayerHwid()[lastIndex] = Hwid;
                            lastIndex++;
                            Server.setLastIndex(lastIndex);
                            Server.setServerStatus(ServerConfig.GameStatus_lobby);
                            System.out.println(Server.getServerStatus());
                            //Server.getAllPlayerData();
                        }
                        
                    }else if(Server.getServerStatus().equalsIgnoreCase(ServerConfig.GameStatus_lobby)){
                        if (method.equalsIgnoreCase("UpDatePlayerData")) {
                            String[] rows = data.split(",");
                            Server.UpDatePlayerName(rows[0] , rows[1]);
                            Server.getAllPlayerData();
                        }else if(method.equalsIgnoreCase("UpDatePlayerDataHost")){
                            String[] rows = data.split(",");
                            Server.UpDatePlayerName(rows[0] , rows[1]);
                        }else if(method.equalsIgnoreCase("setTank")){
                            String[] rows = data.split(",");
                            Server.UpDatePlayerTank(Integer.parseInt(rows[0]) , rows[1]);
                        }else if (method.equalsIgnoreCase("GameStart")) {
                            Server.sendAll("server:GameStartCreate", null);
                            Server.setServerStatus(ServerConfig.GameStatus_start);
                            Server.GameStart();
                        }else if(method.equalsIgnoreCase("LeaveGame")){
                            Server.LeaveGame(data);
                        }
                    }else if(Server.getServerStatus().equalsIgnoreCase(ServerConfig.GameStatus_start)){
                        if (method.equalsIgnoreCase("playerkill")) {
                            Server.CheckPlayer(data);
                        }

                    }else if(Server.getServerStatus().equalsIgnoreCase(ServerConfig.GameStatus_end)){
                        if (method.equalsIgnoreCase("ReStartGame")) {
                            Server.GameReStart();
                        }
                    }

                }else{
                    Server.sendAll(message, this);
                }
                
            }
        } catch (IOException e) {
        } finally {
            Server.removeClient(this);
            try { 
                socket.close(); 
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }
}
