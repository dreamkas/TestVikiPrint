import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jssc.SerialPort;
import ru.dreamkas.viki_print.VikiPrint;

import static ru.dreamkas.viki_print.VikiPrint.DATE_FORMATTER;
import static ru.dreamkas.viki_print.VikiPrint.DATE_TIME_FORMATTER;
import static ru.dreamkas.viki_print.VikiPrint.TIME_FORMATTER;

public class VikiPrintTest {
    private static SerialPort port;
    private static int maxFFDVersion;
    private static final boolean PRINT = false;

    @BeforeAll
    public static void setup() throws Exception {
        port = new SerialPort(VikiPrint.COM_PORT);
        port.openPort();
        port.purgePort(SerialPort.PURGE_TXCLEAR | SerialPort.PURGE_RXCLEAR);
        while (port.getInputBufferBytesCount() > 0) {
            port.readBytes();
        }
        VikiPrint.checkConnection(port);

        Object[] fnVersions = VikiPrint.executeCommand(port, 0x78, 22);
        maxFFDVersion = Integer.parseInt((String) fnVersions[2]);
        setPrintDocuments(PRINT);
    }

    @BeforeEach
    public void prepare() throws Exception {
        Object[] flags = VikiPrint.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        int docStatus = Integer.parseInt((String) flags[2]);
        if ((status & (1L)) != 0) { // Не выполнена команда “Начало работы”
            LocalDateTime now = LocalDateTime.now();
            String date = now.format(DATE_FORMATTER);
            String time = now.format(TIME_FORMATTER);
            VikiPrint.executeCommand(port, 0x10, date, time); // Начало работы с ККТ (0x10)
        }
        if ((docStatus & 0x1F) != 0) { // Открыт документ
            VikiPrint.executeCommand(port, 0x32); // Аннулировать документ (0x32)
        }
        if ((status & (1L << 2)) == 0) { // Смена не открыта
            VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
        } else if ((status & (1L << 3)) != 0) { // 24 часа истекли
            VikiPrint.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
            VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
        }
    }

    @AfterAll
    public static void finalisation() throws Exception {
        setPrintDocuments(!PRINT);
        port.closePort();
    }

    private static void setPrintDocuments(boolean print) throws Exception {
        Object[] tableData = VikiPrint.executeCommand(port, 0x11, 1, 0);
        int value = Integer.parseInt((String) tableData[0]);
        if (print) {
            value &= (~(1 << 7));
        } else {
            value |= (1 << 7);
        }
        VikiPrint.executeCommand(port, 0x12, 1, 0, value);
    }

