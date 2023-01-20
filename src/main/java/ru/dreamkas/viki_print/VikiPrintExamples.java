package ru.dreamkas.viki_print;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import jssc.SerialPort;

@SuppressWarnings("DuplicatedCode")
public class VikiPrintExamples {
    private static final Pattern LOG_PATTERN = Pattern.compile("\\p{Print}");
    private static final Charset ENCODING = Charset.forName("cp866");
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char FS = 0x1C;
    private static final String PASSWORD = "PIRI";
    private static int PACKET_ID = 0x20;

    public static void main(String[] args) throws Exception {
        SerialPort port = new SerialPort("COM11");
        try {
            port.openPort();
            port.purgePort(SerialPort.PURGE_TXCLEAR | SerialPort.PURGE_RXCLEAR);

            Object[] responseData;

            System.out.println("Запрос состояния печатающего устройства");
            responseData = executeCommand(port, 0x04);
            System.out.printf("Статус печатающего устройства: %s%n", responseData[0]);
            System.out.println();

            System.out.println("Чтение даты/времени ККТ");
            responseData = executeCommand(port, 0x13);
            System.out.printf("Дата: %s%n", responseData[0]);
            System.out.printf("Время: %s%n", responseData[1]);
            System.out.println();

            System.out.println("Запрос флагов статуса ККТ");
            responseData = executeCommand(port, 0x00, "3");
            System.out.printf("Статус фатального состояния ККТ: %s%n", responseData[0]);
            System.out.printf("Статус текущих флагов ККТ: %s%n", responseData[1]);
            System.out.printf("Статус документа: %s%n", responseData[2]);
            System.out.println();

            System.out.println("Запрос сведений о ККТ");
            responseData = executeCommand(port, 0x02, 3);
            System.out.printf("ИНН: %s%n", responseData[1]);
            responseData = executeCommand(port, 0x02, 1);
            System.out.printf("Заводской номер ККТ: %s%n", responseData[1]);
            System.out.println();

            System.out.println("Печать сервисного чека");
            executeCommand(port, 0x30, 1, 1);
            executeCommand(port, 0x40, "Текст");
            executeCommand(port, 0x31, 2);
            System.out.println();

            System.out.println("Продажа штучного товара (Обычный режим формирования документа)");
            executeCommand(port, 0x30, 2, 1, "Петров", "", 0, "");
            executeCommand(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
            executeCommand(port, 0x44);
            executeCommand(port, 0x47, 0, 1000);
            responseData = executeCommand(port, 0x31, 2);
            System.out.printf("ФД: %s%n", responseData[3]);
            System.out.printf("ФП: %s%n", responseData[4]);
            System.out.printf("Номер смены: %s%n", responseData[5]);
            System.out.printf("Номер документа в смене: %s%n", responseData[6]);
            System.out.printf("Дата документа: %s%n", responseData[7]);
            System.out.printf("Время документа: %s%n", responseData[8]);
            System.out.println();

            System.out.println("Продажа штучного товара (Пакетный режим формирования документа)");
            executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
            executeCommandPacket(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
            executeCommandPacket(port, 0x44);
            executeCommandPacket(port, 0x47, 0, 1000);
            responseData = executeCommand(port, 0x31, 2);
            System.out.printf("ФД: %s%n", responseData[3]);
            System.out.printf("ФП: %s%n", responseData[4]);
            System.out.printf("Номер смены: %s%n", responseData[5]);
            System.out.printf("Номер документа в смене: %s%n", responseData[6]);
            System.out.printf("Дата документа: %s%n", responseData[7]);
            System.out.printf("Время документа: %s%n", responseData[8]);
            System.out.println();
        } finally {
            port.closePort();
        }
    }

    private static Object[] executeCommand(SerialPort port, int command, Object... parameters) throws Exception {
        return execute(port, command, false, parameters);
    }

    private static void executeCommandPacket(SerialPort port, int command, Object... parameters) throws Exception {
        execute(port, command, true, parameters);
    }

    private static Object[] execute(SerialPort port, int command, boolean packetMode, Object... parameters) throws Exception {
        Object[] result = new Object[0];

        byte[] request = makeRequest(command, parameters);
        System.out.printf("==> %s%n", toString(request));
        port.writeBytes(request);
        if (packetMode) {
            return result;
        }

        int responsePacketId = 0;
        while (responsePacketId < PACKET_ID - 1) {
            while (port.getInputBufferBytesCount() <= 0) {
                Thread.yield();
            }
            byte[] response = port.readBytes();
            System.out.printf("<== %s%n", toString(response));
            checkCrc(response);

            String data = new String(response, ENCODING);

            int errorCode = Integer.parseInt(data.substring(4, 6), 16);
            if (errorCode != 0) {
                if (errorCode == 0x01 || errorCode == 0x03) {
                    throw new Exception((String) executeCommand(port, 0x06, 1)[2]);
                }
                throw new Exception(String.format("Ошибка 0x%s", toHexString(errorCode)));
            }

            responsePacketId = data.charAt(1);

            result = Arrays.stream(data.substring(6).split("" + FS)).toArray();
        }
        return result;
    }

    private static byte[] makeRequest(int command, Object... parameters) {
        StringBuilder strPacket = new StringBuilder()
            .append(STX)
            .append(PASSWORD)
            .append((char) PACKET_ID++)
            .append(toHexString(command));
        if (parameters.length == 0) {
            strPacket.append(FS);
        }
        for (Object param : parameters) {
            strPacket.append(param).append(FS);
        }
        strPacket.append(ETX);
        int crc = calculateCrc(strPacket.toString());
        strPacket.append(toHexString(crc));

        return strPacket
            .toString()
            .getBytes(ENCODING);
    }

    private static int calculateCrc(String string) {
        byte[] rawPacket = string.getBytes(ENCODING);
        int crc = 0;
        for (int i = 1; i < rawPacket.length; i++) {
            crc = (crc ^ rawPacket[i]) & 0xFF;
        }
        return crc;
    }

    private static void checkCrc(byte[] bytes) throws Exception {
        String s = new String(bytes, ENCODING);
        String dataPart = s.substring(0, s.indexOf(ETX) + 1);
        String crcPart = s.substring(s.indexOf(ETX) + 1);
        int crc = Integer.parseInt(crcPart, 16);
        if (crc != calculateCrc(dataPart)) {
            throw new Exception("Wrong CRC");
        }
    }

    private static String toString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (char c : new String(bytes, ENCODING).toCharArray()) {
            if (c > 255 || LOG_PATTERN.matcher(String.valueOf(c)).matches()) {
                sb.append(c);
            } else {
                sb.append('{').append(toHexString(c)).append('}');
            }
        }
        return sb.toString();
    }

    private static String toHexString(int value) {
        return StringUtils.leftPad(Integer.toHexString(value & 0xFF).toUpperCase(), 2, '0');
    }
}