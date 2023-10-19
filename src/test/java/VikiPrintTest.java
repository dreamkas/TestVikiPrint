import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jssc.SerialPort;
import jssc.SerialPortException;
import ru.dreamkas.viki_print.VikiPrint;

public class VikiPrintTest {
    private static SerialPort port;

    @BeforeAll
    public static void setup() throws Exception {
        port = new SerialPort(VikiPrint.COM_PORT);
        port.openPort();
        port.purgePort(SerialPort.PURGE_TXCLEAR | SerialPort.PURGE_RXCLEAR);
        while (port.getInputBufferBytesCount()>0) {
            port.readBytes();
        }
        VikiPrint.checkConnection(port);
    }

    @BeforeEach
    public void prepare() throws Exception {
        Object[] flags = VikiPrint.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        int docStatus = Integer.parseInt((String) flags[2]);
        if ((status & (1L)) != 0) { // Не выполнена команда “Начало работы”
            LocalDateTime now = LocalDateTime.now();
            String date = now.format(DateTimeFormatter.ofPattern("ddMMyy"));
            String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
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
    public static void finalisation() throws SerialPortException {
        port.closePort();
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
        String date = now.format(DateTimeFormatter.ofPattern("ddMMyy"));
        String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
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
            2,
            4
        ); // Регистрация ККТ

        Assertions.assertNotEquals(0, response.length, "Не получен номер ФД");
        Assertions.assertNotNull(response[0], "Не получен номер ФД");
        Assertions.assertNotNull(response[1], "Не получена ФП");
        date = (String) response[2];
        time = (String) response[3];
        LocalDateTime regDate = LocalDateTime.parse(date + time, DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }

    @Test
    @DisplayName("Формирование чека в пакетном режиме")
    public void testPurchaseInPacketMode() throws Exception {
        // Запрос проверки КМ в ФН
        VikiPrint.executeCommand(port, 0x79, 3);
        Object[] data = VikiPrint.executeCommand(port, 0x79, 1, "OTc4MDIwMTM3OTYy", 0, 2, 900, 10, 1);
        int tag2106 = Integer.parseInt((String) data[1]);

        // Подтверждение добавления КМ в чек
        VikiPrint.executeCommand(port, 0x79, 2, 1, "", "", "", "", "");

        VikiPrint.executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        VikiPrint.executeCommandPacket(port, 0x79, 15, "OTc4MDIwMTM3OTYy", 2, 0, tag2106, 10, "");
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
        LocalDateTime regDate = LocalDateTime.parse(date + time, DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }

    @Test
    @DisplayName("Формирование чека в синхронном режиме")
    public void testPurchaseInRegularMode() throws Exception {
        // Запрос проверки КМ в ФН
        VikiPrint.executeCommand(port, 0x79, 3);
        Object[] data = VikiPrint.executeCommand(port, 0x79, 1, "OTc4MDIwMTM3OTYy", 0, 2, 900, 10, 1);
        int tag2106 = Integer.parseInt((String) data[1]);

        // Подтверждение добавления КМ в чек
        VikiPrint.executeCommand(port, 0x79, 2, 1, "", "", "", "", "");

        VikiPrint.executeCommand(port, 0x30, 2, 1, "Петров", "", 0, "");
        VikiPrint.executeCommand(port, 0x79, 15, "OTc4MDIwMTM3OTYy", 2, 0, tag2106, 10, "");
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
        LocalDateTime regDate = LocalDateTime.parse(date + time, DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        Assertions.assertTrue(ChronoUnit.MINUTES.between(regDate, LocalDateTime.now()) < 5);
    }
}