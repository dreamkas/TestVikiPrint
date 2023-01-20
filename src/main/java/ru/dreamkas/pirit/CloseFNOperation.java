package ru.dreamkas.pirit;

import jssc.SerialPort;

import static ru.dreamkas.pirit.Util.*;

public class CloseFNOperation {
    public static void main(String[] args) {
        try {
            port.openPort();
            {
                // Закрытие смены
                port.writeBytes(makeRequest(0x21, "Обязательный параметр - Кассир Петров"));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Закрытие ФН. Не требует дополнительных параметров
                port.writeBytes(makeRequest(0x71, "Обязательный параметр - Петров"));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Восстановление МГМ для последующей фискализации ККТ. Не требует дополнительных параметров
                port.writeBytes(makeRequest(0x9B));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(100);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            port.closePort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
