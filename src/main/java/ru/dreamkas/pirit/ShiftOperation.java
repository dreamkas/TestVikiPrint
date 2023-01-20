package ru.dreamkas.pirit;

import jssc.SerialPort;

import static ru.dreamkas.pirit.Util.*;

public class ShiftOperation {
        public static void main(String[] args) {
            try {
                port.openPort();
                {
                    // Открытие смены
                    port.writeBytes(makeRequest(0x23, "Обязательный параметр - Кассир Петров"));
                    port.purgePort(SerialPort.PURGE_TXCLEAR);
                    byte[] read = port.readBytes(20);
                    System.out.printf("<-- %s%n", Util.toString(read));
                    checkCrc(read);
                }
                {
                    // Печать Х-отчета
                    port.writeBytes(makeRequest(0x20, "Необязательный параметр - Кассир Петров"));
                    port.purgePort(SerialPort.PURGE_TXCLEAR);
                    byte[] read = port.readBytes(20);
                    System.out.printf("<-- %s%n", Util.toString(read));
                    checkCrc(read);
                }
                {
                    // Закрытие смены
                    port.writeBytes(makeRequest(0x21, "Обязательный параметр - Кассир Петров"));
                    port.purgePort(SerialPort.PURGE_TXCLEAR);
                    byte[] read = port.readBytes(20);
                    System.out.printf("<-- %s%n", Util.toString(read));
                    checkCrc(read);
                }

                port.closePort();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
}
