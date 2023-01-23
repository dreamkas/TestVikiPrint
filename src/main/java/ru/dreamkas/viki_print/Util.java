package ru.dreamkas.viki_print;

import java.nio.charset.Charset;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import jssc.SerialPort;

public class Util {
    public static final Charset ENCODING = Charset.forName("cp866");
    private static final char STX = 0x02;
    private static final String PASSWORD = "PIRI";
    private static int MIN_PACKET_ID = 0x20;

    public static final char ETX = 0x03;
    public static final char ENQ = 0x05;
    public static final char ACK = 0x06;
    public static final char NAK = 0x15;
    public static final int LF = 0x0A;
    public static final int START_WORK = 0x10;
    public static final int GET_STATUS = 0x00;
    public static final int GET_INFO = 0x02;
    public static final char FS = 0x1C;
    public static final int PRINT_STRING = 0x40;
    public static final int START = 0x30;
    public static final int END = 0x31;
    public static final int X = 0x20;
    static SerialPort port = new SerialPort("COM11");

    @SuppressWarnings("unused")
    public static int getBit(int number, int bitNum) {
        return number & (1 << bitNum);
    }


    public static String toString(byte[] bytes) {
        return toString(bytes, " ", '[', ']');
    }

    public static String toString(int[] values) {
        return toString(values, " ", '[', ']');
    }

    public static String toString(byte[] bytes, String delimiter, Character open, Character close) {
        if (bytes == null) {
            return (open != null ? "[" : "") + "null" + (close != null ? "]" : "");
        }
        if (bytes.length <= 0) {
            return (open != null ? "[" : "") + (close != null ? "]" : "");
        }

        StringBuilder str = new StringBuilder();
        if (open != null) {
            str.append(open);
        }
        for (byte b : bytes) {
            str.append(String.format("0x%02X", b));
            if (delimiter != null) {
                str.append(delimiter);
            }
        }
        String result = str.toString().trim();
        if (close != null) {
            result += close;
        }

        return result;
    }

    public static String toString(int[] values, String delimiter, Character open, Character close) {
        if (values == null || values.length <= 0) {
            return (open != null ? "[" : "") + "null" + (close != null ? "]" : "");
        }
        StringBuilder sb = new StringBuilder();
        if (open != null) {
            sb.append(open);
        }
        for (int i = 0; i < values.length - 1; i++) {
            sb.append(toUnsignedByte(values[i]));
            if (delimiter != null) {
                sb.append(delimiter);
            }
        }
        sb.append(toUnsignedByte(values[values.length - 1]));
        if (close != null) {
            sb.append(close);
        }
        return sb.toString();
    }

    public static String toUnsignedByte(int b) {
        return String.format("0x%02X", b & 0xFF);
    }


    public static void checkCrc(byte[] bytes) throws Exception {
        String s = new String(bytes, ENCODING);
//        if (!s.contains("" +  MIN_PACKET_ID)) throw new Exception("Incorrect callback");
//        if (!s.startsWith("\u0002")) throw new Exception("Callback not starts with " + "\u0002");
        String secondPart = StringUtils.substringAfter(s, "\u0003").substring(0, 2);
        String firstPart = StringUtils.substringBefore(s, "\u0003") + "\u0003" ;
        int sec = Integer.parseInt(secondPart, 16);
        int first = calculateCrc(firstPart);
        System.out.println(sec == first);
        if (sec != first) {
            throw new Exception("Wrong sum");
        }
    }

    public static void makeKKTRequest(int command, String... parameters) {
        byte[] result = null;
        try {
            if (!port.isOpened()) port.openPort();

            String strPacket = STX +
                PASSWORD +
                (char) ++MIN_PACKET_ID +
                formatToHex(command);
            for (String param: parameters) {
                strPacket += param + FS;
            }
            strPacket += ETX;
            result = (strPacket + formatToHex(calculateCrc(strPacket))).getBytes(ENCODING);


            port.writeBytes(result);

            Thread.sleep(200);
            checkCrc(port.readBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String formatToHex(int response) {
        return StringUtils.leftPad(Integer.toHexString(response & 0xFF).toUpperCase(), 2, '0');
    }

    public static int calculateCrc(byte[] rawPacket) {
        int crc = 0;
        for (int i = 1; i < rawPacket.length; i++) {
            crc = (crc ^ rawPacket[i]) & 0xFF;
        }
        return crc;
    }

    public static int calculateCrc(String string) {
        return calculateCrc(string.getBytes(ENCODING));
    }

    // Вызывать, если пришла ошибка и нужно уточнить ее подробности
    public static int getErrorType(byte[] rawPacket) {
        int crc = 0;
        for (int i = 1; i < rawPacket.length; i++) {
            crc = (crc ^ rawPacket[i]) & 0xFF;
        }
        return crc;
    }

    public static byte[] makeRequest(int command, String... parameters) {
        byte[] result = null;
        try {
            if (!port.isOpened()) port.openPort();

            String strPacket = STX +
                PASSWORD +
                (char) ++MIN_PACKET_ID +
                formatToHex(command);
            for (String param: parameters) {
                strPacket += param + FS;
            }
            strPacket += ETX;
            result = (strPacket + formatToHex(calculateCrc(strPacket))).getBytes(ENCODING);


            port.writeBytes(result);

            Thread.sleep(500);
            checkCrc(port.readBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static String maskNonPrintableChars(String value) {
        return maskNonPrintableChars(value, c -> "$" + String.format("%02X", (int) c));
    }

    private static String maskNonPrintableChars(String value, Function<Character, String> mask) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            sb.append(c >= 32 ? c : mask.apply(c));
        }
        String result = StringUtils.replaceChars(sb.toString(), "«»", "\"\"");
        return StringUtils.trimToNull(result) == null ? null : result;
    }
}