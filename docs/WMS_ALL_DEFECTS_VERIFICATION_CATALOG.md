# РЕЕСТР ВЕРИФИКАЦИИ ДЕФЕКТОВ WMS (ISD)
**Независимый состязательный аудит (Second-Order Adversarial QA Pass — Stage 2 Calibration)**  
**Базовый коммит:** `82f20d3` | **Режим проверки:** Строго READ-ONLY  
**Принцип верификации:** *Implementation ≠ Verification* (Наличие кода, комментариев или тестов не является доказательством корректности).

> **Официальный детальный отчет:** [`WMS_ADVERSARIAL_VERIFICATION_REPORT.md`](WMS_ADVERSARIAL_VERIFICATION_REPORT.md)  
> **План устранения дефектов (Roadmap):** [`WMS_REMEDIATION_ROADMAP.md`](WMS_REMEDIATION_ROADMAP.md)  
> **Методология разделения доказательств (3-Tier Evidence Model):**
> 1. `[STATIC FACT]` — Непосредственно доказано кодом, аннотациями, схемой БД или файлами без домыслов.
> 2. `[ARCHITECTURAL DEDUCTION]` — Строго следует из логики вызовов и структуры приложения, но не наблюдалось в рантайме.
> 3. `[RUNTIME/LOAD GAP]` — Требует живого прогона (параллельных потоков, сетевых задержек, реальной LLM, нагрузки на память).

---

## 1. Сводная Матрица Второго Порядка (Second-Order Calibration Matrix)

