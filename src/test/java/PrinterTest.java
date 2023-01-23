import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jssc.SerialPort;
import jssc.SerialPortException;
import ru.dreamkas.viki_print.VikiPrintExamples;


public class PrinterTest {
    private static SerialPort port;

    @BeforeEach
    public void setupTest() throws Exception {
        Object[] flags = VikiPrintExamples.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) == 0) {
            VikiPrintExamples.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
        }
    }

    @BeforeAll
    public static void setup() throws SerialPortException {
        port = new SerialPort("COM11");
        port.openPort();
    }

    @AfterAll
    public static void finalisation() throws SerialPortException {
        port.closePort();
    }

    @Test
    public void testConnection() throws Exception {
        VikiPrintExamples.checkConnection(port);
    }

    @Test
    public void testShiftCloseAndOpen() throws Exception {
        Object[] flags = VikiPrintExamples.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            VikiPrintExamples.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        } else {
            VikiPrintExamples.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
            VikiPrintExamples.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }
        VikiPrintExamples.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
    }

    @Test
    public void testRegistrationKKT() throws Exception {
        Object[] flags = VikiPrintExamples.executeCommand(port, 0x00);
        int status = Integer.parseInt((String) flags[1]);
        if ((status & (1L << 2)) != 0) {
            VikiPrintExamples.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
        }
        VikiPrintExamples.executeCommand(port, 0x60,"0","1000000000008261","1122334455","1","0","Петров",
            LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")),
        new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()),"            НАЗВАНИЕ ОРГАНИЗАЦИИ            ","",
        "             АДРЕС ОРГАНИЗАЦИИ              ",
            "","второй этаж","","Такском ОФД","7704211201","ofd-receipts@dreamkas.ru","www.nalog.gov.ru","1","4"); // Регистрация ККТ
    }

    @Test
    public void testKMPurchase() throws Exception {
        VikiPrintExamples.executeCommand(port, 0x79,"1","OTc4MDIwMTM3OTYy","0","2","900","10","1");
        VikiPrintExamples.executeCommand(port, 0x79,"2","1","", "", "", "", "");
        VikiPrintExamples.executeCommand(port, 0x30,"2","1","Петров","0");
        VikiPrintExamples.executeCommand(port, 0x79,"15","OTc4MDIwMTM3OTYy","2","0","15","10","");
        VikiPrintExamples.executeCommand(port, 0x42,"Товар","","900","100","0","", "", "","10","","4","4","", "", "");
        VikiPrintExamples.executeCommand(port, 0x47,"1","90000","");
        VikiPrintExamples.executeCommand(port, 0x31, "2");
    }

    @Test
    public void testPurchaseInPacketMode() throws Exception {
        VikiPrintExamples.executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        VikiPrintExamples.executeCommandPacket(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
        VikiPrintExamples.executeCommandPacket(port, 0x42,"Сахар","","1.111","100","4","", "", "","11");
        VikiPrintExamples.executeCommandPacket(port, 0x44);
        VikiPrintExamples.executeCommandPacket(port, 0x47, 0, 1000);
        VikiPrintExamples.executeCommand(port, 0x31, "2");
    }

    @Test
    public void testPurchaseInRegularMode() throws Exception {
        VikiPrintExamples.executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
        VikiPrintExamples.executeCommandPacket(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
        VikiPrintExamples.executeCommandPacket(port, 0x42,"Сахар","","1.111","100","4","", "", "","11");
        VikiPrintExamples.executeCommandPacket(port, 0x44);
        VikiPrintExamples.executeCommandPacket(port, 0x47, 0, 1000);
        VikiPrintExamples.executeCommand(port, 0x31, "2");
    }

}
