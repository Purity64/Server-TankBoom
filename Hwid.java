import java.net.NetworkInterface;
import java.util.Enumeration;

public class Hwid {
    public static String getMacHWID() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();

                // กรองเอาเฉพาะการ์ดจอ/การ์ดแลนจริง (ไม่เอา Loopback หรือ Virtual Adapter)
                if (mac != null && !ni.isLoopback() && !ni.isVirtual()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "UNKNOWN_HWID";
    }
}