    @Test
    @DisplayName("Закрытие / открытие кассовой смены")
    public void testShiftCloseAndOpen() throws Exception {
        Object[] flags = VikiPrint.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            VikiPrint.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        } else {
            VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
            VikiPrint.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }
        VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
    }

    @Test
    @DisplayName("Перерегистрация ККТ без замены ФН")
    public void testRegistrationKKT() throws Exception {
        //Проверим, что смена не закрыта
        Object[] flags = VikiPrint.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            //Закроем смену
            VikiPrint.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }

        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE_FORMATTER);
        String time = now.format(TIME_FORMATTER);
        //Выполним перерегистрацию ККТ без замены ФН
        Object[] response = VikiPrint.executeCommand(port, 0x60,
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
        Object[] fnVersions = VikiPrint.executeCommand(port, 0x78, 22);
        int ffdVersion = Integer.parseInt((String) fnVersions[1]);

        int tag2106 = 0;
        if (ffdVersion == 4) { // Версия ФФД 1.2 поэтому проверяем КМ в ФН
            // Запрос проверки КМ в ФН
            VikiPrint.executeCommand(port, 0x79, 3);
            Object[] data = VikiPrint.executeCommand(port, 0x79, 1, "0103041094787443215CY6tH\u001D93dGVz", 0, 2, 900, 10, 1);
            tag2106 = Integer.parseInt((String) data[1]);

            // Подтверждение добавления КМ в чек
            VikiPrint.executeCommand(port, 0x79, 2, 1, "", "", "", "", "");
        }

        VikiPrint.executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        if (ffdVersion == 4) { // Версия ФФД 1.2 передаем в ФН
            VikiPrint.executeCommandPacket(port, 0x79, 15, "0103041094787443215CY6tH\u001D93dGVz", 2, 0, tag2106, 10, "");
        } else { // Версия ФФД 1.05 передаем в дополнительные реквизиты позиции
            VikiPrint.executeCommandPacket(port, 0x24, "0103041094787443215CY6tH\u001D93dGVz");
        }
        VikiPrint.executeCommandPacket(port, 0x42, "Маркированный товар", "", 1, 100, 0, "", "", "", 10, "", 4, 4, "", "", "");
        VikiPrint.executeCommandPacket(port, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "");
        VikiPrint.executeCommandPacket(port, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", 11);
        VikiPrint.executeCommandPacket(port, 0x44);
        VikiPrint.executeCommandPacket(port, 0x47, 0, 1000);
        Object[] response = VikiPrint.executeCommand(port, 0x31, "2");
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
        Object[] fnVersions = VikiPrint.executeCommand(port, 0x78, 22);
        int ffdVersion = Integer.parseInt((String) fnVersions[1]);

        int tag2106 = 0;
        if (ffdVersion == 4) { // Версия ФФД 1.2 поэтому проверяем КМ в ФН
            // Запрос проверки КМ в ФН
            VikiPrint.executeCommand(port, 0x79, 3);
            Object[] data = VikiPrint.executeCommand(port, 0x79, 1, "0103041094787443215CY6tH\u001D93dGVz", 0, 2, 900, 10, 1);
            tag2106 = Integer.parseInt((String) data[1]);

            // Подтверждение добавления КМ в чек
            VikiPrint.executeCommand(port, 0x79, 2, 1, "", "", "", "", "");
        }

        VikiPrint.executeCommand(port, 0x30, 2, 1, "Петров", "", 0, "");
        if (ffdVersion == 4) { // Версия ФФД 1.2 передаем в ФН
            VikiPrint.executeCommand(port, 0x79, 15, "0103041094787443215CY6tH\u001D93dGVz", 2, 0, tag2106, 10, "");
        } else { // Версия ФФД 1.05 передаем в дополнительные реквизиты позиции
            VikiPrint.executeCommand(port, 0x24, "0103041094787443215CY6tH\u001D93dGVz");
        }

        VikiPrint.executeCommand(port, 0x42, "Маркированный товар", "", 1, 100, 0, "", "", "", 10, "", 4, 4, "", "", "");
        VikiPrint.executeCommand(port, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "");
        VikiPrint.executeCommand(port, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", 11);
        VikiPrint.executeCommand(port, 0x44);
        VikiPrint.executeCommand(port, 0x47, 0, 1000.0);
        Object[] response = VikiPrint.executeCommand(port, 0x31, 2);
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
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE_FORMATTER);
        String time = now.format(TIME_FORMATTER);

        // Регистрация чека
        VikiPrint.executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        VikiPrint.executeCommandPacket(port, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "");
        VikiPrint.executeCommandPacket(port, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", 11);
        VikiPrint.executeCommandPacket(port, 0x44);
        VikiPrint.executeCommandPacket(port, 0x47, 0, 1000);
        Object[] response = VikiPrint.executeCommand(port, 0x31, 2, "customer@mail.ru");

        // Получение номера чека и номера ФД
        Integer fdNum = Integer.valueOf((String) response[3]);
        Integer checkNum = Integer.valueOf((String) response[6]);

        // Формирование копии чека
        VikiPrint.executeCommandPacket(port, 0x53, 2 | 16, 1, "Петров", checkNum, 4, date, time, fdNum, 0);
        VikiPrint.executeCommandPacket(port, 0x42, "Штучный товар", "", 1, 100, 4, "", "", "");
        VikiPrint.executeCommandPacket(port, 0x42, "Весовой товар", "", 1.111, 100, 4, "", "", "", 11);
        VikiPrint.executeCommandPacket(port, 0x44);
        VikiPrint.executeCommandPacket(port, 0x47, 0, 1000);
        response = VikiPrint.executeCommand(port, 0x31, 2, "customer@mail.ru");
        Assertions.assertEquals(2, response.length, "Получены лишние данные из копии чека");
    }
}