| ID | Исходная Severity | Перекалиброванная Severity | Итоговый Verdict | Доменная область | Статус доказанности и границы обоснования |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **DEF-01** | Critical | **High** | `[DEFECT]` | AI / Безопасность | Утечка токена в Tool Response доказана `[STATIC FACT]`; автономный вызов без человека — `[RUNTIME/LOAD GAP]`. |
| **DEF-02** | Critical | **Critical** | `[DEFECT]` | БД / Учетные данные | Доказан полностью кодом миграции V31 `[STATIC FACT]` (0 Gap). |
| **DEF-03** | High | **High** | `[DEFECT]` | Авторизация / BOLA | Доказан полностью отсутствием проверок владельца `[STATIC FACT]` (0 Gap). |
| **DEF-04** | High | **High** | `[DEFECT]` | Авторизация / BOLA | Доказан полностью отсутствием проверок владельца `[STATIC FACT]` (0 Gap). |
| **DEF-05** | High | **High** | `[DEFECT]` | Аудит / Целостность | Доказан полностью принятием `userId` из тела запроса `[STATIC FACT]` (0 Gap). |
| **DEF-06** | High | **High** | `[DEFECT]` | Утечка данных | Доказан полностью безусловным `findAll()` `[STATIC FACT]` (0 Gap). |
| **DEF-07** | High | **Medium** | `[DEFECT]` | Надежность / DoS | Отсутствие пагинации доказано `[STATIC FACT]`; падение JVM DoS/OOM — `[RUNTIME/LOAD GAP]`. |
| **DEF-08** | High | **Low** | `[RESIDUAL RISK]` | Миграции БД | Исторический артефакт V12 `[STATIC FACT]`; на текущей V36 защищен контрольной суммой Flyway (0 Gap). |
| **DEF-09** | High | **High** | `[DEFECT]` | Конкурентность | Отсутствие `@Version`/лока доказано `[STATIC FACT]`; тайминг чередования гонки — `[RUNTIME/LOAD GAP]`. |
| **DEF-10** | High | **High** | `[DEFECT]` | Конкурентность | Отсутствие `@Version`/лока доказано `[STATIC FACT]`; тайминг чередования гонки — `[RUNTIME/LOAD GAP]`. |
| **DEF-11** | High | **High** | `[DEFECT]` | Схема / Ограничения | Межтоварный конфликт ячейки доказан индексами V33 и V34 `[STATIC FACT]` (0 Gap). |
| **DEF-12** | High | **Medium** | `[DEFECT]` | Доменная логика | Удаление без валидации статуса доказано `[STATIC FACT]`; для заказов с аллокациями FK вызывает 500 ошибку, а не тихое удаление. |
| **DEF-13** | High | **High** | `[DEFECT]` | Стейт-машина WMS | Недостижимость статуса `COMPLETED` доказана детерминированным кодом `[STATIC FACT]` (0 Gap). |
| **DEF-14** | High | **Low** | `[RESIDUAL RISK]` | AI / Сеть | Обернут в try-catch post-ready `[STATIC FACT]`; краш старта опровергнут кодом (0 Gap). |
| **DEF-15** | High | **Medium** | `[DEFECT]` | Транзакции / Сеть | Удержание коннекта HikariCP при SMTP доказано `[STATIC FACT]`; исчерпание пула — `[RUNTIME/LOAD GAP]`. |
| **DEF-16** | High | **High** | `[DEFECT]` | Тесты / QA | Фиктивность ассертов на локальные моки/grep доказана кодом тестов `[STATIC FACT]` (0 Gap). |
| **DEF-17** | Medium | **Medium** | `[DEFECT]` | Авторизация | Отсутствие `@PreAuthorize` и доступ оператора доказаны `[STATIC FACT]` (0 Gap). |
| **DEF-18** | Medium | **Medium** | `[DEFECT]` | Валидация DTO | Пропуск валидации строк коллекции без `@Valid` доказан Jakarta Spec `[STATIC FACT]` (0 Gap). |
| **DEF-19** | Medium | **Low** | `[DEFECT]` | Валидация DTO | Разрешение нулевого объема пополнением `@Min(0)` доказано `[STATIC FACT]` (0 Gap). |
| **DEF-20** | Medium | **Medium** | `[DEFECT]` | Целостность БД | Для User доказан дефект дубликатов `[STATIC FACT]`; для Product закрыт сервисом `existsByBarcodeIgnoreCase` `[STATIC FACT]`. |
| **DEF-21** | Medium | **Low** | `[DEFECT]` | Аудит склада | Логирование остатка до списания доказано порядком вызовов в коде `[STATIC FACT]` (0 Gap). |
| **DEF-22** | Medium | **Low** | `[RESIDUAL RISK]` | Фронтенд / Права | UI подменяется `[STATIC FACT]`; бэкенд строго защищен 403 Forbidden `[STATIC FACT]` (0 Gap). |
| **DEF-23** | Medium | **Low** | `[DEFECT]` | Фронтенд / Конфиг | Жесткий литерал HTTP/8080 доказан кодом `index.js` `[STATIC FACT]` (0 Gap). |
| **DEF-24** | Medium | **Medium** | `[DEFECT]` | AI / Бизнес-логика | Игнорирование флага `isActive` оператора доказано кодом `[STATIC FACT]` (0 Gap). |
| **DEF-25** | Medium | **Low** | `[DEFECT]` | AI / Запросы к БД | Неэффективный `findAll` + Stream доказан `[STATIC FACT]`; исчерпание кучи JVM — `[RUNTIME/LOAD GAP]`. |
| **DEF-26** | Low | **Low** | `[DOCUMENTATION DRIFT]` | Конфигурация | Мертвый роут `/api/operator/**` доказан отсутствием контроллеров `[STATIC FACT]` (0 Gap). |

---

## 2. Реестр Пробелов Верификации (Verification Gap Registry)

Данный реестр формализует границы между статически доказанными дефектами архитектуры и неисследованными эффектами в рантайме. Утверждение о "0 Verification Gaps" в первичном аудите признано методологически неверным.

