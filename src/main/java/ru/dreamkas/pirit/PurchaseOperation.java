package ru.dreamkas.pirit;

import jssc.SerialPort;

import static ru.dreamkas.pirit.Util.*;

public class PurchaseOperation {
    public static void main(String[] args) {

        try {
            port.openPort();
            {
                // Открытие чека на оплату
                port.writeBytes(makeRequest(0x30,
                    // 1 - Чек на оплату (2)
                    "2",
                    // 2 - Номер отдела
                    "1",
                    // 3 - Имя оператора
                    "Петров"
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Добавление товарной позиции
                port.writeBytes(makeRequest(0x42,
                    // 1 - Наименование товара
                    "Сахар",
                    // 2 - Артикул, если он отсутствует, оставляем пустым
                    "",
                    // 3 - Количество текущей позиции. Если товар штучный, то передавать целое число, если весовой, то дробное
                    "1",
                    // 4 - Цена
                    "100",
                    // 5 - Ставка налога
                    "4",
                    // Передача необязательных атрибутов "Номер позиции", "Номер секции", "Тип скидки"
                    "", "", ""
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Подитог (НЕОБЯЗАТЕЛЕН)
                port.writeBytes(makeRequest(0x44));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Оплата
                port.writeBytes(makeRequest(0x47,
                    // 1 - Тип платежа, условно 0 - наличный, 1 - безналичный, видов может быть много, все соответствует "Таблице настроек ККТ"
                    "0",
                    // 2 - Сумма платежа
                    "1000"
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Закрытие и обрезка чека
                port.writeBytes(makeRequest(0x31, "2"));
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

