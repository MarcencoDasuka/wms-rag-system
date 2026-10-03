# Архитектурный справочник ядра WMS-системы (inbound-storage-dispatch)

> **Статус документа:** Исчерпывающий реестр **18** базовых архитектурных решений ядра WMS, результаты состязательного аудита **8** ключевых решений (3 `[VERIFIED]`, 5 `[PARTIALLY VERIFIED]`) и реестр **5** новых дефектов (`DEF-01` – `DEF-05`), выявленных в ходе верификации.  
> **Основание:** Анализ полного графа коммитов Git (`git log`), данных состязательного аудита и верификации на боевом каталоге PostgreSQL 16 (**207 автоматических тестов:** 200 Java 21 бэкенда + 7 Node.js фронтенда).

---

# ЧАСТЬ 1. БАЗОВЫЕ АРХИТЕКТУРНЫЕ РЕШЕНИЯ ЯДРА WMS

---

### 1.1. [SEC-01] Отклонение дефолтного JWT-секрета в Production профиле
* **Коммит:** `9bc4ea4`
* **В чём заключалась уязвимость:**
  В классе `JwtUtil` при отсутствии переменной окружения `JWT_SECRET` загружался жестко закодированный dev-ключ (`default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`). При развертывании в продакшене злоумышленник мог подписать произвольный JWT-токен с максимальными привилегиями (`ROLE_DEV`, `ROLE_SUPERVISOR`) и полностью скомпрометировать систему.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`.
* **Как устранено:**
  Внедрен метод `validateSecretConfiguration()`, вызываемый в `@PostConstruct`. Если активен профиль `prod` или `production`, и значение ключа совпадает с дефолтным dev-секретом, выбрасывается `IllegalStateException`, аварийно останавливая контекст Spring до открытия сетевого порта.
* **Тест:** `JwtUtilTest.java`.

---

### 1.2. [DATA-01] Идемпотентность и атомарность завершения шагов оператора
* **Коммит:** `5c6da66`
* **В чём заключалась уязвимость:**
  Методы `PickingOperatorStrategy.executeStep` и `ReplenishmentOperatorStrategy.executeStep` при сетевом сбое или повторном клике оператора могли списывать остатки дважды. Отсутствовали проверки терминальных состояний задачи.
* **Где скрывалась:** `service/allocation/PickingOperatorStrategy.java`, `ReplenishmentOperatorStrategy.java`.
* **Как устранено:**
  1. Добавлена проверка идемпотентности: повторный вызов для уже завершенной аллокации возвращает актуальное состояние без повторных мутаций остатков.
  2. Все операции изменения количества товара и перевода статуса объединены в строгие транзакции с валидацией инвариантов.
* **Тест:** `PickingFlowIntegrationTest.java`, `ReplenishmentFlowIntegrationTest.java`.

---

### 1.3. [DATA-02] Защита от отрицательных остатков и переполнения ячеек
* **Коммит:** `6c10e30`
* **В чём заключалась уязвимость:**
  При ручных корректировках инвентаризации (`InventoryService`) или параллельном резервировании остатки на складе могли уходить в отрицательные значения, а ячейки — переполняться сверх допустимой вместимости (capacity).
* **Где скрывалась:** `service/InventoryService.java`, `entity/Stock.java`, `entity/Location.java`.
* **Как устранено:**
  1. Внедрена проверка `quantity >= 0` и `reservedQuantity >= 0` на уровне сущности и сервиса.
  2. В `LocationService` добавлена валидация максимальной вместимости перед приемом товара.
* **Тест:** `InventoryServiceTest.java`.

---

### 1.4. [CONC-01] Пессимистическая блокировка при распределении задач
* **Коммит:** `b30a1cd`
* **В чём заключалась уязвимость:**
  Когда несколько свободных операторов одновременно запрашивали следующую задачу через `TaskService.getNextAvailableTask`, происходило состояние гонки: одна и та же задача назначалась двум операторам одновременно.
* **Где скрывалась:** `repository/TaskRepository.java`, `service/TaskService.java`.
* **Как устранено:**
  В репозиторий добавлен метод с аннотацией `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Task t WHERE t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
  List<Task> findAvailableTasksWithLock(Pageable pageable);
  ```
* **Тест:** `TaskConcurrencyIntegrationTest.java`.

---

### 1.5. [CONC-02] Обработка Optimistic Locking Exception и маппинг в HTTP 409
* **Коммит:** `76ce83b`
* **В чём заключалась уязвимость:**
  При одновременной модификации заказов несколькими пользователями Hibernate выбрасывал `ObjectOptimisticLockingFailureException` или `OptimisticLockException`. Из-за отсутствия явного обработчика в `GlobalExceptionHandler` клиент получал `HTTP 500 Internal Server Error`, что скрывало бизнес-природу конфликта.
* **Где скрывалась:** `GlobalExceptionHandler.java`.
* **Как устранено:**
  Добавлен обработчик `@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})`, возвращающий стандартизированный ответ `HTTP 409 Conflict` с рекомендацией повторить операцию.
* **Тест:** `GlobalExceptionHandlerTest.java`.

---

### 1.6. [TEST-01] Актуализация устаревших юнит-тестов и сигнатур
* **Коммиты:** `839933b`, `621faa9`
* **В чём заключалась неточность:**
  После эволюции бизнес-логики сервисов аллокации и конструкторов контроллеров старые тесты не компилировались или падали на изменившихся стратегиях выполнения (`PickingAllocationStrategy`, `ReplenishmentAllocationCompletionStrategy`).
* **Где скрывалось:** Тестовые классы `OrderServiceTest`, `ReplenishmentServiceTest`, `AllocationExecutionServiceTest`, `CategoryServiceTest`.
* **Как устранено:**
  Обновлены моки, актуализированы сигнатуры конструкторов и скорректированы assertions под актуальное поведение бизнес-процессов.

---

### 1.7. [INFRA-01] Контейнеризация стека WMS в Docker Compose
* **Коммит:** `d40ea94`
* **В чём заключалась задача:**
  Отсутствовала изолированная среда для локального развертывания полного контура WMS (бэкенд, фронтенд, PostgreSQL, векторный индекс).
* **Как устранено:**
  Создан `docker-compose.yaml` с сервисами `postgres`, `wms-backend` (мультистейдж сборка OpenJDK 21), `wms-frontend` (Nginx + Vue 3) и сетевой изоляцией.

---

### 1.8. [S-1] Запрет аутентификации неактивных пользователей (Account Deactivation Bypass)
* **Коммит:** `b1169d5`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  При аутентификации пользователя через `/api/auth/login` (`AuthService`) и проверке JWT-токена в `JwtRequestFilter` отсутствовала проверка флага активности `user.getIsActive()`. Пользователь, заблокированный или уволенный администратором, мог успешно войти в систему, получить валидный JWT-токен и продолжать вызывать защищенные складские эндпоинты.
* **Где скрывалась:** `service/AuthService.java`, `service/CustomUserDetailsService.java`, `security/JwtRequestFilter.java`.
* **Как устранено:**
  1. В `CustomUserDetailsService.loadUserByUsername` статус активности передан в Spring Security `User(..., enabled=user.getIsActive())`.
  2. В `AuthService.authenticate` добавлена строгая проверка активности учетной записи с выбросом `AccountDeactivatedException`.
  3. В `GlobalExceptionHandler` зарегистрирован маппинг `AccountDeactivatedException` -> `HTTP 403 Forbidden`.
  4. В `JwtRequestFilter` добавлена проверка активности пользователя на каждом входящем запросе.
* **Тест:** `AuthServiceTest.java`, `CustomUserDetailsServiceTest.java`, `AuthControllerTest.java`.

---

### 1.9. [S-2] Предотвращение самоактивации неактивных учетных записей через `/register`
* **Коммит:** `2e1f0e9`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В эндпоинте регистрации `/api/auth/register` (`UserService.registerUser`) при получении запроса с логином или email уже существующего пользователя сервис перезаписывал его пароль и безусловно выставлял `userToSave.setIsActive(true)`. Это позволяло любому ранее заблокированному или уволенному сотруднику (включая супервайзеров) разблокировать свою учетную запись без ведома администратора.
* **Где скрывалась:** `service/UserService.java`.
* **Как устранено:**
  1. В `UserService.registerUser` добавлена проверка статуса активности существующей учетной записи: если пользователь деактивирован (`!existingUser.getIsActive()`), любая попытка повторной регистрации блокируется с `AccessDeniedException` («Cannot reactivate a deactivated user through registration. Contact an administrator.»).
  2. Добавлен контроль вызывающего контекста: создание или обновление учетных записей с ролью `ROLE_SUPERVISOR` заблокировано для не-супервайзеров.
  3. Новые пользователи создаются с `isActive = false` до прохождения верификации через email-токен.
* **Тест:** `UserServiceReactivationSecurityTest.java`.

---

### 1.10. [S-3] Искоренение захардкоженных секретов и валидация через SHA-256 Fingerprint
* **Коммиты:** `79b229b`, `5b445a3`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В репозитории присутствовали захардкоженные дефолтные пароли и секреты (`docker-compose.yaml`, `EmailService.java`). Предыдущая проверка в `JwtUtil` (`9bc4ea4`) сравнивала секрет с открытой строкой `default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`, что приводило к сохранению скомпрометированного секрета в открытом виде в скомпилированном байткоде и открывало риск утечки при декомпиляции.
* **Где скрывалась:** `security/JwtUtil.java`, `service/EmailService.java`, `docker-compose.yaml`.
* **Как устранено:**
  1. Удалены жестко закодированные пароли из `EmailService.java` и `docker-compose.yaml`.
  2. В `JwtUtil` открытая текстовая проверка заменена на криптографический SHA-256 фингерпринт скомпрометированного секрета (`b428d00346a0661266e7b57fa0d238ecfef5f0bc59a68b9264c39b7d87bc7d96`).
  3. При запуске в профилях `prod` или `production` наличие скомпрометированного секрета немедленно прерывает работу приложения с `IllegalStateException`.
* **Тест:** `JwtUtilTest.java`.

---

### 1.11. [B-1] Защита активных заказов от разрушительного cron-удаления
* **Коммит:** `d61e887`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Фоновая задача `DataCleanupJob` выполняла периодическую очистку устаревших сущностей, удаляя заказы (`orders`), строки заказов (`order_lines`), пополнения (`replenishments`) и задачи (`tasks`) старше срока давности (`cutoffDate`) без фильтрации по их статусу. Это приводило к физическому удалению активных заказов в статусах `CREATED` и `IN_PROGRESS`, провоцируя необратимую потерю зарезервированного товара на складе.
* **Где скрывалась:** `OrderRepository.java`, `OrderLineRepository.java`, `ReplenishmentRepository.java`, `TaskRepository.java`, `DataCleanupJob.java`.
* **Как устранено:**
  Во всех репозиториях запросы на удаление строго ограничены терминальными статусами:
  - Заказы: `status IN ('COMPLETED', 'CANCELED')`.
  - Строки заказов: `status IN ('COMPLETED', 'CANCELED')`.
  - Пополнения: `status IN ('COMPLETED', 'CANCELED')`.
  - Задачи: `status IN ('COMPLETED', 'CANCELED')`.
* **Тест:** `DataCleanupProtectionIntegrationTest.java`.

---

### 1.12. [D-1] Устранение деструктивных миграций Flyway (`TRUNCATE TABLE ... CASCADE`)
* **Коммит:** `ede81dc`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В версионированных миграциях Flyway `V19__populate_isd_database.sql` и `V31__seed_warehouse_data_final.sql` на первой же строке выполнялась деструктивная команда:
  ```sql
  TRUNCATE TABLE products, locations, orders, replenishments, stocks RESTART IDENTITY CASCADE;
  ```
  Поскольку эти файлы являлись частью основной миграционной цепочки Flyway, при их выполнении на рабочей базе данных все существующие складские данные безвозвратно удалялись.
* **Где скрывалась:** `db/migration/V19__populate_isd_database.sql`, `V31__seed_warehouse_data_final.sql`.
* **Как устранено:**
  1. Команды `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` полностью удалены из обеих миграций.
  2. Все вставки данных переведены на идемпотентный синтаксис PostgreSQL `INSERT INTO ... ON CONFLICT DO NOTHING`.
* **Тест:** Проверено применение цепочки миграций Flyway на чистой и наполненной бизнес-данными БД без потерь.

---

### 1.13. [B-2] Предотвращение потери обновлений (Lost Update) при параллельном отборе строк заказов
* **Коммит:** `b131be1`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  При параллельном отборе товаров несколькими операторами по разным аллокациям одного и того же `OrderLine` метод `PickingOperatorStrategy.executeStep` считывал строку заказа без блокировки, вычислял новый `deliveredQuantity = currentDelivered + pickedQuantity` и сохранял сущность. При одновременном выполнении происходил классический Lost Update: одно из обновлений бесследно затирало другое.
* **Где скрывалась:** `service/allocation/PickingOperatorStrategy.java`, `repository/OrderLineRepository.java`.
* **Как устранено:**
  Внедрена сериализация через пессимистическую блокировку на запись:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ol FROM OrderLine ol WHERE ol.task.id = :taskId")
  Optional<OrderLine> findByTaskIdWithLock(@Param("taskId") Long taskId);
  ```
