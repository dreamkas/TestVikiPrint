package ru.dreamkas.pirit;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;


import jssc.SerialPort;

import static ru.dreamkas.pirit.Util.*;

public class KKTRegistration {
    public static void main(String[] args) {
        try {
            port.openPort();
            {
                // Регистрация/Перерегистрация ККТ с заменой ФН
                port.writeBytes(makeRequest(0x60,
                    // 1 - параметр с Заменой ФН (1) или без (0)
                    "1",
                    // 2 - Регистрационный номер
                    "1000000000008261",
                    // 3 - ИНН Владельца
                    "1122334455",
                    // 4 - Система налогоообложения, в данном случае стоит "Упрощенная Доход"
                    "1",
                    // 5 - Режим работы, в данном случае стоит "Признак шифрования"
                    "0",
                    // 6 - Кассир
                    "Петров",
                    // 7 - Текущая дата, вносится текущая дата в формате 'ddMMyy' без знаков препинания
                    LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")),
                    // 8 - Текущее время, вносится текущее время в формате 'HHmmss' без знаков препинания
                    new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()),
                    // 9 - Наименование пользователя 2 раза
                    "            НАЗВАНИЕ ОРГАНИЗАЦИИ            ",
                    "",
                    // 10 - Адрес пользователя 2 раза
                    "             АДРЕС ОРГАНИЗАЦИИ              ",
                    "",
                    // Место расчетов
                    "второй этаж",
                    //  Номер автомата.
                    "",
                    // Наименование ОФД.
                    "Такском ОФД",
                    // ИНН ОФД.
                    "7704211201",
                    // Адрес электронной почты отправителя чека.
                    "ofd-receipts@dreamkas.ru",
                    // адрес сайта ФНС.
                    "www.nalog.gov.ru",
                    //(Число) Дополнительный режим работы.
                    "1",
                    //(Число) Версия ФФД.
                    "4"
                    ));
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
