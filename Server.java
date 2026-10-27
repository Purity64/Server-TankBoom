import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class Server extends Thread {
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final HashMap<String , Boolean> playHashMap = new HashMap<>(); 
    private static String[] playerHwid = {null,null,null,null};
    private static String[] playerNames = {null, null, null, null};
    private static int[] playerTanks = {1, 1, 1, 1};
    private static int lastIndex = 0;
    private static int MaxPlayer ;
    private static final Random random = new Random();
    private  static  String gamestatus = ServerConfig.GameStatus_Nolobby;
    
    public Server() {
        start();
 
    }

    public static void main(String[] args) {
        new Server();
    }

    @Override
    public void run() {
        try (ServerSocket servSocket = new ServerSocket(50101)) {
            System.out.println("Server started on port 50101...");
            
            while (true) {
                try {
                    Socket socket = servSocket.accept();

                    InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                    BufferedReader bufferIn = new BufferedReader(isr);


                    String line = bufferIn.readLine();
                    if (line != null && !isCheckHwid(line)) {
                        ClientHandler handler = new ClientHandler(socket, socket.getInetAddress().getHostAddress(), line);
                        System.out.println(line);

                        clients.add(handler);
                        handler.start();
                    } else {
                        socket.close();
                    }

                    //printhwid();
                    //getHwid();
                    //getAllPlayerData();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendAll(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (sender != null && c == sender) {
                    continue;
                }
                c.sendMessage(message);
            }
        }
    }

    public static void CheckPlayer(String hwid){
        playHashMap.put(hwid, false);
        System.out.println(hwid);
        if (PlayerStatus() == 1) {
            GameEnd();
        }
    }

    public static int PlayerStatus(){
        int i = 0;
        for (String a : playHashMap.keySet()) {
            if (playHashMap.get(a)) {
               i++; 
            }
        }

        return i;
    }



    public static boolean isCheckHwid(String hwid) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.getHwid().equalsIgnoreCase(hwid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        playHashMap.remove(clientHandler.getHwid());
    }

    public static String randomType(){
        int randomNumber = random.nextInt(3) + 1;
        String type = "";
        switch (randomNumber) {
            case 1: type = "boom_bullet";break;
            case 2: type = "hp";break;
            case 3: type = "shield";break;
            default: type = "boom_bullet";break;
        }
        return type;
    }

    public static void printhwid() {
        System.out.println("--- Online Players ---");
        synchronized (clients) {
            for (String c : playHashMap.keySet()) {
                System.out.println(playHashMap.get(c));
            }
        }
    }

    public static void GameStart(){
        if (gamestatus.equalsIgnoreCase(ServerConfig.GameStatus_start)) {
            String msg = "server:game_start:1";
            sendAll(msg, null);
            System.out.println(msg);
            getHwid();
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    int mainSizeW = 1000;
                    int mainSizeH = 700;
                    int x = random.nextInt(mainSizeW - 50);
                    int y = random.nextInt(mainSizeH - 50);
                    String type = randomType();
                    String msg = "server:spownitem:"+x+","+y+","+type;
                    sendAll(msg, null);
                }
            }, 0, 10000);


        }

    }

    
    public static void SetMaxPlayer(int max){
        MaxPlayer = max;
    }
    public static int GetMaxPlayer(){
        return  MaxPlayer;
    }
    public static int GetCounPlayer(){
        int i = 0;
        for(String a : playerHwid){
            if (a == null) {
                continue;
            }
            i++;
        }

        return  i;
    }

    public static HashMap<String , Boolean> getplayHashMap(){
        return playHashMap;
    }

    public static String[] getplayerHwid(){
        return playerHwid;
    }

    public static int getLastIndex() {
        return lastIndex;
    }

    public static void setLastIndex(int lastIndex) {
        Server.lastIndex = lastIndex;
    }


    public static void GameEnd(){
        String msg = "server:game_end:1";
        sendAll(msg, null);
        gamestatus = ServerConfig.GameStatus_end;
        System.out.println(msg);
    }
    public static String getServerStatus(){
        return gamestatus;
    }
    public static void setServerStatus(String ststus){
        gamestatus = ststus;
    }
    public static void GameReStart(){
        for(String a : playHashMap.keySet()){
            playHashMap.put(a, true);
        }
        gamestatus= ServerConfig.GameStatus_start;
        sendAll("server:game_restart:1", null);
        getHwid();
    }
    public static void  getHwid(){
        String tempArr = "";

        for (String c : playHashMap.keySet()) {
            if (tempArr.length() == 0) {
                tempArr += c;
            }else{
                tempArr += (","+c);
            }
        }
        
        synchronized (clients) {
            String msg = "server:player:"+tempArr;
            for (ClientHandler c : clients) {
                c.sendMessage(msg);
            }
        }
    }

    public static void UpDatePlayerName(String name , String hwid){
        int i = 0;
        for(String player : playerHwid){
            if (player.equalsIgnoreCase(hwid)) {
                break;
            }
            i++;
        }
        playerNames[i] = name;
        
    }

    public static void UpDatePlayerTank(int TankNum , String hwid){
        int i = 0;
        for(String player : playerHwid){
            if (player.equalsIgnoreCase(hwid)) {
                break;
            }
            i++;
        }
        playerTanks[i] = TankNum;
        System.out.println(playerNames[i]+":"+TankNum);
        getAllPlayerData();
    }

    public static void  getAllPlayerData(){
        String tempArr = "";
        int i = 0;
        for (String c : playerHwid) {
            if (c == null) {
                continue;
            }
            if (tempArr.length() == 0) {
                tempArr += c;
            }else{
                tempArr += (","+c);
            }
            tempArr += ";"+playerNames[i]+";"+playerTanks[i];
            i++;
        }
        
        
        synchronized (clients) {
            String msg = "server:playerAlldata:"+tempArr;
            System.out.println(msg);
            for (ClientHandler c : clients) {
                c.sendMessage(msg);
            }
        }
    }
}