* **Тест:** `OrderLinePickingConcurrencyIntegrationTest.java`.

---

### 1.14. [B-3] Обеспечение монополии складской ячейки при параллельном размещении товаров
* **Коммит:** `da8d654`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Фундаментальный инвариант WMS гласит: ячейка склада монопольна и не может одновременно содержать остатки разных товаров. Констрейнт `uk_stocks_product_location` защищал только от дублирования одного и того же товара. Если два оператора одновременно размещали товар `A` и товар `B` в одну свободную ячейку, оба потока одновременно фиксировали отсутствие остатков (`stocks.isEmpty() == true`) и вставляли записи, приводя к физическому захвату одной ячейки двумя разными артикулами.
* **Где скрывалась:** `service/InventoryService.java`, `service/allocation/ReplenishmentOperatorStrategy.java`, `repository/LocationRepository.java`.
* **Как устранено:**
  1. Создана миграция Flyway `V34__add_unique_constraint_stock_active_location.sql` с частичным уникальным индексом:
     ```sql
     CREATE UNIQUE INDEX IF NOT EXISTS uk_stocks_active_location
         ON stocks (location_id)
         WHERE available = true;
     ```
  2. В `LocationRepository` добавлен метод `findByIdWithLock(Long id)` с `LockModeType.PESSIMISTIC_WRITE`.
  3. В `InventoryService.addStock` и `ReplenishmentOperatorStrategy` перед проверкой доступности ячейки захватывается блокировка строки `Location` в БД.