| ID | Заявленный Runtime/Load Эффект | Статус Статического Доказательства | Пробел Верификации (`[RUNTIME/LOAD GAP]`) | Протокол Доремедиационной Проверки |
| :--- | :--- | :--- | :--- | :--- |
| **DEF-01** | LLM автономно завершает двухфазный цикл удаления без участия человека | Токен подтверждения генерируется и возвращается в открытом виде в строке ответа Tool `[STATIC FACT]` | Поведение Spring AI ChatClient в реальном цикле вызова тулов (Loop Mechanics) с живой моделью не зафиксировано | Интеграционный тест с эмуляцией/вызовом ChatClient и проверкой числа итераций авто-вызова `deleteOrder` |
| **DEF-07** | Гарантированный отказ в обслуживании (DoS / OOM) при запросе больших списков | Эндпоинты возвращают полный `List<T>` через `findAll()` без `Pageable` `[STATIC FACT]` | Фактическое падение JVM с OutOfMemoryError зависит от heap-памяти (`-Xmx`) и объема БД (в демо-БД OOM не воспроизводится) | Нагрузочный тест (JMeter/k6) на коллекциях >100,000 сущностей с замером аллокаций памяти |
| **DEF-09** | Одновременное назначение заказа двум операторам с дублированием Task | Сущность `Order` не имеет `@Version` и блокировок при проверке статуса `[STATIC FACT]` | Точное окно гонки и поведение PostgreSQL при Read Committed на реальном пуле потоков не замерено | Многопоточный тест (Concurrency Test / `CountDownLatch`) с параллельным вызовом `assignOrder` |
| **DEF-10** | Двойное резервирование ячейки и задачи пополнения | Сущность `Replenishment` не имеет `@Version` и блокировок `[STATIC FACT]` | Чередование транзакций в СУБД в многопоточной среде не подтверждено логами рантайма | Многопоточный тест параллельного вызова `assignReplenishment` |
| **DEF-14** | Падение приложения при старте или блокировка готовности при недоступности OpenAI | Метод слушает `ApplicationReadyEvent` и обернут в `try-catch` `[STATIC FACT]` | **Опровергнуто:** сбой сети перехватывается, сервер уже готов к приему трафика | Проверка времени старта приложения с выключенной сетью (замер readiness probe) |
| **DEF-15** | Полный паралич пула соединений БД HikariCP при сетевых задержках SMTP | Метод `@Transactional` синхронно выполняет сетевой SMTP-вызов `[STATIC FACT]` | Истощение пула соединений зависит от concurrency регистраций и SMTP timeouts | Интеграционный тест с искусственной задержкой сокета SMTP (5 сек) и 15 параллельными потоками |
| **DEF-25** | Аварийная остановка сервиса по OutOfMemoryError при вызове AI-инструмента остатков | `findAllByAvailableIsTrue()` выгружает все строки в память и фильтрует Stream `[STATIC FACT]` | Реальное исчерпание кучи на складах обычного масштаба (<50 000 остатков) маловероятно | Профилирование памяти через VisualVM/JProfiler при 50 000 строках таблицы `stocks` |

---

## 3. Детальные Карточки Дефектов с Разделением Доказательств

---

