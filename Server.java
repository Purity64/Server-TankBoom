import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class Server extends Thread {

    /** ข้อมูลผู้เล่น 1 คน (ใช้ List แทน array parallel เพื่อลด race) */
    public static class Player {
        public String hwid;
        public String name = "";
        public int tank = 1;
        public boolean alive = true;
        public int hp = 100;
        public int kills = 0;

        Player(String hwid) { this.hwid = hwid; }
    }

    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final List<Player> players = Collections.synchronizedList(new ArrayList<>());

    private static String hostHwid = null;
    private static int MaxPlayer = 3;
    private static final Random random = new Random();
    private static String gamestatus = ServerConfig.GameStatus_Nolobby;

    // Timers ของรอบเกม (ต้อง cancel ทุกครั้งที่จบเกม ไม่งั้นจะซ้อนกันเรื่อยๆ)
    private static volatile Timer itemSpawnTimer = null;
    private static volatile Timer matchTimer = null;

    // ค่าคงที่ของรอบเกม
    public static final int ITEM_SPAWN_INTERVAL_MS = 10000;
    public static final int MATCH_TIME_MS = 180000; // 3 นาที
    public static final int COUNTDOWN_SECONDS = 3;

    private static final int MAP_W = 1000;
    private static final int MAP_H = 700;

    public static void main(String[] args) {
        new Server().run();
    }

    @Override
    public void run() {
        try (ServerSocket servSocket = new ServerSocket(50101)) {
            System.out.println("Server started on port 50101...");

            while (true) {
                try {
                    Socket socket = servSocket.accept();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty() && !isDuplicateHwid(line.trim())) {
                        ClientHandler handler = new ClientHandler(socket,
                                socket.getInetAddress().getHostAddress(), line.trim(), reader);
                        System.out.println("[CONNECT] " + line.trim());
                        clients.add(handler);
                        handler.start();
                    } else {
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // ส่งข้อความ
    // ------------------------------------------------------------------
    public static void sendAll(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendMessage(message);
            }
        }
        System.out.println("[SEND] " + message);
    }

    public static void sendTo(String hwid, String message) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.getHwid().equalsIgnoreCase(hwid)) {
                    c.sendMessage(message);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // ผู้เล่น
    // ------------------------------------------------------------------
    public static Player findPlayer(String hwid) {
        synchronized (players) {
            for (Player p : players) {
                if (p.hwid.equalsIgnoreCase(hwid)) return p;
            }
        }
        return null;
    }

    private static boolean isDuplicateHwid(String hwid) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.getHwid().equalsIgnoreCase(hwid)) return true;
            }
        }
        return false;
    }

    public static boolean addPlayer(String hwid) {
        if (players.size() >= MaxPlayer) return false;
        synchronized (players) {
            for (Player p : players) {
                if (p.hwid.equalsIgnoreCase(hwid)) return false; // มีอยู่แล้ว
            }
            players.add(new Player(hwid));
        }
        return true;
    }

    public static void removePlayer(String hwid) {
        synchronized (players) {
            for (int i = players.size() - 1; i >= 0; i--) {
                if (players.get(i).hwid.equalsIgnoreCase(hwid)) {
                    players.remove(i);
                }
            }
        }
    }

    public static int countPlayers() {
        return players.size();
    }

    public static int countAlive() {
        int n = 0;
        synchronized (players) {
            for (Player p : players) {
                if (p.alive) n++;
            }
        }
        return n;
    }

    public static String getHostHwid() {
        return hostHwid;
    }

    public static void setHost(String hwid) {
        hostHwid = hwid;
    }

    public static boolean isHost(String hwid) {
        return hwid != null && hwid.equalsIgnoreCase(hostHwid);
    }

    /** ตัดอักขระที่ทำให้ protocol พังออกจากชื่อผู้เล่น */
    public static String sanitizeName(String name) {
        if (name == null) return "";
        return name.replace(":", " ").replace(";", " ").replace(",", " ").trim();
    }

    public static List<ClientHandler> getAllClients() {
        return clients;
    }

    public static void SetMaxPlayer(int max) {
        MaxPlayer = max;
    }

    public static int GetMaxPlayer() {
        return MaxPlayer;
    }

    // ------------------------------------------------------------------
    // สถานะเกม
    // ------------------------------------------------------------------
    public static String getServerStatus() {
        return gamestatus;
    }

    public static void setServerStatus(String status) {
        gamestatus = status;
        System.out.println("[STATUS] " + status);
    }

    // ------------------------------------------------------------------
    // ไอเทม
    // ------------------------------------------------------------------
    public static String randomType() {
        int r = random.nextInt(3) + 1;
        switch (r) {
            case 1: return "boom_bullet";
            case 2: return "hp";
            default: return "shield";
        }
    }

    private static void startItemSpawnTimer(long initialDelay) {
        stopItemSpawnTimer();
        itemSpawnTimer = new Timer("ItemSpawn", true);
        itemSpawnTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (!ServerConfig.GameStatus_start.equals(gamestatus)) {
                    stopItemSpawnTimer();
                    return;
                }
                int x = random.nextInt(Math.max(1, MAP_W - 60)) + 20;
                int y = random.nextInt(Math.max(1, MAP_H - 60)) + 20;
                sendAll("server:spownitem:" + x + "," + y + "," + randomType(), null);
            }
        }, initialDelay, ITEM_SPAWN_INTERVAL_MS);
    }

    private static void stopItemSpawnTimer() {
        Timer t = itemSpawnTimer;
        itemSpawnTimer = null;
        if (t != null) t.cancel();
    }

    // ------------------------------------------------------------------
    // เริ่มเกม / จบเกม
    // ------------------------------------------------------------------
    public static void GameStart() {
        if (!ServerConfig.GameStatus_start.equals(gamestatus)) return;

        stopItemSpawnTimer();
        stopMatchTimer();

        // ส่ง countdown ให้ client นับ 3-2-1 ก่อนปลดล็อคการเคลื่อนที่
        sendAll("server:countdown:" + COUNTDOWN_SECONDS, null);

        // ไอเทมเริ่ม spawn หลัง countdown จบ แล้วทุก 10 วิ
        itemSpawnTimer = new Timer("ItemSpawn", true);
        itemSpawnTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (!ServerConfig.GameStatus_start.equals(gamestatus)) {
                    stopItemSpawnTimer();
                    return;
                }
                int x = random.nextInt(Math.max(1, MAP_W - 60)) + 20;
                int y = random.nextInt(Math.max(1, MAP_H - 60)) + 20;
                sendAll("server:spownitem:" + x + "," + y + "," + randomType(), null);
            }
        }, (COUNTDOWN_SECONDS + 1) * 1000L, ITEM_SPAWN_INTERVAL_MS);

        // จับเวลาแมตช์ — หมดเวลาตัดสินด้วย HP
        matchTimer = new Timer("MatchTimer", true);
        matchTimer.schedule(new TimerTask() {
            public void run() {
                if (ServerConfig.GameStatus_start.equals(gamestatus)) {
                    timeUp();
                }
            }
        }, MATCH_TIME_MS);

        sendPlayerList();
    }

    private static void stopMatchTimer() {
        Timer t = matchTimer;
        matchTimer = null;
        if (t != null) t.cancel();
    }

    /** หมดเวลาแมตช์ — ผู้ชนะคือคนที่มี HP สูงสุดที่ยังมีชีวิต */
    private static void timeUp() {
        Player best = null;
        synchronized (players) {
            for (Player p : players) {
                if (!p.alive) continue;
                if (best == null || p.hp > best.hp) best = p;
            }
        }
        if (countAlive() <= 1 && best != null) {
            endGame(best);
        } else if (best != null) {
            endGame(best);
        } else {
            endGame(null);
        }
    }

    /** ตายเพราะถูกฆ่า (มี killer) — นับ kill ให้คนยิง */
    public static void onPlayerKilled(String victimHwid, String killerHwid) {
        Player victim = findPlayer(victimHwid);
        if (victim == null || !victim.alive) return; // dedupe: ตายซ้ำไม่นับ

        victim.alive = false;
        victim.hp = 0;

        Player killer = (killerHwid == null || killerHwid.isEmpty()) ? null : findPlayer(killerHwid);
        if (killer != null && killer != victim && killer.alive) {
            killer.kills++;
        }

        sendAll("server:killed:" + victimHwid, null);
        sendAll("server:killmsg:" + (killer == null ? "" : killerHwid) + ";" + victimHwid, null);

        checkGameEnd();
    }

    /** ตายเพราะ disconnect ระหว่างเกม */
    public static void onPlayerDisconnected(String hwid) {
        Player p = findPlayer(hwid);
        if (p != null && p.alive
                && (ServerConfig.GameStatus_start.equals(gamestatus)
                    || ServerConfig.GameStatus_lobby.equals(gamestatus))) {
            if (ServerConfig.GameStatus_start.equals(gamestatus)) {
                onPlayerKilled(hwid, null);
                return;
            }
            // อยู่ห้อง lobby — เอาออกจากห้อง
            removePlayer(hwid);
            sendAllPlayerData();
        }
        removePlayer(hwid);
    }

    private static void checkGameEnd() {
        int alive = countAlive();
        if (alive <= 1) {
            Player winner = null;
            synchronized (players) {
                for (Player p : players) {
                    if (p.alive) { winner = p; break; }
                }
            }
            endGame(winner);
        }
    }

    /** จบเกม: ส่ง score + ผู้ชนะ แล้วหยุด timer ทั้งหมด */
    public static void endGame(Player winner) {
        if (!ServerConfig.GameStatus_start.equals(gamestatus)) return;
        setServerStatus(ServerConfig.GameStatus_end);

        stopItemSpawnTimer();
        stopMatchTimer();

        // ส่งตารางคะแนน: hwid;name;kills,...
        StringBuilder sb = new StringBuilder();
        synchronized (players) {
            for (Player p : players) {
                if (sb.length() > 0) sb.append(",");
                sb.append(p.hwid).append(";").append(p.name).append(";").append(p.kills);
            }
        }
        sendAll("server:score:" + sb, null);
        sendAll("server:game_end:" + (winner == null ? "draw" : winner.hwid), null);
    }

    /** เริ่มรอบใหม่ (host กด restart) */
    public static void GameReStart() {
        if (!ServerConfig.GameStatus_end.equals(gamestatus)) return;

        synchronized (players) {
            for (Player p : players) {
                p.alive = true;
                p.hp = 100;
                p.kills = 0;
            }
        }
        setServerStatus(ServerConfig.GameStatus_start);
        sendAll("server:game_restart:1", null);
        GameStart();
    }

    // ------------------------------------------------------------------
    // ส่งรายชื่อผู้เล่น
    // ------------------------------------------------------------------
    /** server:player:<hwid>;<name>;<tank>,... */
    public static void sendPlayerList() {
        sendAll("server:player:" + buildPlayerData(), null);
    }

    /** server:playerAlldata:<hwid>;<name>;<tank>,... */
    public static void sendAllPlayerData() {
        sendAll("server:playerAlldata:" + buildPlayerData(), null);
    }

    private static String buildPlayerData() {
        StringBuilder sb = new StringBuilder();
        synchronized (players) {
            for (Player p : players) {
                if (sb.length() > 0) sb.append(",");
                sb.append(p.hwid).append(";").append(p.name).append(";").append(p.tank);
            }
        }
        return sb.toString();
    }

    public static void updatePlayerName(String name, String hwid) {
        Player p = findPlayer(hwid);
        if (p != null) p.name = sanitizeName(name);
    }

    public static void updatePlayerTank(int tankNum, String hwid) {
        Player p = findPlayer(hwid);
        if (p != null) p.tank = tankNum;
        sendAllPlayerData();
    }

    public static void updatePlayerHp(String hwid, int hp) {
        Player p = findPlayer(hwid);
        if (p != null && p.alive && hp > p.hp) {
            // รับค่าสูงกว่าเดิมได้ (heal) แต่ไม่ให้เกิน 100
            p.hp = Math.min(hp, 100);
        } else if (p != null && p.alive) {
            p.hp = hp;
        }
    }

    // ------------------------------------------------------------------
    // ออกจากห้อง (กดปุ่ม Leave)
    // ------------------------------------------------------------------
    public static void leaveGame(String hwid) {
        boolean wasHost = isHost(hwid);
        removePlayer(hwid);
        if (wasHost) {
            // ถ้า host ออก ให้ปิดห้อง (กลับสู่ nolobby) และพาทุกคนกลับเมนู
            setServerStatus(ServerConfig.GameStatus_Nolobby);
            hostHwid = null;
            stopItemSpawnTimer();
            stopMatchTimer();
            synchronized (players) {
                players.clear();
            }
            sendAll("server:host_left:1", null);
            System.out.println("[HOST LEFT] ห้องถูกปิด");
            return;
        }
        sendAllPlayerData();
    }
}