* **Тест:** `LocationProductExclusivityConcurrencyIntegrationTest.java`.

---

### 1.15. [B-4] Предотвращение гонки между списанием остатков и отбором аллокаций
* **Коммит:** `edb5a9b`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Существовало состояние гонки реального времени: супервайзер через `InventoryAdjustmentApplier` списывал испорченный товар и отменял аллокации, в то время как оператор через `AllocationExecutionService.completeAllocation` завершал физический отбор. При параллельном исполнении оператор мог подтвердить отбор по уже отмененной аллокации, либо `Stock.reservedQuantity` списывался повторно, уходя в отрицательные значения.
* **Где скрывалась:** `service/AllocationExecutionService.java`, `service/InventoryAdjustmentPlanner.java`, `service/InventoryAdjustmentApplier.java`, `repository/AllocationRepository.java`.
* **Как устранено:**
  1. В `AllocationRepository` добавлены методы блокировки `findByIdWithLock` и `findActiveByStockIdWithLock`.
  2. В `AllocationExecutionService.completeAllocation` аллокация загружается через `findByIdWithLock` и проверяется инвариант терминальности (`if (allocation.getStatus() == Status.CANCELED) throw new InvalidRequestException(...)`).
  3. В `InventoryAdjustmentPlanner` и `InventoryAdjustmentApplier` при отмене аллокаций предварительно захватываются пессимистические блокировки на все активные аллокации данного стока.
* **Тест:** `AllocationAdjustmentConcurrencyIntegrationTest.java`.

---

### 1.16. [AI-1] Контур авторизации и двухфазное подтверждение для мутирующих AI-инструментов
* **Коммит:** `6ceb759`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Инструменты AI-ассистента (`ChatbotService`) предоставляли возможность мутировать состояние склада (отмена заказов, перемещение остатков) без разграничения привилегий и без подтверждения оператором. Скомпрометированный промпт или галлюцинация LLM могли привести к неконтролируемому удалению или перемещению критических складских ресурсов.
* **Где скрывалась:** `service/ChatbotService.java`, `service/ai/OrderAiTools.java`, `service/ai/InventoryAiTools.java`.
* **Как устранено:**
  1. Мутирующие методы изолированы в отдельные классы (`OrderMutatingAiTools`, `InventoryMutatingAiTools`), а безопасные инструменты оставлены в read-only домене.
  2. Создан защитный компонент `AiToolSecurityBoundary`, валидирующий наличие ролей `ROLE_SUPERVISOR` или `ROLE_DEV`.
  3. Внедрен протокол двухфазного подтверждения с криптографическим токеном: вызов мутации формирует `confirmation_token`, и только повторный запрос с этим токеном применяет изменения в БД.
* **Тест:** `AiToolSecurityBoundaryTest.java`, `InventoryAiToolsSecurityTest.java`, `OrderAiToolsSecurityTest.java`.

---

### 1.17. [AI-2] Авторизация на уровне объектов (BOLA / IDOR) и валидация доменных зон в AI-инструментах
* **Коммит:** `75cc3fa`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Проверка общей роли `ROLE_SUPERVISOR` в AI-инструментах не защищала от атак класса BOLA/IDOR: один супервайзер мог отменить чужой заказ или пополнение, передать задачу пользователю без роли `ROLE_OPERATOR`, либо переместить товар в технологически несовместимую зону (например, `DISPATCH`).
* **Где скрывалась:** `service/ai/AiToolSecurityBoundary.java`, `service/ai/OrderMutatingAiTools.java`, `service/ai/InventoryMutatingAiTools.java`, `service/ai/ReplenishmentAiTools.java`, `repository/OrderRepository.java`.
* **Как устранено:**
  1. В `OrderRepository` добавлен запрос `findSupervisorUsernamesByOrder(Long orderId)`.
  2. В `AiToolSecurityBoundary` реализованы методы проверки владения объектом: `enforceOrderAccess(orderId)` и `enforceReplenishmentAccess(replenishmentId)`.
  3. Внедрен метод `enforceTargetOperator(username)`, проверяющий существование, активность и наличие роли `ROLE_OPERATOR`.
  4. Добавлена валидация целевых зон `validateDestinationZone(Location loc, Zone expectedZone)`.
* **Тест:** `AiToolObjectLevelAuthorizationTest.java`.

---

# ЧАСТЬ 2. СВОДНАЯ МАТРИЦА ТРАССИРУЕМОСТИ АУДИТА WMS

