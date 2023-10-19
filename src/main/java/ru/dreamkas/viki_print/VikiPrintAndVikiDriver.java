package ru.dreamkas.viki_print;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class VikiPrintAndVikiDriver {
    public static final Integer TCP_PORT = 50003;
    public static final String LOCAL_IP = "127.0.0.1";
    private static final Pattern LOG_PATTERN = Pattern.compile("\\p{Print}");
    private static final Charset ENCODING = Charset.forName("cp866");
    private static final byte ENQ = 0x05;
    private static final byte ACK = 0x06;
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char FS = 0x1C;
    private static final String PASSWORD = "PIRI";
    private static int PACKET_ID = 0x20;

    public static void main(String[] args) throws Exception {
        Socket vikiDriverSocket = new Socket();
        vikiDriverSocket.connect(new InetSocketAddress(LOCAL_IP, TCP_PORT), 3000);
        InputStream in = vikiDriverSocket.getInputStream();
        OutputStream out = vikiDriverSocket.getOutputStream();

        System.out.println("Проверка связи с ККТ");
        checkConnection(out, in);

//        Object[] responseData;
//
//        System.out.println("Запрос состояния печатающего устройства");
//        responseData = executeCommand(out, in, 0x04);
//        System.out.printf("Статус печатающего устройства: %s%n", responseData[0]);
//        System.out.println();
    }

    public static void checkConnection(OutputStream out, InputStream in) throws Exception {
        out.write(ENQ);
        System.out.printf("==> %s%n", ENQ);
        int i = 0;
        byte[] bytes = new byte[0];
        while ((i = in.available()) > 0) {
            bytes = new byte[i];
            in.read(bytes);
        }
        System.out.printf("<~~ %s%n", toString(bytes));
        for (byte[] response : splitPackets(bytes)) {
            System.out.printf("<== %s%n", toString(response));
            if (response[0] != ACK) {
                parseResponse(response);
            }
        }
    }

    //Синхронная работа
    public static Object[] executeCommand(OutputStream out, InputStream in, int command, Object... parameters) throws Exception {
        return execute(out, in, command, false, parameters);
    }

    //Пакетная работа
    public static void executeCommandPacket(OutputStream out, InputStream in, int command, Object... parameters) throws Exception {
        execute(out, in, command, true, parameters);
    }

    //Отправка пакета в порт принтера, получение и разбор ответа
    private static Object[] execute(OutputStream out, InputStream in, int command, boolean packetMode, Object... parameters) throws Exception {
        Object[] result = new Object[0];

        byte[] request = makeRequest(command, parameters);
        System.out.printf("==> %s%n", toString(request));
        out.write(request);
        if (packetMode) {
            return result;
        }

        int responsePacketId = 0;
        while (responsePacketId < PACKET_ID - 1) {
            int i = 0;
            byte[] bytes = new byte[0];
            while ((i = in.available()) > 0) {
                bytes = new byte[i];
                in.read(bytes);
            }
            System.out.printf("<~~ %s%n", toString(bytes));
            for (byte[] response : splitPackets(bytes)) {
                System.out.printf("<== %s%n", toString(response));
                Object[] responseData = parseResponse(response);
                responsePacketId = (int) responseData[0];
                int errorCode = (int) responseData[2];
                if (errorCode != 0) {
                    if (errorCode == 0x01 || errorCode == 0x03) {
                        throw new Exception((String) executeCommand(out, in, 0x06, 1)[2]);
                    }
                    throw new Exception(String.format("Ошибка 0x%s", toHexString(errorCode)));
                }

                result = new Object[responseData.length - 4];
                System.arraycopy(responseData, 3, result, 0, responseData.length - 4);
            }
        }
        return result;
    }

    //Парсинг пакета ответа со стороны КП
    static Object[] parseResponse(byte[] response) throws Exception {
        String data = new String(response, ENCODING);
        int packetId = data.charAt(1);                              // ID пакета
        int command = Integer.parseInt(data.substring(2, 4), 16);   // Код команды
        int errorCode = Integer.parseInt(data.substring(4, 6), 16); // Код ошибки
        String dataPart = data.substring(6, data.indexOf(ETX));     // Данные
        String dataForCRCPart = data.substring(0, data.indexOf(ETX) + 1);
        String crcPart = data.substring(data.indexOf(ETX) + 1);
        int crc = Integer.parseInt(crcPart, 16);
        if (crc != calculateCrc(dataForCRCPart)) {
            throw new Exception("Wrong CRC");
        }

        Object[] dataArray = Arrays.stream(dataPart.split(String.valueOf(FS))).toArray();
        Object[] result = new Object[3 + dataArray.length + 1];
        result[0] = packetId;
        result[1] = command;
        result[2] = errorCode;
        System.arraycopy(dataArray, 0, result, 3, dataArray.length);
        result[result.length - 1] = crc;
        return result;
    }

    //Формирование пакета команды со стороны КП
    private static byte[] makeRequest(int command, Object... parameters) {
        StringBuilder strPacket = new StringBuilder()
            .append(STX)                                    // STX
            .append(PASSWORD)                               // Пароль связи
            .append((char) PACKET_ID++)                     // ID пакета
            .append(toHexString(command));                  // Код команды
        if (parameters.length == 0) {                       //
            strPacket.append(FS);                           //
        } else {                                            //
            for (Object param : parameters) {               //
                strPacket.append(param).append(FS);         // Данные
            }                                               //
        }                                                   //
        strPacket.append(ETX);                              // ETX
        int crc = calculateCrc(strPacket.toString());
        strPacket.append(toHexString(crc));                 // CRC

        return strPacket
            .toString()
            .getBytes(ENCODING);
    }

    //Разбиение полученных байтов по пакетам
    static List<byte[]> splitPackets(byte[] response) {
        List<byte[]> result = new ArrayList<>();
        List<Byte> part = new ArrayList<>();
        part.add(response[0]);
        for (int i = 1; i < response.length; i++) {
            if (response[i] != STX) {
                part.add(response[i]);
            }
            if (response[i] == STX || i == response.length - 1) {
                result.add(ArrayUtils.toPrimitive(part.toArray(new Byte[0])));
                part.clear();
                part.add(response[i]);
            }
        }
        return result;
    }

    //Красивая запись данных пакета для логирования
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

    //Расчет контрольной суммы пакета
    private static int calculateCrc(String string) {
        byte[] rawPacket = string.getBytes(ENCODING);
        int crc = 0;
        for (int i = 1; i < rawPacket.length; i++) {
            crc = (crc ^ rawPacket[i]) & 0xFF;
        }
        return crc;
    }

    //Понятное преобразование 16-тиричного представления числа
    private static String toHexString(int value) {
        return StringUtils.leftPad(Integer.toHexString(value & 0xFF).toUpperCase(), 2, '0');
    }
}

