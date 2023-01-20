package ru.dreamkas.pirit;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import jssc.SerialPort;
import static ru.dreamkas.pirit.Util.makeRequest;
import static ru.dreamkas.pirit.Util.*;

public class KMPurchaseOperation {
    public static void main(String[] args) {

        try {
            port.openPort();
            {
                // Передача КМ в ФН для проверки достоверности КМ
                port.writeBytes(makeRequest(0x79,
                    // 1 - Передача атрибута 1, означающего, что должна быть проверка в ОИСМ
                    "1",
                    // 2 - КМ
                    "OTc4MDIwMTM3OTYy",
                    // 3 - Режим обработки кода маркировки
                    "0",
                    // 4 - Планируемый статус товара
                    "2",
                    // 5 - Кол-во товара
                    "900",
                    // 6 - Мера количества
                    "10",
                    // 7 - Режим работы
                    "1"
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(100);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Принятие КМ для включения в документ
                port.writeBytes(makeRequest(0x79,
                    // 1 - Передача атрибута 2, означающего, что
                    // команда предназначена для сохранения КМ в ФН для последующего включения КМ
                    // в состав реквизита предмет расчета товара, подлежащего обязательной маркировке.
                    "2",
                    // 2 - Результат проверки КМ в ФН
                    "1",
                    "", "", "", "", ""
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Открытие чека на оплату
                port.writeBytes(makeRequest(0x30,
                    // 1 - Чек на оплату (2)
                    "2",
                    // 2 - Номер отдела
                    "1",
                    // 3 - Имя оператора
                    "Петров",
                    "0"
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Передача КМ в ФН для проверки достоверности КМ
                port.writeBytes(makeRequest(0x79,
                    // 1 - Передача атрибута 15 для включения КМ в кассовый чек
                    "15",
                    // 2 - КМ
                    maskNonPrintableChars("OTc4MDIwMTM3OTYy"),
                    // 3 - Присвоенный статус товара
                    "2",
                    // 4 - Режим обработки кода маркировки
                    "0",
                    // 5 - Результат проведенной проверки КМ
                    "15",
                    // 6 - Мера количества
                    "10",
                    ""
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
                    "Товар",
                    // 2 - Артикул, если он отсутствует, оставляем пустым
                    "",
                    // 3 - Количество текущей позиции. Если товар штучный, то передавать целое число, если весовой, то дробное
                    "900",
                    // 4 - Цена
                    "100",
                    // 5 - Ставка налога
                    "0",
                    // Передача необязательных атрибутов "Номер позиции", "Номер секции", "Тип скидки"
                    "", "", "",
                    // 6 - Единица измерения
                    "10",
                    // 7 - Сумма скидки, необязательный параметр
                    "",
                    // 8 - Признак способа расчета
                    "4",
                    // 9 - Признак предмета расчета
                    "4",
                    // Передача необязательных атрибутов "Код страны происхождения", "Номер таможенной декларации", "Сумма акциза"
                    "", "", ""
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Оплата
                port.writeBytes(makeRequest(0x47,
                    // 1 - Тип платежа, условно 0 - наличный, 1 - безналичный, видов может быть много, все соответствует "Таблице настроек ККТ"
                    "1",
                    // 2 - Сумма платежа
                    "90000",
                    ""
                ));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(20);
                System.out.printf("<-- %s%n", Util.toString(read));
                checkCrc(read);
            }
            {
                // Закрытие и обрезка чека
                port.writeBytes(makeRequest(0x31, "1"));
                port.purgePort(SerialPort.PURGE_TXCLEAR);
                byte[] read = port.readBytes(1000);
                System.out.printf("<-- %s%n", Util.toString(read));
            }

            port.closePort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
