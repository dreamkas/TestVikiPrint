import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.dreamkas.viki_print.VikiPrintWithVikiDriver;

import static ru.dreamkas.viki_print.VikiPrintWithVikiDriver.DATE_FORMATTER;
import static ru.dreamkas.viki_print.VikiPrintWithVikiDriver.DATE_TIME_FORMATTER;
import static ru.dreamkas.viki_print.VikiPrintWithVikiDriver.TIME_FORMATTER;

public class VikiPrintWithVikiDriverTest {

    private static InputStream in;
    private static OutputStream out;
    private static Socket vikiDriverSocket;
    private static int maxFFDVersion;
    private static final boolean PRINT = false; // Флаг печатать документа на чековой ленте или не печатать!

    @BeforeAll
    public static void setup() throws Exception {
        vikiDriverSocket = new Socket();
        vikiDriverSocket.connect(new InetSocketAddress("127.0.01", 50003), 3000);
        in = vikiDriverSocket.getInputStream();
        out = vikiDriverSocket.getOutputStream();
        VikiPrintWithVikiDriver.checkConnection(out, in);

        Object[] fnVersions = VikiPrintWithVikiDriver.executeCommand(out, in, 0x78, 22);
        maxFFDVersion = Integer.parseInt((String) fnVersions[2]);
        setPrintDocuments(PRINT);
    }