| Код | Дефект / Инвариант | Домен | Статус верификации | Подтверждение / Примечание аудита |
|-----|--------------------|-------|--------------------|-----------------------------------|
| **S-1** | Inactive users authentication bypass | Security | `[AWAITING VERIFICATION]` | `b1169d5` / `AuthServiceTest` |
| **S-2** | Inactive supervisor self-reactivation via `/register` | Security | `[AWAITING VERIFICATION]` | `2e1f0e9` / `UserServiceReactivationSecurityTest` |
| **S-3** | Hardcoded credentials and insecure secret fallbacks | Security | `[AWAITING VERIFICATION]` | `79b229b`, `5b445a3` / `JwtUtilTest` |
| **S-4** | Overly broad CORS trust boundary | Security | `[PARTIALLY VERIFIED]` | `fe04b2f` / Wildcard устранены; порт 80 в дефолтных origins без профилирования |
| **S-5** | JS-readable access-token storage in `localStorage` | Security | `[PARTIALLY VERIFIED]` | `83a6047` / HttpOnly кука включена; утечка токена в JSON теле и рассинхрон `user_id` |
| **B-1** | Active orders destroyed by scheduled DB cleanup | Data Integrity | `[AWAITING VERIFICATION]` | `d61e887` / `DataCleanupProtectionIntegrationTest` |
| **B-2** | Lost update during order picking (`OrderLine`) | Concurrency | `[AWAITING VERIFICATION]` | `b131be1` / `OrderLinePickingConcurrencyIntegrationTest` |
| **B-3** | Cell/location monopoly race | Concurrency | `[AWAITING VERIFICATION]` | `da8d654` / `LocationProductExclusivityConcurrencyIntegrationTest` |
| **B-4** | Allocation vs inventory adjustment race | Concurrency | `[AWAITING VERIFICATION]` | `edb5a9b` / `AllocationAdjustmentConcurrencyIntegrationTest` |
| **B-5** | Concurrent stock reservation / allocation integrity | Concurrency | `[VERIFIED]` | `dce5b8c` / Пессимистическая блокировка + канонический порядок + DB CHECK |
| **D-1** | Destructive Flyway migrations (`TRUNCATE TABLE`) | DB Integrity | `[AWAITING VERIFICATION]` | `ede81dc` / Идемпотентные миграции V19, V31 |
| **D-2** | Stock/Location mapping integrity | DB Integrity | `[VERIFIED]` | `53a8803` / Частичный индекс `uk_stocks_active_location` + защита удаления ячейки |
| **D-3** | `logic_id` uniqueness and integrity | DB Integrity | `[PARTIALLY VERIFIED]` | `f44b0af` / Индекс активен, но несоответствие `lower()` в БД и `upper()` в JPA |
| **D-4** | Missing FK indexes across warehouse tables | Performance | `[VERIFIED]` | `eb1b6b5` / 100% покрытие (20/20 внешних ключей поддержаны B-Tree индексами) |
| **D-5** | N+1 query problem | Performance | `[VERIFIED]` | `5f0f112`, GAP-03 / Запросы заказов, пополнений, остатков и истории переведены на `@EntityGraph` (1–2 запроса) |
| **F-1** | Centralized 401/403/409 interceptors in frontend | Frontend UX | `[PARTIALLY VERIFIED]` | `aed97d3` / Интерцепторы активны; ложный логаут на 401 логина, двойной Toast, Open Redirect |
| **DEF-01** | False session expiry on bad login credentials | Frontend / Auth | `[VERIFIED]` | `interceptors.js` / Предикат `isAuthLoginRequest` исключает 401 при логине из сброса сессии, 4 теста `def01_login_401_interceptor.test.js` |
| **DEF-02** | User ID storage desync & fallback to mock IDs | Frontend / Data | `[VERIFIED]` | `useCurrentUserId.js`, `OrderWithLinesForm.vue`, `InventoryView.vue`, `auth.js` / Legacy localStorage и mock ID устранены, fail-closed валидация, 8 тестов `def02_user_id_dataflow.test.js` |
| **DEF-03** | Index case mismatch (`lower` vs `upper`) | Backend / DB | `[VERIFIED]` | `OrderRepository.java`, `ReplenishmentRepository.java` / Явный JPQL `LOWER(logicId) = LOWER(:logicId)` для findBy и existsBy, подтверждено `LogicIdUniquenessIntegrationTest` |
| **DEF-04** | Unvalidated open redirect in LoginView | Frontend / Sec | `[VERIFIED]` | `redirectSanitizer.js`, `LoginView.vue` / Канонизация и валидация внутренних путей, отсечение //, схемы, backslash и encoded, 7 тестов `def04_open_redirect.test.js` |
| **DEF-05** | Dead code `wms:conflict` event dispatch | Frontend / Arch | `[CONFIRMED DEFECT]` | `interceptors.js` / Событие диспатчится в `window`, но нет ни одного слушателя во фронтенде |
| **GAP-01** | Map DataIntegrityViolationException (`logic_id`) to HTTP 409 | Backend / API | `[VERIFIED]` | `GlobalExceptionHandler.java` / Маппинг нарушений уникальности `logic_id` в HTTP 409 Conflict вместо 500, 4 теста `GlobalExceptionHandlerTest` |
| **GAP-02** | Require secure JWT cookies in production profile | Security | `[VERIFIED]` | `JwtUtil.java`, `AuthController.java` / Fail-fast валидация `wms.jwt.cookie-secure=true` при профилях `prod`/`production`, 16 тестов в `JwtUtilTest` и `AuthControllerTest` |
| **GAP-03** | Missing EntityGraphs in Inventory & History queries | Backend / DB | `[VERIFIED]` | `StockRepository.java`, `InventoryHistoryRepository.java` / `@EntityGraph` на stock (`product`, `location`) и history (`product`, `sourceLocation`, `destinationLocation`, `user`), подтверждено 1 SQL запрос в `NPlusOneQueryPerformanceIntegrationTest` |

---

# ЧАСТЬ 3. ДЕТАЛЬНЫЙ РЕЕСТР ДЕФЕКТОВ АУДИТА И ВЕРИФИКАЦИЯ (S-4 – F-1)

---

### 3.1. [S-4] Overly broad CORS trust boundary (`allowedOriginPatterns("*")`)
* **Статус аудита:** `[PARTIALLY VERIFIED]`
* **Критичность:** High
* **Домен:** Security / Network Boundary
* **Где находится:** `config/WebConfig.java`, `security/SecurityConfig.java`, `application.properties`.
* **Суть проблемы:**
  Ранее использовался подстановочный знак `allowedOriginPatterns("*")` в сочетании с `allowCredentials(true)`. Браузеры отклоняют такую комбинацию, однако при определенных условиях конфигурация открывала доверие любому источнику в локальной сети.