### DEF-01: Утечка токена двухфазного подтверждения в LLM
* **Файлы:**
  * [`AiToolSecurityBoundary.java#L222-L226`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/AiToolSecurityBoundary.java#L222-L226)
  * [`OrderMutatingAiTools.java#L115-L125`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/OrderMutatingAiTools.java#L115-L125)
  * [`ChatbotService.java#L58-L86`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ChatbotService.java#L58-L86)
* **[STATIC FACT]:**
  В методе `requireConfirmation()` при отсутствии токена генерируется строка:
  `"CONFIRMATION REQUIRED: ... To confirm and execute, call this tool with confirmationToken='%s'."`
  Метод `OrderMutatingAiTools.deleteOrder` возвращает эту строку как результат выполнения тула. В `ChatbotService` настроен стандартный `ChatClient` с регистрацией инструментов мутации.
* **[ARCHITECTURAL DEDUCTION]:**
  Токен подтверждения передается в ответе инструмента непосредственно модели. Если пользователь или промпт-инъекция содержит указание «удали и сразу подтверди», модель располагает всеми данными для вызова подтверждающего метода.
* **[RUNTIME/LOAD GAP]:**
  В изолированном окружении не проводился запуск живой LLM для проверки, вызывает ли модель инструмент подтверждения *автономно в том же цикле без явного указания человека*, либо останавливается и переспрашивает оператора. Это зависит от системного промпта и настроек advisor'а Spring AI.
* **Итоговая калибровка:** Критичность: **HIGH** (понижено с Critical, так как автономный байпас без участия человека требует рантайм-подтверждения). Классификация: **`[DEFECT]`** (архитектурная компрометация секрета подтверждения).

---

### DEF-02: Пароли пользователей открытым текстом в комментариях Flyway
* **Файл:** [`V31__seed_warehouse_data_final.sql#L6-L39`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V31__seed_warehouse_data_final.sql#L6-L39)
* **[STATIC FACT]:**
  Строки 6–39 содержат таблицу с открытыми паролями 17 пользователей:
  `-- | supervisor1 | Office%13 | ...`
  `-- | supervisor2 | Dunder@38 | ...`
  Файл закоммичен в Git и включается в итоговый исполняемый JAR.
* **[ARCHITECTURAL DEDUCTION]:**
  Любой субъект с доступом к репозиторию или артефакту получает готовые учетные данные для немедленной компрометации системы под ролью `ROLE_SUPERVISOR`.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Уязвимость доказана статическим осмотром файла.
* **Итоговая калибровка:** Критичность: **CRITICAL**. Классификация: **`[DEFECT]`**.

---

### DEF-03: Уязвимость BOLA / IDOR на заказах
* **Файлы:**
  * [`OrderController.java#L61-L142`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/OrderController.java#L61-L142)
  * [`OrderService.java#L111-L135, L162-L186`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L111-L135)
* **[STATIC FACT]:**
  Методы `getOrderExtendedById`, `updateExtended`, `deleteOrderById` принимают `@PathVariable Long id` и работают с объектом через `orderRepository.findById(id)`. В контроллере и сервисе полностью отсутствуют вызовы `securityFacade.getCurrentUsername()` или сверка владельца заказа.
* **[ARCHITECTURAL DEDUCTION]:**
  Супервайзер A может прочитать, отредактировать состав или удалить заказ Супервайзера B, просто подставив идентификатор `id`.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). В коде нет скрытых AOP-аспектов или фильтров, обеспечивающих изоляцию объектов.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-04: Уязвимость BOLA / IDOR на пополнениях
* **Файл:** [`ReplenishmentService.java#L124-L151, L213-L276`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java#L124-L151)
* **[STATIC FACT]:**
  Методы `cancelReplenishment(Long id)` и `deleteReplenishment(Long id)` загружают запись из репозитория и выполняют мутацию без проверки создателя задачи.
* **[ARCHITECTURAL DEDUCTION]:**
  Любой аутентифицированный супервайзер может сорвать операционную работу коллег, отменив чужие задачи пополнения.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Логика сервиса прямолинейна.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-05: Подделка автора операций в аудит-логе (Actor Spoofing)
* **Файлы:**
  * [`InventoryService.java#L141`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/InventoryService.java#L141)
  * [`InventoryAdjustmentValidator.java#L57`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/validation/InventoryAdjustmentValidator.java#L57)
* **[STATIC FACT]:**
  DTO `AddStockRequest` и `RemoveStockRequest` содержат поле `Long userId`. Сервис выполняет `userRepository.findById(request.userId())` и связывает полученного пользователя с записью `InventoryHistory`.
* **[ARCHITECTURAL DEDUCTION]:**
  Любой пользователь может списать материальные ценности со склада, передав в теле запроса ID другого сотрудника, подделав аудит неотрекаемости (non-repudiation).
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Поток данных от JSON клиента до сохранения в таблицу истории непрерывен.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-06: Утечка данных всех заказов через `GET /api/v1/orders/extended`
* **Файл:** [`OrderService.java#L321-L322`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L321-L322)
* **[STATIC FACT]:**
  Метод `getAllOrdersExtended()` вызывает `orderRepository.findAll()` без условий выборки.
* **[ARCHITECTURAL DEDUCTION]:**
  Эндпоинт раскрывает все заказы всех клиентов и супервайзеров любому пользователю с базовой ролью супервайзера.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Поведение метода `findAll()` безусловно.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-07: Отсутствие пагинации в табличных REST-эндпоинтах
* **Файлы:** Контроллеры `OrderController`, `InventoryController`, `ProductController`, `UserController`.
* **[STATIC FACT]:**
  Все методы получения коллекций возвращают `List<T>` и вызывают `findAll()` без передачи объекта `Pageable`.
* **[ARCHITECTURAL DEDUCTION]:**
  Сложность и объем памяти ответа растут линейно $O(N)$ от размера базы данных, создавая структурный риск при масштабировании.
* **[RUNTIME/LOAD GAP]:**
  Утверждение о «гарантированном отказе в обслуживании (DoS) через OutOfMemoryError» является теоретической экстраполяцией. В условиях демо-базы (до нескольких тысяч строк) JVM без труда справляется с нагрузкой. Падение зависит от размера Heap (`-Xmx`) и не проверялось под нагрузкой.
* **Итоговая калибровка:** Критичность: **MEDIUM** (понижено с HIGH). Классификация: **`[DEFECT]`** (архитектурный дефект контракта API).

---

### DEF-08: Деструктивная миграция `DROP TABLE ... CASCADE`
* **Файл:** [`V12__drop_tables.sql#L1-L12`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V12__drop_tables.sql#L1-L12)
* **[STATIC FACT]:**
  Миграция содержит операторы `DROP TABLE IF EXISTS ... CASCADE`. Текущая версия схемы в проекте — `V36`.
* **[ARCHITECTURAL DEDUCTION]:**
  В действующей базе данных Flyway никогда не запустит V12 повторно из-за фиксации контрольной суммы. На чистой установке V12 удаляет ранние таблицы прототипа перед развертыванием финальной схемы.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Факт невозможности повторного выполнения на V36 доказан механизмом Flyway.
* **Итоговая калибровка:** Критичность: **LOW** (понижено с HIGH/MEDIUM). Классификация: **`[RESIDUAL RISK]`** (исторический дефект версионирования).

---

### DEF-09: Гонка двойного назначения в `assignOrder`
* **Файл:** [`OrderService.java#L206-L247`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L206-L247)
* **[STATIC FACT]:**
  Сущность `Order` не имеет поля `@Version`. Метод `assignOrder` считывает заказ простым `findById`, проверяет статус в памяти Java и вызывает создание задач `Task` до фиксации статуса в БД.
* **[ARCHITECTURAL DEDUCTION]:**
  При одновременном нажатии кнопки назначения двумя пользователями оба потока увидят статус `CREATED`, создадут дубликаты записей `Task` и вызовут конкурирующие апдейты.
* **[RUNTIME/LOAD GAP]:**
  Многопоточный тест на чередование транзакций (interleaving) под управлением PostgreSQL не запускался; ширина окна гонки и поведение на уровне блокировок строк СУБД выведены дедуктивно из кода.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`** (отсутствие concurrency guards подтверждено кодом; эмпирический interleaving — `[RUNTIME/LOAD GAP]`).

---

### DEF-10: Гонка двойного резервирования в `assignReplenishment`
* **Файл:** [`ReplenishmentService.java#L170-L210`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ReplenishmentService.java#L170-L210)
* **[STATIC FACT]:**
  Сущность `Replenishment` не имеет поля `@Version`. Блокировка `PESSIMISTIC_WRITE` при назначении не запрашивается.
* **[ARCHITECTURAL DEDUCTION]:**
  Параллельные запросы могут одновременно перевести задачу в `ASSIGNED`, порождая дублирующиеся складские перемещения.
* **[RUNTIME/LOAD GAP]:**
  Эмпирический замер гонки в рантайме не проводился (`[RUNTIME/LOAD GAP]`).
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-11: Межтоварный конфликт ячейки назначения при пополнении
* **Файлы:**
  * [`V33__add_unique_index_active_replenishments.sql#L3`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/resources/db/migration/V33__add_unique_index_active_replenishments.sql#L3)
  * [`V34__add_unique_active_location_stock.sql#L3`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/resources/db/migration/V34__add_unique_active_location_stock.sql#L3)
* **[STATIC FACT]:**
  Индекс `V33` построен по кортежу `(product_id, destination_location_id)`. Индекс `V34` построен строго по `stock(location_id) WHERE quantity > 0`.
* **[ARCHITECTURAL DEDUCTION]:**
  `V33` пропускает одновременное создание двух пополнений для *разных* товаров в одну и ту же свободную ячейку. При завершении второго пополнения вставка остатка завершится ошибкой нарушения уникальности `V34`, вызвав аварийный откат транзакции сборщика.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Конфликт схемы очевиден из определений индексов DDL.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-12: Физическое удаление активных и выполняемых заказов
* **Файл:** [`OrderService.java#L162-L186`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/OrderService.java#L162-L186)
* **[STATIC FACT]:**
  В методе `deleteOrderById` отсутствует доменная проверка `order.getStatus()`. Выполняется прямой вызов `orderRepository.delete(order)`. При этом таблица `processes` (аллокации) содержит внешний ключ `task_id REFERENCES tasks(id) NOT NULL` (без `ON DELETE CASCADE`).
* **[ARCHITECTURAL DEDUCTION]:**
  Если заказ находится в стадии сборки (`ASSIGNED`/`IN_PROGRESS`) и имеет аллокации, каскадное удаление задач `Task` наталкивается на ограничение целостности внешнего ключа в БД, вызывая откат транзакции и необработанную ошибку HTTP 500. Заказ не удаляется тихо под ногами оператора, но API аварийно завершается. Для заказов без аллокаций происходит деструктивное удаление независимо от статуса.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Механизм внешних ключей и отсутствие валидации доказаны структурой БД и кодом сервиса.
* **Итоговая калибровка:** Критичность: **MEDIUM** (понижено с High: тихое удаление выполняемых заказов предотвращается СУБД, дефект выражается в отсутствии бизнес-валидации и необработанном сбое 500). Классификация: **`[DEFECT]`**.

---

### DEF-13: Преждевременное снижение статуса заказа до `PARTIALLY_COMPLETED`
* **Файл:** [`PickingOperatorStrategy.java#L133`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/src/main/java/com/isd/wms/strategy/PickingOperatorStrategy.java#L133)
* **[STATIC FACT]:**
  В методе `handleOrderCompletion` ветка для не-отмененных заказов содержит безусловную строку:
  `order.setStatus(OrderStatus.PARTIALLY_COMPLETED);`
  Код, устанавливающий `OrderStatus.COMPLETED`, в данном методе полностью отсутствует.
* **[ARCHITECTURAL DEDUCTION]:**
  Даже при 100% успешном сборе всех строк заказ переходит в статус частичного завершения, блокируя автоматический переход к отгрузке.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Логика метода детерминирована.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-14: Слепая ре-индексация базы в OpenAI при каждом старте
* **Файл:** [`ProductVectorIndexer.java#L46-L65`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/ProductVectorIndexer.java#L46-L65)
* **[STATIC FACT]:**
  Метод `indexAllProducts()` слушает `ApplicationReadyEvent`. Вызов `vectorStore.add(documents)` находится внутри блока:
  `try { vectorStore.add(documents); } catch (Exception e) { log.warn(...); }`
* **[ARCHITECTURAL DEDUCTION]:**
  1. `ApplicationReadyEvent` наступает, когда встроенный веб-сервер **уже запущен и слушает порт**.
  2. Блок `try-catch` перехватывает любые исключения сетевого сбоя или отсутствия ключа OpenAI, выводя `log.warn`.
  3. **Приложение НЕ падает и НЕ блокирует готовность к обслуживанию**.
* **[RUNTIME/LOAD GAP]:**
  Утверждение предыдущего аудита о том, что «отсутствие ключа OpenAI или сети приводит к падению старта приложения» — **ОПРОВЕРГНУТО КОДОМ**. Имеется лишь нежелательный фоновый вызов, создающий задержку в фоновом потоке.
* **Итоговая калибровка:** Критичность: **LOW** (понижено с HIGH/MEDIUM). Классификация: **`[RESIDUAL RISK]`** (архитектурный Code Smell).

---

### DEF-15: Синхронная отправка email внутри открытой транзакции БД
* **Файлы:**
  * [`UserService.java#L67, L160`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java#L67,L160)
  * [`EmailService.java#L50-L74`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/EmailService.java#L50-L74)
* **[STATIC FACT]:**
  Метод `registerUser` помечен `@Transactional` и синхронно вызывает `emailService.sendVerificationEmail(...)`. `EmailService` не имеет аннотации `@Async`.
* **[ARCHITECTURAL DEDUCTION]:**
  Транзакция БД удерживает физическое соединение из пула HikariCP на все время сетевого диалога по протоколу SMTP.
* **[RUNTIME/LOAD GAP]:**
  Фактическое исчерпание пула коннектов (всех 10 соединений) зависит от частоты регистраций и таймаута SMTP-сокета. Замер истощения пула под нагрузкой не проводился.
* **Итоговая калибровка:** Критичность: **MEDIUM** (понижено с HIGH). Классификация: **`[DEFECT]`** (анти-паттерн удержания транзакции доказан кодом; исчерпание пула — `[RUNTIME/LOAD GAP]`).

---

### DEF-16: Фиктивные тесты DEF-02 и DEF-05 (проверка моков и grep)
* **Файлы:**
  * [`wmsFront/test/def02_user_id_dataflow.test.js#L65-L88`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def02_user_id_dataflow.test.js#L65-L88)
  * [`wmsFront/test/def05_conflict_event.test.js#L143-L165`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/test/def05_conflict_event.test.js#L143-L165)
* **[STATIC FACT]:**
  В тесте DEF-02 тестируются фиктивные функции `simulateAddStock`, объявленные внутри самого тестового файла. Файлы репозитория читаются через `fs.readFileSync` для поиска подстрок регулярными выражениями.
* **[ARCHITECTURAL DEDUCTION]:**
  Тесты создают фиктивную зеленую отчетность в CI/CD, не тестируя реальные компоненты фронтенда.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Фиктивность проверок очевидна из тела тестов.
* **Итоговая калибровка:** Критичность: **HIGH**. Классификация: **`[DEFECT]`**.

---

### DEF-17: Отсутствие `@PreAuthorize` на чтении пополнений
* **Файл:** [`ReplenishmentController.java#L33-L70`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/ReplenishmentController.java#L33-L70)
* **[STATIC FACT]:**
  Методы `getAllReplenishments` и `getReplenishmentById` не содержат аннотаций `@PreAuthorize`. В `SecurityConfig.java#L52` маршрут разрешен для `ROLE_OPERATOR`.
* **[ARCHITECTURAL DEDUCTION]:**
  Любой оператор имеет возможность мониторить глобальные планы пополнения склада.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`).
* **Итоговая калибровка:** Критичность: **MEDIUM**. Классификация: **`[DEFECT]`**.

---

### DEF-18: Отсутствие каскадной `@Valid` в `ExtendedOrderCreateRequest`
* **Файл:** [`ExtendedOrderCreateRequest.java#L18-L23`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/order/extended/ExtendedOrderCreateRequest.java#L18-L23)
* **[STATIC FACT]:**
  Поле списка `items` не помечено аннотацией Jakarta `@Valid` (использован `@NonNull` Lombok).
* **[ARCHITECTURAL DEDUCTION]:**
  Валидация дочерних элементов коллекции (отрицательные количества, нулевые ID) игнорируется валидатором Spring. Значения с отрицательным количеством строк сохраняются в БД.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Поведение стандарта Bean Validation специфицировано.
* **Итоговая калибровка:** Критичность: **MEDIUM**. Классификация: **`[DEFECT]`**.

---

### DEF-19: Разрешено создание задач пополнения с нулевым объемом
* **Файл:** [`ReplenishmentCreateRequest.java#L15`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/dto/replenishment/ReplenishmentCreateRequest.java#L15)
* **[STATIC FACT]:**
  Поле `requestedQuantity` аннотировано `@Min(0)`.
* **[ARCHITECTURAL DEDUCTION]:**
  Клиент может передавать `0`, создавая пустые зомби-задачи в очереди.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`).
* **Итоговая калибровка:** Критичность: **LOW** (понижено с MEDIUM). Классификация: **`[DEFECT]`**.

---

### DEF-20: Регистрозависимые уникальные индексы (Разделение User vs Product)
* **Файлы:**
  * [`V1__init_schema.sql#L10`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V1__init_schema.sql#L10)
  * [`ProductService.java#L67`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ProductService.java#L67)
  * [`UserService.java#L97-L98`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java#L97-L98)
* **[STATIC FACT]:**
  1. В `V1` и `V3` уникальные индексы не используют функцию `LOWER(...)`.
  2. В `ProductService.java#L67` явно вызывается:
     `if (productRepository.existsByBarcodeIgnoreCase(barcode)) throw ...;`
  3. В `UserService.java#L97-L98` и `CustomUserDetailsService.java#L25` вызываются точные регистрозависимые методы `findByUsername` и `findByEmail`.
* **[ARCHITECTURAL DEDUCTION]:**
  - **Для User:** Защиты нет ни на уровне приложения, ни в БД. Учетные записи `supervisor` и `Supervisor` будут зарегистрированы как две независимые сущности с разными паролями (`[DEFECT]`).
  - **Для Product:** Защита от коллизий регистров полностью реализована на уровне сервиса (`existsByBarcodeIgnoreCase`). Дефект в БД существует только как остаточный риск при прямых SQL-инъекциях/импортах (`[RESIDUAL RISK]`).
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Разделение доказано проверкой исходного кода сервисов.
* **Итоговая калибровка:** Критичность: **MEDIUM**. Классификация: **`[DEFECT]`** (единый вердикт по уязвимости дубликатов учетных записей User; для товаров уровень риска классифицирован как Residual Risk).

---

### DEF-21: Рассинхронизация аудита сбора (запись до списания)
* **Файл:** [`AllocationExecutionService.java#L256-L270`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/AllocationExecutionService.java#L256-L270)
* **[STATIC FACT]:**
  Вызов `recordPickingHistory()` в `PickingOperatorStrategy#complete` предшествует списанию остатка и фиксирует исходное значение `stock.getQuantity()`.
* **[ARCHITECTURAL DEDUCTION]:**
  Поле истории содержит снимок остатка полки до проведения пикинга, искажая тайминг аудита внутри транзакции.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Порядок вызовов зафиксирован в коде.
* **Итоговая калибровка:** Критичность: **LOW** (понижено с Medium: дефект выражается лишь во внутреннем снимке остатка до списания в рамках единой неделимой транзакции). Классификация: **`[DEFECT]`**.

---

### DEF-22: Подмена роли через `sessionStorage` на фронтенде
* **Файлы:**
  * [`wmsFront/src/stores/auth.js#L58-L62`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/stores/auth.js#L58-L62)
  * [`SecurityConfig.java`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/config/SecurityConfig.java)
* **[STATIC FACT]:**
  Хранилище `auth.js` считывает роль из `sessionStorage` без запроса к `/api/auth/me`. Однако Spring Security на бэкенде проверяет claims подписанного JWT-токена на каждом HTTP-запросе.
* **[ARCHITECTURAL DEDUCTION]:**
  Подмена роли в DevTools открывает супервайзерские меню в браузере, но все попытки совершить мутации или прочитать защищенные данные отклоняются бэкендом с HTTP 403 Forbidden.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Это косметический сбой отображения UI, а не эскалация привилегий в данных.
* **Итоговая калибровка:** Критичность: **LOW** (понижено с MEDIUM). Классификация: **`[RESIDUAL RISK]`** (единый вердикт).

---

### DEF-23: Жестко зашитый протокол `http://` и порт `8080` в Base URL фронтенда
* **Файл:** [`wmsFront/src/api/index.js#L14`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsFront/src/api/index.js#L14)
* **[STATIC FACT]:**
  Константа задана литералом `const API_BASE_URL = 'http://${currentHostname}:8080/api'`.
* **[ARCHITECTURAL DEDUCTION]:**
  Препятствует эксплуатации фронтенда за обратным прокси по протоколу HTTPS.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`).
* **Итоговая калибровка:** Критичность: **LOW** (понижено с MEDIUM). Классификация: **`[DEFECT]`**.

---

### DEF-24: Назначение заказов деактивированным операторам через AI
* **Файл:** [`WarehouseAiTools.java#L86`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/WarehouseAiTools.java#L86)
* **[STATIC FACT]:**
  Фильтр операторов проверяет только `u.getUserRole().name().equals("ROLE_OPERATOR")` и не содержит предиката `Boolean.TRUE.equals(u.getIsActive())`.
* **[ARCHITECTURAL DEDUCTION]:**
  AI-инструмент может назначить задачи деактивированному сотруднику.
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`). Предикат фильтрации статически очевиден.
* **Итоговая калибровка:** Критичность: **MEDIUM**. Классификация: **`[DEFECT]`**.

---

### DEF-25: Полное сканирование всех остатков склада в память JVM через AI
* **Файл:** [`InventoryMutatingAiTools.java#L90-L95`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/ai/InventoryMutatingAiTools.java#L90-L95)
* **[STATIC FACT]:**
  Вызывается `stockRepository.findAllByAvailableIsTrue()` с последующей фильтрацией через `.stream().filter(...)`.
* **[ARCHITECTURAL DEDUCTION]:**
  Вместо целевого SQL-запроса выгружаются все доступные остатки склада.
* **[RUNTIME/LOAD GAP]:**
  Заявление об «исчерпании кучи JVM» является теоретической экстраполяцией. В условиях небольшого склада накладные расходы незначительны.
* **Итоговая калибровка:** Критичность: **LOW** (понижено с MEDIUM). Классификация: **`[DEFECT]`** (неэффективный запрос к БД; исчерпание памяти — `[RUNTIME/LOAD GAP]`).

---

### DEF-26: Мертвый URL-паттерн `/api/operator/**` в SecurityConfig
* **Файл:** [`SecurityConfig.java#L54`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/config/SecurityConfig.java#L54)
* **[STATIC FACT]:**
  Паттерн `/api/operator/**` объявлен в конфигурации Spring Security, но ни один контроллер не маппится на этот префикс.
* **[ARCHITECTURAL DEDUCTION]:**
  Мертвое правило. Никакого вектора атаки не открывает (запросы получают 404).
* **[RUNTIME/LOAD GAP]:**
  Отсутствует (`0 GAP`).
* **Итоговая калибровка:** Критичность: **LOW**. Классификация: **`[DOCUMENTATION DRIFT]`** (единый вердикт).
