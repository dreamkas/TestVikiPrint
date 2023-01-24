## Проект VikiPrintExamples
Реализация базовых команд протокола Пирит на Java

## Для запуска проекта необходимо:
Для возможности использования проекта в качестве практического инструмента для интеграции кассового ПО
проект необходимо "клонировать" в среде разработки или через GitBash командой:

git clone https://gitlab.dreamkas.ru/viki/test-pirit

Рекомендуемая версия JDK для работы с данным проектом - [1.8.0_201](https://126008.selcdn.ru/setstart/jdk1.8.0_201.zip)

Далее можно открыть в IDE (например IntelliJ Idea) как Gradle проект указав файл **build.gradle**

В проекте задействованы библиотеки:</br>
Tests:
- 'org.junit.jupiter:junit-jupiter-api:5.8.1'
- 'org.junit.jupiter:junit-jupiter-engine:5.8.1'

Implementation:
- 'org.scream3r', name: 'jssc', version: '2.8.0'
- 'org.apache.commons', name: 'commons-lang3', version: '3.0'

Подключить фискальный принтер через COM или USB порт и
установить значение константы **COM_PORT** в классе _**VikiPrintExamples**_:

## VikiPrintExamples
Вызов метода _**main(String[] args)**_ класса _**VikiPrintExamples**_ выполнит базовые команды протокола **[FM16](https://fisgo.pages.dreamkas.ru/pirit_documentation/documentation_fm16_1_2.html#zapros-flagov-statusa-kkt-0x00)**, такие, как:

- Проверка связи с ККТ
- Запрос состояния печатающего устройства
- Чтение даты/времени ККТ
- Запрос флагов статуса ККТ
- Запрос сведений о ККТ
- Печать сервисного чека


## PrinterTest
В тестах класса PrinterTest производится выполнение основных команд протокола **[FM16](https://fisgo.pages.dreamkas.ru/pirit_documentation/documentation_fm16_1_2.html#zapros-flagov-statusa-kkt-0x00)**, такие, как:
- Закрытие / открытие кассовой смены
- Перерегистрация ККТ без замены ФН
- Продажа товара в пакетном режиме 
- Продажа товара в обычном режиме

В тесте продажи проверяется добавление
- Маркированного товара, включая его проверку в ФН
- Штучного товара
- Весового товара



## Поддержка
Для связи с поддержкой данного проекта, отправки сообщений об ошибках,
пожеланий и запросов на изменения можно связаться по почте:
n.ivlev@dreamkas.ru