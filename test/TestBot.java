import java.io.*;
import java.net.*;
import java.util.*;

/**
 * บอททดสอบ server — จำลองผู้เล่น 3 คน (host + 2 joiner)
 * ทดสอบ flow: createRoom, joinRoom, setTank, GameStart, countdown,
 * spownitem, myhp, playerkill, killmsg, score, game_end, ReStartGame
 */
public class TestBot {
    static class Conn {
        Socket socket;
        BufferedReader in;
        PrintWriter out;
        String hwid;
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        volatile boolean running = true;

        Conn(String hwid) throws Exception {
            this.hwid = hwid;
            socket = new Socket("127.0.0.1", 50101);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(hwid); // ส่ง hwid บรรทัดแรก
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        received.add(line);
                        System.out.println("  [" + hwid + "] <-- " + line);
                    }
                } catch (IOException ignored) {}
            });
            reader.setDaemon(true);
            reader.start();
        }

        void send(String msg) {
            System.out.println("  [" + hwid + "] --> " + msg);
            out.println(msg);
        }

        boolean has(String needle) {
            synchronized (received) {
                for (String m : received) {
                    if (m.contains(needle)) return true;
                }
            }
            return false;
        }

        void waitUntil(String needle, int timeoutMs) throws Exception {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                if (has(needle)) return;
                Thread.sleep(50);
            }
            throw new AssertionError("TIMEOUT waiting for: " + needle);
        }
    }

    static int passed = 0, failed = 0;

    static void check(boolean cond, String name) {
        if (cond) { passed++; System.out.println("[PASS] " + name); }
        else { failed++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) throws Exception {
        Conn host = new Conn("HWID_HOST");
        Conn b2 = new Conn("HWID_B2");
        Conn b3 = new Conn("HWID_B3");

        // ---- สร้างห้อง ----
        host.send("server:createRoom:4");
        Thread.sleep(300);
        host.send("server:UpDatePlayerDataHost:Host," + host.hwid);
        Thread.sleep(300);
        check(host.has("returnJoin:ok"), "host ได้ returnJoin:ok");
        check(host.has("playerAlldata:HWID_HOST;Host;1"), "host ได้รับ playerAlldata (ตัวเองอยู่ในห้อง)");

        // ---- คนอื่นเข้าห้อง ----
        b2.send("server:joinRoom:" + b2.hwid);
        b3.send("server:joinRoom:" + b3.hwid);
        Thread.sleep(300);
        check(b2.has("returnJoin:ok"), "b2 join สำเร็จ");
        check(b3.has("returnJoin:ok"), "b3 join สำเร็จ");
        check(host.has("HWID_B2;;1,"), "host เห็น b2 ในห้อง");
        check(host.has("HWID_B3;;1"), "host เห็น b3 ในห้อง");

        // ---- เปลี่ยนชื่อ + เลือก tank ----
        b2.send("server:UpDatePlayerData:BotTwo," + b2.hwid);
        b3.send("server:UpDatePlayerData:BotThree," + b3.hwid);
        b2.send("server:setTank:2," + b2.hwid);
        Thread.sleep(300);
        check(host.has("BotTwo"), "ชื่อ BotTwo ไปถึง host");

        // ---- เริ่มเกม ----
        host.send("server:GameStart:1");
        Thread.sleep(300);
        check(b2.has("GameStartCreate"), "b2 ได้รับ GameStartCreate");
        check(b2.has("countdown:3"), "b2 ได้รับ countdown:3");
        check(host.has("HWID_B2;BotTwo;2"), "มี player list พร้อมชื่อ/tank");

        // ---- รอไอเทม spawn (ควรมาหลัง countdown ~4 วิ) ----
        host.waitUntil("spownitem:", 8000);
        check(true, "ได้รับ spownitem");

        // ---- รายงาน HP ----
        host.send("server:myhp:80");
        b2.send("server:myhp:60");
        b3.send("server:myhp:100");
        Thread.sleep(200);

        // ---- ยิงกัน: b3 ฆ่า b2 แล้ว host ฆ่า b3 ----
        b3.send("server:playerkill:" + b2.hwid + "," + b3.hwid);
        Thread.sleep(300);
        check(host.has("killed:" + b2.hwid), "broadcast killed (b2)");
        check(host.has("killmsg:" + b3.hwid + ";" + b2.hwid), "broadcast killmsg");

        // ฆ่าซ้ำ (dedupe test) — ไม่ควรทำให้เกมจบผิด
        b3.send("server:playerkill:" + b2.hwid + "," + b3.hwid);
        Thread.sleep(200);

        // ตอนนี้เหลือ 2 คน — host ฆ่า b3 → เกมจบ
        host.send("server:playerkill:" + b3.hwid + "," + host.hwid);
        Thread.sleep(400);
        check(host.has("score:"), "ได้รับ score");
        check(host.has("game_end:HWID_HOST"), "game_end ประกาศ host เป็นผู้ชนะ");
        check(b2.has("game_end:HWID_HOST"), "b2 ได้รับ game_end ด้วย");

        // ---- restart (host เท่านั้น) ----
        b2.send("server:ReStartGame:1");
        Thread.sleep(200);
        check(!hasAny(b2, "game_restart"), "b2 (ไม่ใช่ host) restart ไม่ได้");
        host.send("server:ReStartGame:1");
        Thread.sleep(400);
        check(b2.has("game_restart"), "host restart ได้");
        check(countOccurrences(b2, "countdown:3") >= 2, "เริ่มรอบใหม่ + countdown ใหม่");

        // ---- จำลอง disconnect ระหว่างเล่น → ควรถือว่าตาย ----
        b3.running = false;
        b3.socket.close();
        Thread.sleep(500);
        check(host.has("killed:" + b3.hwid), "disconnect ระหว่างเล่น = ตาย (killed broadcast)");

        // ---- LeaveGame (host ออก → ปิดห้อง) ----
        host.send("server:LeaveGame:" + host.hwid);
        Thread.sleep(300);
        check(b2.has("host_left"), "host ออก → broadcast host_left");

        System.out.println("\n==============================");
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.out.println("==============================");
        System.exit(failed == 0 ? 0 : 1);
    }

    static boolean hasAny(Conn c, String needle) {
        return c.has(needle);
    }

    static int countOccurrences(Conn c, String needle) {
        int n = 0;
        synchronized (c.received) {
            for (String m : c.received) {
                if (m.contains(needle)) n++;
            }
        }
        return n;
    }
}
