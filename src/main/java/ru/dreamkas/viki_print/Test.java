package ru.dreamkas.viki_print;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import static ru.dreamkas.viki_print.Util.*;

public class Test {
    public static void main(String[] args) {
        // Запрос состояния печатающего устройства (ПУ)
        makeKKTRequest(0x04, "");

        // Проверка связи
        makeKKTRequest(0x05, "");

        // Печать сервисного чека
        makeKKTRequest(0x30, "1", "1");
        makeKKTRequest(0x40, "Текст");
        makeKKTRequest(0x31, "2");

        //Открытие и закрытие смены, печать X-отчета
        makeKKTRequest(0x23, "Кассир Петров");
        makeKKTRequest(0x20, "");
        makeKKTRequest(0x21, "Кассир Петров");

        // Закрытие архива ФН
        makeKKTRequest(0x71, "Петров");

        // Продажа штучного товара
        makeKKTRequest(0x30,"2", "1", "Петров");
        makeKKTRequest(0x42,"Сахар","","1","100","4","", "", "");
        makeKKTRequest(0x44);
        makeKKTRequest(0x47,"0","1000");
        makeKKTRequest(0x31, "2");

        // Продажа весового товара
        makeKKTRequest(0x30,"2", "1", "Петров");
        makeKKTRequest(0x42,"Сахар","","1.111","100","4","", "", "","11");
        makeKKTRequest(0x44);
        makeKKTRequest(0x47,"0","1000");
        makeKKTRequest(0x31, "2");

        // Продажа маркированного товара
        makeKKTRequest(0x79,"1","OTc4MDIwMTM3OTYy","0","2","900","10","1");
        makeKKTRequest(0x79,"2","1","", "", "", "", "");
        makeKKTRequest(0x30,"2","1","Петров","0");
        makeKKTRequest(0x79,"15",maskNonPrintableChars("OTc4MDIwMTM3OTYy"),"2","0","15","10","");
        makeKKTRequest(0x42,"Товар","","900","100","0","", "", "","10","","4","4","", "", "");
        makeKKTRequest(0x47,"1","90000","");
        makeKKTRequest(0x31, "2");

        // Регистрация/Перерегистрация ККТ с заменой ФН
        makeRequest(0x60,"1","1000000000008261","1122334455","1","0","Петров",
            LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy")),new SimpleDateFormat("HHmmss").format(Calendar.getInstance().getTime()),
            "            НАЗВАНИЕ ОРГАНИЗАЦИИ            ","","             АДРЕС ОРГАНИЗАЦИИ              ","","второй этаж","","Такском ОФД","7704211201",
            "ofd-receipts@dreamkas.ru","www.nalog.gov.ru","1","4");
    }
}