    @BeforeEach
    public void prepare() throws Exception {
        Object[] flags = VikiPrintWithVikiDriver.executeCommand(out, in, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        int docStatus = Integer.parseInt((String) flags[2]);
        if ((status & (1L)) != 0) { // Не выполнена команда “Начало работы”
            LocalDateTime now = LocalDateTime.now();
            String date = now.format(DATE_FORMATTER);
            String time = now.format(TIME_FORMATTER);
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x10, date, time); // Начало работы с ККТ (0x10)
        }
        if ((docStatus & 0x1F) != 0) { // Открыт документ
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x32); // Аннулировать документ (0x32)
        }
        if ((status & (1L << 2)) == 0) { // Смена не открыта
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x23, "Администратор"); // Открыть смену (0x23)
        } else if ((status & (1L << 3)) != 0) { // 24 часа истекли
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x23, "Администратор"); // Открыть смену (0x23)
        }
    }

    @AfterAll
    public static void finalisation() throws Exception {
        setPrintDocuments(!PRINT);
        vikiDriverSocket.close();
    }

    private static void setPrintDocuments(boolean print) throws Exception {
        Object[] tableData = VikiPrintWithVikiDriver.executeCommand(out, in, 0x11, 1, 0);
        int value = Integer.parseInt((String) tableData[0]);
        if (print) {
            value &= (~(1 << 7));
        } else {
            value |= (1 << 7);
        }
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x12, 1, 0, value);
    }

    @Test
    @DisplayName("Закрытие / открытие кассовой смены")
    public void testShiftCloseAndOpen() throws Exception {
        Object[] flags = VikiPrintWithVikiDriver.executeCommand(out, in, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        } else {
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x23, "Администратор"); // Открыть смену (0x23)
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x23, "Администратор"); // Открыть смену (0x23)
    }

    @Test
    @DisplayName("Перерегистрация ККТ без замены ФН")
    public void testRegistrationKKT() throws Exception {
        //Проверим, что смена не закрыта
        Object[] flags = VikiPrintWithVikiDriver.executeCommand(out, in, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            //Закроем смену
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }

        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE_FORMATTER);
        String time = now.format(TIME_FORMATTER);
        //Выполним перерегистрацию ККТ без замены ФН
        Object[] response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x60,
            0,
            "1000000000008261",
            "1122334455",
            1,
            0,
            "Петров",
            date,
            time,
            "            НАЗВАНИЕ ОРГАНИЗАЦИИ            ",
            "",
            "             АДРЕС ОРГАНИЗАЦИИ              ",
            "",
            "второй этаж",
            "",
            "Такском ОФД",
            "7704211201",
            "ofd-receipts@dreamkas.ru",
            "www.nalog.gov.ru",
            maxFFDVersion == 4 ? 2 : 0,
            maxFFDVersion == 4 ? 4 : 2
        ); // Регистрация ККТ

        Assertions.assertNotEquals(0, response.length, "Не получен номер ФД");
        Assertions.assertNotNull(response[0], "Не получен номер ФД");
        Assertions.assertNotNull(response[1], "Не получена ФП");
        date = (String) response[2];
        time = (String) response[3];
        LocalDateTime regDate = LocalDateTime.parse(date + time, DATE_TIME_FORMATTER);
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }

    @Test
    @DisplayName("Формирование чека в пакетном режиме")
    public void testPurchaseInPacketMode() throws Exception {
        // Запрос проверки КМ в ФН
        Object[] fnVersions = VikiPrintWithVikiDriver.executeCommand(out, in, 0x78, 14);
        int ffdVersion = Integer.parseInt((String) fnVersions[3]); // 2 = ФФД 1.05; 4 = ФФД 1.2

        int tag2106 = 0;
        if (ffdVersion == 4) { // Версия ФФД 1.2 поэтому проверяем КМ в ФН
            // Запрос проверки КМ в ФН
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 3);
            Object[] data = VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 1, "0103041094787443215CY6tH\u001D93dGVz", 0, 2, 900, 10, 1);
            tag2106 = Integer.parseInt((String) data[1]);

            // Подтверждение добавления КМ в чек
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 2, 1, "", "", "", "", "");
        }

        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        if (ffdVersion == 4) { // Версия ФФД 1.2 передаем в ФН
            VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x79, 15, "0103041094787443215CY6tH\u001D93dGVz", 2, 0, tag2106, 10, "");

            /**
             * Значение реквизита «отраслевой реквизит предмета расчета» 1260.
             * Более подробно можно прочитать в документации от ЧЗ пункт 3.1. Ссылка на документ указана в Readme.md.
             */

            //Значение реквизита «отраслевой реквизит предмета расчета» 1260:
            String tag1262 = "030";                                                                                       // Значение реквизита «идентификатор ФОИВ» (тег 1262)
            String tag1263 = "21112023";                                                                                  // Значение реквизита «дата документа основания» (тег 1263)
            String tag1264 = "1944";                                                                                      //Значение реквизита «номер документа основания» (тег 1264)
            String tad1265 = String.format("UUID=%s&Time=%s", "2ce10bdb-6510-4d37-be04-dd473b98c728", "1692691702065");   //Значение реквизита «значение отраслевого реквизита» (тег 1265)

            VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x24, "", "", "", "", "", "", "", "", "", "", "", "", "", "", tag1262, tag1263, tag1264, tad1265);
        } else { // Версия ФФД 1.05 передаем в дополнительные реквизиты позиции
            VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x24, "0103041094787443215CY6tH\u001D93dGVz");
        }
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, "Маркированный товар", "", 1, 100.0, 0, "", "", "", (ffdVersion == 4 ? 0 : "шт"), "", 4, (ffdVersion == 4 ? 33 : 1), "", "", "");
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, "Штучный товар", "", 1, 100.0, 4, "", "", "", (ffdVersion == 4 ? 0 : "шт"));
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, "Весовой товар", "", 1.111, 100.0, 4, "", "", "", (ffdVersion == 4 ? 11 : "кг"));
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x44);
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x47, 0, 1000);
        Object[] response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x31, "2");
        Assertions.assertNotEquals(0, response.length, "Не получен номер ФД");
        Assertions.assertNotNull(response[3], "Не получен номер ФД");
        Assertions.assertNotNull(response[4], "Не получена ФП");
        Assertions.assertNotNull(response[5], "Не получена номер смены");
        Assertions.assertNotNull(response[6], "Не получен номер документа в смене");
        Assertions.assertNotNull(response[7], "Не получен дата документа");
        Assertions.assertNotNull(response[8], "Не получен время документа");
        String date = (String) response[7];
        String time = (String) response[8];
        LocalDateTime regDate = LocalDateTime.parse(date + time, DATE_TIME_FORMATTER);
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }

    @Test
    @DisplayName("Формирование чека в синхронном режиме")
    public void testPurchaseInRegularMode() throws Exception {
        Object[] fnVersions = VikiPrintWithVikiDriver.executeCommand(out, in, 0x78, 14);
        int ffdVersion = Integer.parseInt((String) fnVersions[3]); // 2 = ФФД 1.05; 4 = ФФД 1.2

        int tag2106 = 0;
        if (ffdVersion == 4) { // Версия ФФД 1.2 поэтому проверяем КМ в ФН
            // Запрос проверки КМ в ФН
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 3);
            Object[] data = VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 1, "0103041094787443215CY6tH\u001D93dGVz", 0, 2, 900, 10, 1);
            tag2106 = Integer.parseInt((String) data[1]);

            // Подтверждение добавления КМ в чек
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 2, 1, "", "", "", "", "");
        }

        VikiPrintWithVikiDriver.executeCommand(out, in, 0x30, 2, 1, "Петров", "", 0, "");
        if (ffdVersion == 4) { // Версия ФФД 1.2 передаем в ФН
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x79, 15, "0103041094787443215CY6tH\u001D93dGVz", 2, 0, tag2106, 10, "");

            /**
             * Значение реквизита «отраслевой реквизит предмета расчета» 1260.
             * Более подробно можно прочитать в документации от ЧЗ пункт 3.1. Ссылка на документ указана в Readme.md.
             */

            //Значение реквизита «отраслевой реквизит предмета расчета» 1260:
            String tag1262 = "030";                                                                                       // Значение реквизита «идентификатор ФОИВ» (тег 1262)
            String tag1263 = "21112023";                                                                                  // Значение реквизита «дата документа основания» (тег 1263)
            String tag1264 = "1944";                                                                                      //Значение реквизита «номер документа основания» (тег 1264)
            String tad1265 = String.format("UUID=%s&Time=%s", "2ce10bdb-6510-4d37-be04-dd473b98c728", "1692691702065");   //Значение реквизита «значение отраслевого реквизита» (тег 1265)

            VikiPrintWithVikiDriver.executeCommand(out, in, 0x24, "", "", "", "", "", "", "", "", "", "", "", "", "", "", tag1262, tag1263, tag1264, tad1265);
        } else { // Версия ФФД 1.05 передаем в дополнительные реквизиты позиции
            VikiPrintWithVikiDriver.executeCommand(out, in, 0x24, "0103041094787443215CY6tH\u001D93dGVz");
        }
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x42, "Маркированный товар", "", 1, 100.0, 0, "", "", "", (ffdVersion == 4 ? 0 : "шт"), "", 4, (ffdVersion == 4 ? 33 : 1), "", "", "");
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x42, "Штучный товар", "", 1, 100.0, 4, "", "", "", (ffdVersion == 4 ? 0 : "шт"));
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x42, "Весовой товар", "", 1.111, 100.0, 4, "", "", "", (ffdVersion == 4 ? 11 : "кг"));
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x44);
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x47, 0, 1000.0);
        Object[] response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x31, 2);
        Assertions.assertNotEquals(0, response.length, "Не получен номер ФД");
        Assertions.assertNotNull(response[3], "Не получен номер ФД");
        Assertions.assertNotNull(response[4], "Не получена ФП");
        Assertions.assertNotNull(response[5], "Не получена номер смены");
        Assertions.assertNotNull(response[6], "Не получен номер документа в смене");
        Assertions.assertNotNull(response[7], "Не получен дата документа");
        Assertions.assertNotNull(response[8], "Не получен время документа");
        String date = (String) response[7];
        String time = (String) response[8];
        LocalDateTime regDate = LocalDateTime.parse(date + time, DATE_TIME_FORMATTER);
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }

    @Test
    @DisplayName("Формирование копии чека в пакетном режиме")
    public void testCopyOfCheckInPacketMode() throws Exception {
        Object[] fnVersions = VikiPrintWithVikiDriver.executeCommand(out, in, 0x78, 14);
        int ffdVersion = Integer.parseInt((String) fnVersions[3]); // 2 = ФФД 1.05; 4 = ФФД 1.2

        // Регистрация чека в пакетном режиме
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x30, // Открыть документ
            2 | 16,	  // (Целое число) Режим и тип документа
            1,        // (Целое число 1..99) Номер отдела
            "Петров", // (Имя оператора) Имя оператора
            "",       // (Целое число) Номер документа
            0,        // (Число 0..5) Система налогообложения (Тег 1055)
            ""        // (Строка) Адрес пользователя (Тег 1009)
        );
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, // Добавить товарную позицию
            "Штучный товар", // (Строка[0...256]) Название товара
            "",              // (Строка[0..18]) Артикул товара/номер ТРК
            1,               // (Дробное число) Количество товара в товарной позиции
            100,             // (Дробное число[0..99999999.99]) Цена товара по данному артикулу
            4,               // (Целое число) Номер ставки налога
            "",              // (Строка[0..4]) Номер товарной позиции
            "",              // (Целое число 1..16) Номер секции
            "",              // (Целое число) Тип скидки/наценки
            (ffdVersion == 4 ? 0 : "шт"), // (Строка[0..38] или Целое число[0..255]) Единица измерения (Тег 2108), используется, начиная с версий 565.1.6, 665.4.6 и 570.30.0
            0,               // (Дробное число) Сумма скидки
            4,               // (Целое число) Признак способа расчета (Тег 1214)
            1,               // (Целое число) Признак предмета расчета (Тег 1212)
            "",              // (Строка[3]) Код страны происхождения товара (Тег 1230)
            "",              // (Строка[0...32]) Номер таможенной декларации (Тег 1231)
            0                // (Дробное число) Сумма акциза (Тег 1229)
        );
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, // Добавить товарную позицию
            "Весовой товар", // (Строка[0...256]) Название товара
            "",              // (Строка[0..18]) Артикул товара/номер ТРК
            1.111,           // (Дробное число) Количество товара в товарной позиции
            100,             // (Дробное число[0..99999999.99]) Цена товара по данному артикулу
            4,               // (Целое число) Номер ставки налога
            "",              // (Строка[0..4]) Номер товарной позиции
            "",              // (Целое число 1..16) Номер секции
            "",              // (Целое число) Тип скидки/наценки
            (ffdVersion == 4 ? 11 : "кг"), // (Строка[0..38] или Целое число[0..255]) Единица измерения (Тег 2108), используется, начиная с версий 565.1.6, 665.4.6 и 570.30.0
            0,               // (Дробное число) Сумма скидки
            4,               // (Целое число) Признак способа расчета (Тег 1214)
            1,               // (Целое число) Признак предмета расчета (Тег 1212)
            "",              // (Строка[3]) Код страны происхождения товара (Тег 1230)
            "",              // (Строка[0...32]) Номер таможенной декларации (Тег 1231)
            0                // (Дробное число) Сумма акциза (Тег 1229)
        );
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x44); // Подытог
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x47, // Оплата
            0,    // (Целое число 0..15) Код типа платежа
            1000, // (Дробное число) Сумма, принятая от покупателя по данному платежу
            ""    //(Строка[0..44]) Дополнительный текст
        );
        Object[] response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x31, // Завершить документ
            2,                  // (Целое число) Флаг отрезки
            "customer@mail.ru", // (Строка)[0..256] Адрес покупателя (Тег 1008)
            0,                  // (Число) Разные флаги
            "",                 // (Строка) Место расчётов (Тег 1187)
            "",                 // (Строка) Адрес отправителя чеков (Тег 1117)
            "",                 // (Строка) Номер автомата (Тег 1036)
            "",                 // (Строка) Наименование дополнительного реквизита пользователя (Тег 1085)
            "",                 // (Строка) Значение дополнительного реквизита пользователя (Тег 1086)
            "",                 // (Строка)[0..128] Покупатель (Тег 1227)
            "",                 // (Строка)[0..12] ИНН покупателя (Тег 1228)
            "",                 // (Дата8) Дата рождения покупателя (тег 1243). Параметр передается в случаях, установленных законодательством РФ и только при регистрации ККТ в режиме ФФД 1.2.
            0,                  // (Число)[0..3] Гражданство (тег 1244). Параметр передается в случаях, установленных законодательством РФ и только при регистрации ККТ в режиме ФФД 1.2.
            21,                 // (Число)[0..2] Код вида документа, удостоверяющего личность (тег 1245). Параметр передается в случаях, установленных законодательством РФ и только при регистрации ККТ в режиме ФФД 1.2.
            "",                 // (Строка)[0..64] Данные документа, удостоверяющего личность (Тег 1246). Параметр передается в случаях, установленных законодательством РФ и только при регистрации ККТ в режиме ФФД 1.2.
            ""                  // (Строка)[0..256] Адрес покупателя (клиента), географический адрес, не email (Тег 1254). Параметр передается в случаях, установленных законодательством РФ и только при регистрации ККТ в режиме ФФД 1.2.
        );
        // Получение номера чека и номера ФД
        Integer fdNum = Integer.valueOf((String) response[3]);
        Integer checkNum = Integer.valueOf((String) response[6]);
        String date = (String) response[7];
        String time = (String) response[8];

        // Формирование копии чека в синхронном режиме
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x53, // Открыть копию чека
            2,        // (Целое число) Тип чека
            1,        // (Целое число 1..99) Номер отдела
            "Петров", // (Имя оператора) Код и/или имя оператора
            checkNum, // (Целое число) Номер чека
            4,        // (Целое число 1..9999) Логический номер кассы
            date,     // (Дата) Дата чека
            time,     // (Время) Время чека
            fdNum,    // (Целое число) Номер ФД
            0         // (Целое число 0..5) Система налогообложения
        );
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "", (ffdVersion == 4 ? 0 : "шт"), 0, 4, 1, "", "", 0);
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", (ffdVersion == 4 ? 11 : "шт"), 0, 4, 1, "", "", 0);
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x44);
        VikiPrintWithVikiDriver.executeCommand(out, in, 0x47, 0, 1000, "");
        response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x31, 2, "customer@mail.ru", 0, "", "", "", "", "", "", "", "", 0, 21, "", "");
        Assertions.assertEquals(2, response.length, "Получены лишние данные из копии чека");

        // Формирование копии чека в пакетном режиме
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x53, 2 | 16, 1, "Петров", checkNum, 4, date, time, fdNum, 0);
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "", (ffdVersion == 4 ? 0 : "шт"), 0, 4, 1, "", "", 0);
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", (ffdVersion == 4 ? 11 : "шт"), 0, 4, 1, "", "", 0);
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x44);
        VikiPrintWithVikiDriver.executeCommandPacket(out, in, 0x47, 0, 1000, "");
        response = VikiPrintWithVikiDriver.executeCommand(out, in, 0x31, 2, "customer@mail.ru", 0, "", "", "", "", "", "", "", "", 0, 21, "", "");
        Assertions.assertEquals(2, response.length, "Получены лишние данные из копии чека");
    }
}