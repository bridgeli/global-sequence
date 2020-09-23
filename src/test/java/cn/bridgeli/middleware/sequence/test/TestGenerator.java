package cn.bridgeli.middleware.sequence.test;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGenerator.class);

    private static final Random random = new Random();
    private static final String TIME_PATTERN = "yyyyMMddHHmmssSSS";
    private static volatile String ipHexStr;
    private static volatile String pidHexStr;
    private static int sequence = 0;
    private static long lastTimestamp = -1L;

    /**
     * yyyyMMddHHmmss时间+随机数
     */
    public static String nextSeq() {

        long currentTimestamp = System.currentTimeMillis();
        // 17 + 8 + 4 + 8
        return getTimeStr(currentTimestamp) + getIp36Str() + getPid36Str() + getSequence(currentTimestamp);
        //12+2+8+4 + 2    28位字符串
    }

    // 8个字符
    private static synchronized String getSequence(long currentTimestamp) {
        if (currentTimestamp < lastTimestamp) {
            LOGGER.error("Clock moved backwards.");
            return StringUtils.leftPad(String.valueOf(random.nextInt(10000000)), 8, '0');
        }
        if (lastTimestamp == currentTimestamp) {
            sequence++;
            if (sequence == Integer.MAX_VALUE) {
                currentTimestamp = tilNextMillis(lastTimestamp);
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = currentTimestamp;
        return StringUtils.leftPad(Integer.toHexString(sequence), 8, '0');
    }

    private static long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    // 17位字符串
    private static String getTimeStr(long currentTimestamp) {
        return new SimpleDateFormat(TIME_PATTERN).format(new Date(currentTimestamp));
    }

    // 8位字符串
    private static String getIp36Str() {
        if (ipHexStr == null) {
            InetAddress localAddress = NetUtils.getLocalAddress();
            if (localAddress == null || localAddress.getAddress() == null) {
                ipHexStr = StringUtils.leftPad(String.valueOf(random.nextInt(10000000)), 8, '0');
            }
            ipHexStr = Integer.toHexString(bytesToInt(localAddress.getAddress()));
        }
        return ipHexStr;
    }

    private static int bytesToInt(byte[] bytes) {
        int addr = bytes[3] & 0xFF;
        addr |= ((bytes[2] << 8) & 0xFF00);
        addr |= ((bytes[1] << 16) & 0xFF0000);
        addr |= ((bytes[0] << 24) & 0xFF000000);
        return addr;
    }

    // 4位字符串
    private static String getPid36Str() {
        if (pidHexStr == null) {
            try {
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                String name = runtime.getName(); // format: "pid@hostname"
                pidHexStr = Integer.toHexString(Integer.parseInt(name.substring(0, name.indexOf('@'))));
            } catch (Throwable e) {
                pidHexStr = StringUtils.leftPad(String.valueOf(random.nextInt(1000)), 4, '0');
            }
        }
        return pidHexStr;
    }

    public static void main(String[] args) {
        System.out.println(Integer.toString(65535, 36));
        InetAddress localAddress = NetUtils.getLocalAddress();
        System.out.println(Integer.toString(bytesToInt(localAddress.getAddress()), 36));
        System.out.println(Long.MAX_VALUE);
        System.out.println(Long.toString(System.currentTimeMillis(), 36));
    }
}