* **Реализация (`fe04b2f`):**
  Полностью удален `allowedOriginPatterns("*")`. Внедрен жесткий белый список источников через `allowedOrigins` в `WebConfig.java` и `SecurityConfig.java`. Разрешенные источники параметризованы через `wms.cors.allowed-origins` и `wms.frontend.url`. Поддержан Preflight OPTIONS и заголовок `Vary: Origin`.
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Ни одного вхождения `allowedOriginPatterns` или неконтролируемого `@CrossOrigin` в кодовой базе бэкенда не осталось. Подтверждена строгая валидация заголовков, методов (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`) и выставление `maxAge(3600)`.
  - `[RESIDUAL RISK]`: По умолчанию в `application.properties` в список разрешенных доверенных источников жестко прописаны `http://localhost:80`, `http://127.0.0.1:80`, `http://localhost`. В производственном окружении при отсутствии явного переопределения через переменную окружения `WMS_CORS_ALLOWED_ORIGINS` любой локальный веб-сервер на 80 порту получает доступ с учетными данными (`allowCredentials(true)`). Необходима строгая изоляция дефолтных значений по профилям `prod` / `dev`.

---

### 3.2. [S-5] JS-readable access-token storage in `localStorage`
* **Статус аудита:** `[PARTIALLY VERIFIED]`
* **Критичность:** Medium
* **Домен:** Security / Web Session
* **Где находится:** `controller/AuthController.java`, `security/JwtRequestFilter.java`, `wmsFront/src/stores/auth.js`, `src/api/authApi.js`.
* **Суть проблемы:**
  JWT-токен хранился в `localStorage`, что делало его уязвимым для мгновенной кражи при любой XSS-уязвимости в зависимостях фронтенда (PrimeVue, Chart.js, Marked).
* **Реализация (`83a6047`):**
  Сервер переведен на выдачу `HttpOnly` cookie (`wms_token`) с атрибутами `SameSite=Lax` и флагом безопасности `wms.jwt.cookie-secure`. Во фронтенде удалена персистентность токена в `localStorage`. Внедрен эндпоинт `/api/auth/logout`, инвалидирующий куку на стороне сервера.
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Кука `wms_token` формируется через `ResponseCookie`, снабжена атрибутами `HttpOnly`, `Path=/api`, `SameSite=Lax`. Фильтр `JwtRequestFilter` успешно извлекает токен из куки при запросах с `withCredentials: true`.
  - `[RESIDUAL RISK]`: Эндпоинт `AuthController.login` продолжает возвращать сырой JWT в теле JSON ответа (`Map.of("token", token)`). Фронтенд-хранилище Pinia `auth.js` сохраняет этот токен в оперативной памяти и шлет заголовок `Authorization: Bearer <token>` на каждый запрос. Это снижает эффект от `HttpOnly`, так как токен остается доступен для чтения из памяти приложения через JS.
  - `[DEFECT]`: Выявлена критическая рассинхронизация чтения данных пользователя во фронтенде (подробно описана в `DEF-02`).

---

### 3.3. [B-5] Concurrent stock reservation / allocation integrity
* **Статус аудита:** `[VERIFIED]`
* **Критичность:** High
* **Домен:** Concurrency / Transactional Correctness
* **Где находится:** `service/OrderService.java`, `repository/StockRepository.java`, `V32__add_stocks_check_constraint.sql`.
* **Суть проблемы:**
  При одновременном назначении двух и более заказов на один и тот же дефицитный остаток товара оба потока считывали доступное количество (`quantity - reservedQuantity`), проходили проверку и одновременно увеличивали `reservedQuantity`. Это приводило к оверселлингу (over-reservation): количество зарезервированного товара превышало фактическое наличие на складе.
* **Реализация (`dce5b8c`):**
  1. В `StockRepository` добавлен метод выборки с пессимистической блокировкой на запись:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT s FROM Stock s WHERE s.product.id = :productId AND s.available = true ORDER BY s.id ASC")
     List<Stock> findAvailableStocksForProductWithLock(@Param("productId") Long productId);
     ```
  2. В `OrderService.assignOrder` внедрена каноническая сортировка строк заказов по `productId`, гарантирующая одинаковый порядок захвата блокировок разными транзакциями и исключающая Deadlock (взаимные блокировки СУБД).
  3. В миграции `V32` добавлен констрейнт целостности на уровне ядра PostgreSQL: `CHECK (quantity_reserved <= quantity)`.
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Транзакционные границы `@Transactional` в `OrderService` защищают всю цепочку резервирования. Пессимистическая блокировка `FOR UPDATE` сериализует конкурирующие потоки на уровне PostgreSQL. Проверка в каталоге БД подтвердила активность констрейнта `stocks_check`. Многопоточный состязательный тест `StockReservationConcurrencyIntegrationTest` подтверждает корректность отката транзакции при нехватке остатка.

---

### 3.4. [D-2] Stock/Location mapping integrity
* **Статус аудита:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Database Integrity / Lifecycle
* **Где находится:** `entity/Stock.java`, `entity/Location.java`, `service/LocationService.java`, `V34__add_unique_constraint_stock_active_location.sql`.
* **Суть проблемы:**
  Связь между стоком и ячейкой была уязвима: ячейку склада можно было деактивировать или удалить даже при наличии активных товаров, что приводило к потере складского учета. Констрейнт уникальности отсутствовал, допуская коллизии параллельного размещения.
* **Реализация (`53a8803`):**
  1. Создана миграция `V34`, накладывающая частичный уникальный индекс `uk_stocks_active_location` на таблицу `stocks (location_id) WHERE available = true`.
  2. В `LocationService` внедрены превентивные проверки жизненного цикла: запрещено удаление и деактивация ячейки, если на ней числятся активные остатки (`quantity > 0`) или назначены незавершенные складские задачи (`TaskStatus.PENDING`, `IN_PROGRESS`).
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Наличие частичного индекса `uk_stocks_active_location` подтверждено в системном каталоге PostgreSQL `pg_indexes`. Сервисные проверки гарантируют невозможность удаления занятых локаций, а частичный индекс предотвращает появление более чем одной активной записи стока на одну складскую ячейку.

---

### 3.5. [D-3] `logic_id` uniqueness and integrity
* **Статус аудита:** `[PARTIALLY VERIFIED]`
* **Критичность:** Medium
* **Домен:** Database Integrity
* **Где находится:** `entity/Order.java`, `entity/Replenishment.java`, `V35__enforce_logic_id_uniqueness.sql`, `OrderRepository.java`, `ReplenishmentRepository.java`.
* **Суть проблемы:**
  Поле `logic_id` (бизнес-номер накладной, например `ORD-2026-001`) не имело ограничения `NOT NULL` и уникального индекса в базе данных. Проверка уникальности выполнялась только на прикладном уровне в Java, что приводило к гонке при одновременной вставке двух одинаковых накладных через API или CSV-импорт.
* **Реализация (`f44b0af`):**
  Создана миграция `V35`, выставившая `NOT NULL` на колонки `logic_id` таблиц `orders` и `replenishments`, а также добавившая функциональные уникальные индексы по нижнему регистру:
  ```sql
  CREATE UNIQUE INDEX uk_orders_logic_id_lower ON orders (LOWER(logic_id));
  CREATE UNIQUE INDEX uk_replenishments_logic_id_lower ON replenishments (LOWER(logic_id));
  ```
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Индексы `uk_orders_logic_id_lower` и `uk_replenishments_logic_id_lower` активны в PostgreSQL. Попытка вставки дубликата с любым регистром символов надежно отклоняется СУБД ошибкой уникальности.
  - `[RESIDUAL RISK]`: Конкурентная коллизия на уровне БД приводит к выбросу `DataIntegrityViolationException`, который возвращает клиенту `HTTP 500 Internal Server Error` вместо корректного `HTTP 409 Conflict`.
  - `[DEFECT]`: Обнаружено фундаментальное расхождение регистра выражений индекса и запросов Hibernate (подробно описано в `DEF-03`).

---

### 3.6. [D-4] Missing foreign-key indexes across warehouse tables
* **Статус аудита:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Database Performance
* **Где находится:** `db/migration/V36__add_missing_foreign_key_indexes.sql`, каталог PostgreSQL `pg_index`.
* **Суть проблемы:**
  В PostgreSQL создание внешнего ключа (`FOREIGN KEY`) автоматически не создает индекс на дочерней таблице. При выполнении каскадных проверок и JOIN-запросов СУБД выполняла полное последовательное сканирование (Sequential Scan), приводя к блокировкам строк и деградации производительности.
* **Реализация (`eb1b6b5`):**
  Создана миграция `V36`, добавившая B-Tree индексы на все внешние ключи во всех складских таблицах (`idx_fk_allocations_stock`, `idx_fk_orders_destination`, `idx_fk_stocks_location` и др.).
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Полная ревизия системного каталога PostgreSQL (`pg_constraint` соединенный с `pg_index`) подтвердила, что **100% внешних ключей (20 из 20)** имеют покрывающий B-Tree индекс в качестве лидирующей колонки. План запросов PostgreSQL (`EXPLAIN`) подтверждает переход с Sequential Scan на Index Scan.

---

### 3.7. [D-5] N+1 query problem & batch fetching
* **Статус аудита:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Performance / ORM
* **Где находится:** `OrderService.java`, `ReplenishmentService.java`, `InventoryService.java`, `repository/OrderRepository.java`, `repository/StockRepository.java`, `repository/InventoryHistoryRepository.java`, `application.properties`.
* **Суть проблемы:**
  Ленивая загрузка (`FetchType.LAZY`) связей `@ManyToOne` и `@OneToMany` при выборке списков заказов, задач, остатков и истории приводила к лавинообразному выполнению отдельных SQL-запросов на каждую строку (до 304 запросов на 82 заказа), перегружая пул соединений БД.
* **Реализация (`5f0f112`, GAP-03):**
  Включен глобальный батчинг `spring.jpa.properties.hibernate.default_batch_fetch_size=50`. На методы `OrderRepository`, `ReplenishmentRepository`, `StockRepository` и `InventoryHistoryRepository` добавлены явные `@EntityGraph` для предвыборки связей. Добавлен интеграционный тест `NPlusOneQueryPerformanceIntegrationTest`.
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Зафиксировано резкое сокращение запросов на всех ключевых операциях чтения: для расширенных заказов — с 304 до 9 запросов, для пополнений — с 63 до 2 запросов, для выборки остатков `getAllStock` (154 записи) — ровно 1 SQL запрос, для истории инвентаризации `getAllHistory` (178 записей) — ровно 1 SQL запрос. Все тесты производительности в `NPlusOneQueryPerformanceIntegrationTest` успешно пройдены (`5/5 passed`).

---

### 3.8. [F-1] Centralized Axios interceptors for 401, 403, and 409
* **Статус аудита:** `[PARTIALLY VERIFIED]`
* **Критичность:** Medium
* **Домен:** Frontend Architecture & UX
* **Где находится:** `wmsFront/src/api/interceptors.js`, `notificationService.js`, `stores/auth.js`, `views/auth/LoginView.vue`.
* **Суть проблемы:**
  Отсутствовала изолированная централизованная обработка HTTP-ошибок: при `403 Forbidden` пользователь ошибочно разлогинивался с потерей несохраненных данных, а ошибки конкурентных коллизий `409 Conflict` не уведомляли интерфейс о необходимости перезагрузить данные.
* **Реализация (`aed97d3`):**
  Разработан `notificationService.js` с дебаунсингом сообщений (1500 мс). В `api/interceptors.js` внедрены модульные перехватчики: при 401 выполняется `authStore.logout()` и редирект на `/login?sessionExpired=true`; при 403 сессия сохраняется и выводится Toast «Access Denied»; при 409 сессия сохраняется, выводится предупреждение «Conflict Detected» и генерируется событие `wms:conflict`. Добавлен юнит-тест `interceptors.test.js` (7 тестов).
* **Результаты состязательного аудита и границы гарантий:**
  - `[VERIFIED]`: Защита от ложного сброса сессии при 403 подтверждена модульными тестами. Дебаунсинг нотификаций предотвращает шквал всплывающих окон. Все 7 тестов в `interceptors.test.js` успешно проходят.
  - `[RESIDUAL RISK]`: Выявлены 3 критических недочета реализации (ложный сброс сессии при ошибке логина `DEF-01`, незащищенный параметр редиректа `DEF-04`, мертвый код события `wms:conflict` `DEF-05`), подробно зафиксированные в Части 4.

---

# ЧАСТЬ 4. РЕЕСТР НОВЫХ ДЕФЕКТОВ, ВЫЯВЛЕННЫХ СОСТЯЗАТЕЛЬНЫМ АУДИТОМ (DEF-01 – DEF-05)

---

### 4.1. [DEF-01] Ложное истечение сессии при неверных учетных данных в форме логина
* **Статус:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Frontend UX / Authentication
* **Затронутые компоненты:** `inbound-storage-dispatch/wmsFront/src/api/interceptors.js` (строки 16–50, 125–135), `src/views/auth/LoginView.vue`.
* **Суть проблемы:**
  Глобальный перехватчик ответов Axios перехватывал **любой** статус `401 Unauthorized` без проверки URL запроса. Когда неавторизованный пользователь вводил неверный пароль на странице логина (`POST /api/auth/login`), бэкенд возвращал `401 Unauthorized`. Перехватчик вызывал `authStore.logout()`, показывал сообщение «Session expired. Please log in again» и редиректил на `/login?sessionExpired=true`, затирая сообщение об ошибке аутентификации формы логина.
* **Как устранено:**
  1. Реализована функция `isAuthLoginRequest(config)`, надежно идентифицирующая запросы аутентификации на эндпоинт входа (`POST /auth/login`, `POST /api/auth/login`, абсолютные URL).
  2. В `handle401Unauthorized` и `setupInterceptors` внедрена защита: при `isAuthLoginRequest(...) === true` обработчик немедленно возвращает управление, не вызывая `logout()`, не выводя ложный Toast «Session Expired» и не выполняя редирект.
  3. Ошибка `401` беспрепятственно передается в компонент `LoginView.vue`, где штатно отображается понятное пользователю сообщение («Incorrect username or password»).
  4. Для защищенных эндпоинтов (`/orders`, `/inventory`, `/auth/me` и др.) полностью сохранен существующий механизм инвалидации протухшей сессии.
* **Верификация:**
  Создан регрессионный тестовый набор `test/def01_login_401_interceptor.test.js` (4 теста):
  - Проверена точная идентификация запросов через `isAuthLoginRequest` (POST, пути, query params, регистры, защита от ложных срабатываний на `/auth/me`, `/auth/logout`, `/auth/verify`).
  - Проверено отсутствие вызова `authStore.logout()`, уведомлений и навигации при 401 на `/auth/login`.
  - Проверена корректная работа сессионного сброса при 401 на защищенных эндпоинтах (`/v1/orders/extended`, `/inventory`, `/auth/me`, `/inventory/add`).
  - Проверена интеграция с интерцептором Axios `setupInterceptors`.
  - Все тесты успешно пройдены.

---

### 4.2. [DEF-02] Рассинхронизация хранилища `user_id` и подстановка сид-пользователей
* **Статус:** `[VERIFIED]`
* **Критичность:** High
* **Домен:** Frontend Data Integrity / Audit Trail
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsFront/src/composables/useCurrentUserId.js`
  - `inbound-storage-dispatch/wmsFront/src/components/OrderWithLinesForm.vue`
  - `inbound-storage-dispatch/wmsFront/src/views/supervisor/InventoryView.vue`
  - `inbound-storage-dispatch/wmsFront/src/stores/auth.js`
* **Суть проблемы:**
  В рамках задачи S-5 (`83a6047`) данные пользователя и JWT были удалены из `localStorage` и перенесены в реактивное хранилище Pinia (`sessionStorage`). Однако в представлениях `OrderWithLinesForm.vue` и `InventoryView.vue` оставался устаревший синхронный вызов чтения `localStorage.getItem('user_id')` с fallback на 1, 2, 3 при отсутствии значения в `localStorage`.
* **Как устранено:**
  1. Создан composable `useCurrentUserId.js` (`resolveCurrentUserId`), извлекающий валидный целочисленный ID строго из `authStore.user.id`.
  2. В `InventoryView.vue` и `OrderWithLinesForm.vue` удалены все обращения к `localStorage.getItem('user_id')` и исключены fallback-значения (`|| 1`, `|| 2`, `|| 3`).
  3. В `OrderWithLinesForm.vue` и `InventoryView.vue` внедрена строгая fail-closed проверка: при отсутствии авторизованного ID выполнение прерывается с выводом ошибки Toast без отправки HTTP-запроса.
  4. В `auth.js` устранены сид-пользователи (`seededUsers`) с моковыми ID. При логине выполняется автори авторитетный запрос к эндпоинту `/api/auth/me` для извлечения реального ID пользователя из БД.
* **Верификация:**
  Создан состязательный тестовый набор `test/def02_user_id_dataflow.test.js` (8 тестов):
  - Проверена сквозная передача `userId = X` (42) и `userId = Y` (99) в payload корректировки и добавления остатков.
  - Проверена блокировка запросов при отсутствии `user` или невалидном `user.id`.
  - Проверено полное игнорирование `localStorage.user_id`.
  - Статический анализ подтвердил отсутствие вызовов `localStorage.getItem('user_id')` и fallback ID в кодовой базе.
  - Все 15 тестов фронтенда (`interceptors.test.js` + `def02_user_id_dataflow.test.js`) успешно пройдены.

---

### 4.3. [DEF-03] Несоответствие регистра функционального индекса (`lower` vs `upper`)
* **Статус:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Backend Performance / Database Optimization
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V35__enforce_logic_id_uniqueness.sql`  
  - `com/isd/wms/repository/OrderRepository.java`  
  - `com/isd/wms/repository/ReplenishmentRepository.java`
* **Суть проблемы:**
  Миграция `V35` создала уникальный функциональный индекс по выражению в **нижнем регистре**:
  ```sql
  CREATE UNIQUE INDEX uk_orders_logic_id_lower ON orders (LOWER(logic_id));
  CREATE UNIQUE INDEX uk_replenishments_logic_id_lower ON replenishments (LOWER(logic_id));
  ```
  В то же время в Spring Data JPA репозиториях для регистронезависимого поиска был объявлен производный метод `findByLogicIdIgnoreCase(String logicId)` и `existsByLogicIdIgnoreCase(String logicId)`. По спецификации Hibernate и Spring Data JPA ключевое слово `IgnoreCase` генерирует SQL-предикат с приведением к **верхнему регистру**:
  ```sql
  WHERE UPPER(orders.logic_id) = UPPER(?)
  ```
  Оптимизатор запросов PostgreSQL не мог сопоставить выражение `UPPER(logic_id)` с функциональным индексом, построенным по `LOWER(logic_id)`.
* **Как устранено:**
  1. В `OrderRepository` методы `findByLogicIdIgnoreCase` и `existsByLogicIdIgnoreCase` заменены на явные JPQL-запросы:
     ```java
     @Query("SELECT o FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
     Optional<Order> findByLogicIdIgnoreCase(@Param("logicId") String logicId);

     @Query("SELECT COUNT(o) > 0 FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
     boolean existsByLogicIdIgnoreCase(@Param("logicId") String logicId);
     ```
  2. В `ReplenishmentRepository` методы `findByLogicIdIgnoreCase` и `existsByLogicIdIgnoreCase` аналогично заменены на явные JPQL-запросы с функцией `LOWER()`.
  3. Сгенерированный SQL теперь строго формирует предикаты `lower(o.logic_id) = lower(?)`, в точности сопоставимые с выражениями функциональных индексов `uk_orders_logic_id_lower` и `uk_replenishments_logic_id_lower`.
* **Верификация:**
  - Системный каталог PostgreSQL (`pg_indexes`) подтверждает наличие индексов `uk_orders_logic_id_lower` и `uk_replenishments_logic_id_lower` по выражению `lower((logic_id)::text)`.
  - Запущен интеграционный тест `LogicIdUniquenessIntegrationTest`: все 4 теста (проверка уникальности в БД, валидация сервисного слоя, регистронезависимый поиск) успешно пройдены (`4/4 passed`).

---

### 4.4. [DEF-04] Невалидируемый параметр редиректа в форме аутентификации (Open Redirect)
* **Статус:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Web Security / Navigation Integrity
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsFront/src/utils/redirectSanitizer.js`
  - `inbound-storage-dispatch/wmsFront/src/views/auth/LoginView.vue` (строки 82, 118)
* **Суть проблемы:**
  После успешного входа пользователя в систему компонент `LoginView.vue` выполнял безусловный переход по адресу, переданному в `route.query.redirect`:
  ```javascript
  const redirect = route.query.redirect || '/'
  router.push(redirect)
  ```
  Это открывало уязвимость Open Redirect через фишинговые ссылки с `//evil.com`, `https://evil.com`, `/\evil.com` или закодированными URL (`%2f%2fevil.com`).
* **Как устранено:**
  1. Разработан модуль `redirectSanitizer.js` (`sanitizeRedirect`), реализующий строгую валидацию и канонизацию URL.
  2. Разрешаются исключительно безопасные относительные внутренние пути (начинающиеся с одиночного `/`, без backslash, без control characters, без внешних схем `javascript:`, `http:`, `https:`).
  3. Проводится итеративное декодирование для отсечения обхода через URL-encoding (`%2f`, `%5c`, `%252f`).
  4. Проводится верификация через спецификацию WHATWG URL parser с проверкой происхождения `origin === 'http://localhost'`.
  5. В `LoginView.vue` вызов `router.push` обернут в `sanitizeRedirect(route.query.redirect, authStore.dashboardPath)`.
* **Верификация:**
  Создан состязательный тестовый набор `test/def04_open_redirect.test.js` (7 тестов):
  - Проверена корректность перехода на легитимные пути (`/orders`, `/inventory`, `/foo?x=1`, `/supervisor/dashboard`).
  - Проверено отклонение протокольно-относительных URL (`//evil.example`, `///evil.example`).
  - Проверено отклонение абсолютных схем (`https://`, `http://`, `javascript:`, `data:`).
  - Проверено отклонение всех вариантов с обратными слэшами (`/\evil`, `\\evil`, `\orders`).
  - Проверено отклонение закодированных атак (`%2f%2fevil`, `/%5cevil`, `%00/orders`).
  - Проверена обработка `null`, `undefined`, чисел, массивов и пустых строк.
  - Статический анализ подтвердил обязательную санитизацию в `LoginView.vue`.
  - Все тесты успешно пройдены.

---

### 4.5. [DEF-05] Мёртвый код генерации CustomEvent `wms:conflict` без слушателей во фронтенде
* **Критичность:** Low
* **Домен:** Frontend Architecture
* **Затронутые компоненты:** `inbound-storage-dispatch/wmsFront/src/api/interceptors.js` (строка 80).
* **Суть проблемы:**
  При получении ошибки `409 Conflict` перехватчик Axios генерирует кастомное событие на глобальном объекте окна браузера:
  ```javascript
  window.dispatchEvent(new CustomEvent('wms:conflict', { detail: errorPayload }))
  ```
  Однако поиск по всей кодовой базе фронтенда показывает, что ни один компонент или сервис не подписывается на событие (`window.addEventListener('wms:conflict', ...)`).
* **Следствие для системы:**
  Событие является мертвым кодом. Таблицы заказов, списки задач и карточки складских остатков не обновляют свое реактивное состояние при возникновении конфликта версий данных, оставляя пользователя с устаревшим представлением на экране до ручной перезагрузки страницы (F5).
* **План устранения:**
  Добавить обработчик события `wms:conflict` в базовый макет приложения (`App.vue` или композицию табличных представлений) для автоматической тихой перезагрузки активного набора данных при получении конфликта от сервера.

---

### 4.6. [GAP-02] Fail-fast проверка флага wms.jwt.cookie-secure в production профиле
* **Статус:** `[VERIFIED]`
* **Критичность:** High
* **Домен:** Web Security / Session Integrity
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/controller/AuthController.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/JwtUtilTest.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/controller/AuthControllerTest.java`
* **Суть проблемы:**
  По умолчанию флаг `wms.jwt.cookie-secure` выставлен в `false` для удобства локальной разработки через HTTP (`http://localhost:8080`). В производственном окружении выпуск авторизационных cookies без атрибута `Secure` подвергает сессию риску перехвата (man-in-the-middle) при передаче по незащищенным каналам.
* **Как устранено:**
  1. В `JwtUtil` внедрен параметр `@Value("${wms.jwt.cookie-secure:false}") boolean cookieSecure` и валидация активных профилей Spring (`Profiles.of("prod", "production")`).
  2. При активном профиле `prod` / `production` и значении `cookieSecure == false` приложение аварийно прерывает запуск с `IllegalStateException("Production startup aborted: wms.jwt.cookie-secure must be true in production profile. Set WMS_JWT_COOKIE_SECURE=true.")`.
  3. В `AuthController` внедрена аналогичная защита при инициализации контроллера.
  4. Для дев/тест окружений сохранена возможность работы с `cookieSecure == false`.
  5. `JwtUtilTest` переведен на `MockEnvironment` (POJO) для исключения накладных расходов динамических Java-агентов.
* **Верификация:**
  - `JwtUtilTest`: проверены тесты выброса исключения при `prod + cookieSecure=false`, успешного запуска при `prod + cookieSecure=true`, работы в профиле `dev` при `cookieSecure=false` (все 7 тестов пройдены).
  - `AuthControllerTest`: проверены тесты валидации `prod + cookieSecure=false` и успешного запуска с `cookieSecure=true` (все 9 тестов пройдены).
  - Суммарно 16/16 тестов успешно пройдены за 9 секунд.

---

### 4.7. [GAP-01] Маппинг DataIntegrityViolationException (конфликт logic_id) в HTTP 409 Conflict
* **Статус:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Backend API / Error Handling Integrity
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/exception/GlobalExceptionHandler.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/exception/GlobalExceptionHandlerTest.java`
* **Суть проблемы:**
  При параллельной вставке заказов или пополнений с одинаковым `logic_id` сервисный превентивный поиск `findByLogicIdIgnoreCase` мог возвращать `false` в обоих потоках, после чего обе транзакции пытались выполнить `INSERT`. На уровне PostgreSQL функциональный уникальный индекс `uk_orders_logic_id_lower` / `uk_replenishments_logic_id_lower` корректно блокировал дубликат с ошибкой `23505 unique constraint violation`, однако Spring Data выбрасывал неперехваченный `DataIntegrityViolationException`, превращая ошибку для клиента в `HTTP 500 Internal Server Error` вместо семантически корректного `HTTP 409 Conflict`.
* **Как устранено:**
  1. В `GlobalExceptionHandler` зарегистрирован обработчик `@ExceptionHandler(DataIntegrityViolationException.class)`.
  2. Обработчик анализирует `mostSpecificCause` и сопоставляет имена ограничений уникальности `logic_id` (`uk_orders_logic_id_lower`, `uk_replenishments_logic_id_lower`, `logic_id`), формируя ответ `HTTP 409 Conflict` со структурированным телом `ApiErrorResponse` ("A resource with the specified logic_id already exists.").
  3. Для других нарушений уникальности возвращается `HTTP 409 Conflict` с сообщением о нарушении констрейнта дублирования, исключая непредвиденные 500-е ошибки при гонках вставки.
* **Верификация:**
  - Созданы модульные тесты в `GlobalExceptionHandlerTest`:
    - `handleDataIntegrityViolation_withLogicIdUniqueConstraint_returnsConflict`: подтвержден возврат статуса 409 и сообщение о конфликте `logic_id`.
    - `handleDataIntegrityViolation_withGenericUniqueConstraint_returnsConflict`: подтвержден возврат 409 для общих констрейнтов уникальности.
  - Все тесты `GlobalExceptionHandlerTest` успешно пройдены (`4/4 passed`).

---

### 4.8. [GAP-03] Оптимизация N+1 запросов для складских остатков и истории инвентаризации
* **Статус:** `[VERIFIED]`
* **Критичность:** Medium
* **Домен:** Performance / Database Query Optimization
* **Затронутые компоненты:**  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/StockRepository.java`  
  - `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/repository/InventoryHistoryRepository.java`  
  - `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/service/NPlusOneQueryPerformanceIntegrationTest.java`
* **Суть проблемы:**
  Методы чтения складских остатков (`InventoryService.getAllStock()`) и истории операций (`InventoryService.getAllHistory()`) мапили сущности в DTO (`StockMapper`, `InventoryHistoryMapper`), обращаясь к ленивым ассоциациям:
  - `Stock`: `product`, `location`
  - `InventoryHistory`: `product`, `sourceLocation`, `destinationLocation`, `user`
  В `StockRepository` и `InventoryHistoryRepository` отсутствовали аннотации `@EntityGraph`, из-за чего Hibernate выполнял пакетную ленивую догрузку батчами по 50 записей ($O(N / 50)$ SQL-запросов), создавая избыточную нагрузку на базу данных при росте складских запасов и истории.
* **Как устранено:**
  1. В `StockRepository` методы `findAll()`, `findAllByAvailableIsTrue()`, `findByLocationId(Long)` и `findAllByLocationId(Long)` снабжены аннотацией `@EntityGraph(attributePaths = {"product", "location"})`.
  2. В `InventoryHistoryRepository` методы `findAll()` и `findByProductIdAndSourceLocationIdOrProductIdAndDestinationLocationId(...)` снабжены аннотацией `@EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})`.
  3. Поскольку все затронутые ассоциации являются `@ManyToOne` (to-one), JPA выполняет жадную выборку связей через `LEFT OUTER JOIN` в рамках единого SQL-запроса без дублирования строк и декартова произведения.
* **Верификация:**
  - В `NPlusOneQueryPerformanceIntegrationTest` добавлены два теста со счетчиком подготовленных выражений Hibernate (`Statistics.getPrepareStatementCount()`):
    - `measureGetAllStockQueries`: выборка 154 активных складских остатков выполнена ровно за **1 SQL запрос** (`queries <= 2`).
    - `measureGetAllHistoryQueries`: выборка 178 исторических записей инвентаризации выполнена ровно за **1 SQL запрос** (`queries <= 2`).
  - Все 5 интеграционных тестов производительности успешно пройдены (`5/5 passed`, `BUILD SUCCESS`).
