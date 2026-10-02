# Полный справочник архитектурных исправлений и безопасности проекта (WMS + Code RAG)

> **Статус документа:** Исчерпывающий реестр **29** реализованных архитектурных решений (18 в ядре WMS + 11 в подсистеме Code RAG) и дорожная карта (Roadmap) из **8** открытых дефектов аудита безопасности и целостности данных.  
> **Основание:** Анализ полного графа коммитов Git (`git log`), данных технических аудитов и результатов верификации (171 автоматический тест бэкенда Java 21, 29 тестов RAG Python 3.11).

---

# ЧАСТЬ 1. ЯДРО WMS-СИСТЕМЫ (`inbound-storage-dispatch` / Backend)

---

### 1.1. [SEC-01] Отклонение дефолтного JWT-секрета в Production профиле
* **Коммит:** `9bc4ea4`
* **В чём заключалась уязвимость:**
  В классе `JwtUtil` при отсутствии переменной окружения `JWT_SECRET` загружался жестко закодированный dev-ключ (`default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`). При развертывании в продакшене злоумышленник мог подписать произвольный JWT-токен с максимальными привилегиями (`ROLE_DEV`, `ROLE_SUPERVISOR`) и полностью скомпрометировать систему.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`.
* **Как устранено:**
  В конструктор `JwtUtil` внедрен Spring `Environment`. Если активен профиль `prod` или `production`, и обнаружен дефолтный dev-ключ, запуск приложения аварийно прерывается с `IllegalStateException`.
* **Код (Было / Стало):**
  ```java
  // ДО (Уязвимо):
  public JwtUtil(@Value("${wms.jwt.secret}") String secretString) {
      this.SECRET_KEY = Keys.hmacShaKeyFor(secretString.getBytes());
  }

  // ПОСЛЕ (Безопасно):
  public JwtUtil(@Value("${wms.jwt.secret}") String secretString, Environment environment) {
      if (secretString == null || secretString.isBlank()) {
          throw new IllegalStateException("CRITICAL: JWT secret string is empty or null!");
      }
      if (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
          if (DEFAULT_DEV_SECRET.equals(secretString)) {
              throw new IllegalStateException(
                  "Production startup aborted: default JWT secret key cannot be used in production profile. " +
                  "Set a secure JWT_SECRET environment variable."
              );
          }
      }
      this.SECRET_KEY = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
  }
  ```
* **Тест:** `inbound-storage-dispatch/wmsBack/src/test/java/com/isd/wms/security/JwtUtilTest.java`.

---

### 1.2. [SEC-02] Валидация принадлежности оператора при сканировании Transport Unit (IDOR / BOLA)
* **Коммит:** `ad5adc4`
* **В чём заключалась уязвимость:**
  Эндпоинт `POST /api/v1/allocations/{id}/scan-tu` позволял любому аутентифицированному оператору привязать единицу транспортировки (TU) к аллокации, назначенной совершенно другому оператору. Метод `tuService.occupyTransportUnit` вызывался до проверки назначения задачи.
* **Где скрывалась:** `AllocationController.java` (`scanTransportUnit`), `AllocationExecutionService.java`.
* **Как устранено:**
  Перед вызовом `tuService.occupyTransportUnit` добавлен обязательный вызов `allocationExecutionService.getAssignedAllocation(id)`. Метод проверяет, что текущий аутентифицированный пользователь (`securityFacade.getCurrentUser()`) совпадает с оператором, назначенным на задачу, иначе выбрасывает `InvalidRequestException` (HTTP 400).
* **Код (Было / Стало):**
  ```java
  // ДО:
  public ResponseEntity<OperatorTaskSummaryResponse> scanTransportUnit(...) {
      tuService.occupyTransportUnit(request.barcode(), id, request.isOrder());
      ...
  }

  // ПОСЛЕ:
  public ResponseEntity<OperatorTaskSummaryResponse> scanTransportUnit(...) {
      allocationExecutionService.getAssignedAllocation(id); // Валидация принадлежности!
      tuService.occupyTransportUnit(request.barcode(), id, request.isOrder());
      ...
  }
  ```
* **Тест:** `AllocationControllerTest.java` (`scanTransportUnit_WhenOperatorNotAssigned_ShouldReturnBadRequest`).

---

### 1.3. [DATA-01] Гарантия уникальности остатков на локации (Race Condition / Duplicate Stocks)
* **Коммит:** `bc862ad`
* **В чём заключалась уязвимость:**
  В таблице `stocks` отсутствовал уникальный составной ключ на пару `(product_id, location_id)`. При параллельных операциях приемки/размещения создавались дублирующие записи об остатках одного товара на одной ячейке. Это ломало методы репозиториев вроде `findByProductIdAndLocationId`, выбрасывавшие `IncorrectResultSizeDataAccessException`.
* **Где скрывалась:** `com.isd.wms.entity.Stock`, схема БД.
* **Как устранено:**
  1. Создана миграция Flyway `V32__add_unique_constraint_stock_product_location.sql`:
     ```sql
     ALTER TABLE stocks ADD CONSTRAINT uk_stocks_product_location UNIQUE (product_id, location_id);
     ```
  2. В сущность `Stock.java` добавлено аннотирование `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_stocks_product_location", columnNames = {"product_id", "location_id"}))`.
* **Тест:** Компиляция Maven и проверка констрейнтов миграции.

---

### 1.4. [DATA-02] Предотвращение дублирования активных авто-пополнений при конкурентных запросах
* **Коммит:** `3a7c22a`
* **В чём заключалась уязвимость:**
  При одновременном падении остатка товара на ячейке отбора ниже порога параллельные потоки вызывали `checkAndTriggerAutoReplenishment`. Оба потока одновременно проходили проверку `hasActiveReplenishment` и создавали две дублирующие задачи пополнения на одну и ту же позицию.
* **Где скрывалась:** `ReplenishmentService.java`, схема таблицы `replenishments`.
* **Как устранено:**
  1. Добавлена миграция Flyway `V33__add_unique_index_active_replenishments.sql` с частичным уникальным индексом:
     ```sql
     CREATE UNIQUE INDEX uk_active_replenishment ON replenishments (product_id, destination_location_id)
     WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS');
     ```
  2. Метод триггера изолирован в транзакцию `Propagation.REQUIRES_NEW` с перехватом `DataIntegrityViolationException`, безопасно гасящим конкурентные коллизии без отката вызывающей бизнес-транзакции.
* **Тест:** `ReplenishmentServiceTest.java`.

---

### 1.5. [DATA-03] Исключение активных аллокаций из очистки БД (Утечка зарезервированного стока)
* **Коммит:** `62ad8d2`
* **В чём заключалась уязвимость:**
  Фоновая задача `DataCleanupJob` удаляла все аллокации старше заданного срока (`cutoffDate`) без проверки их статуса. При удалении активных аллокаций (`CREATED`, `ASSIGNED`, `IN_PROGRESS`) физический остаток на `Stock.reservedQuantity` оставался заблокированным навсегда, приводя к необратимой потере доступного инвентаря.
* **Где скрывалась:** `AllocationRepository.java` (`deleteAllocationsOlderThan`), `DataCleanupJob.java`.
* **Как устранено:**
  Запрос модифицирован: удаление разрешено строго для терминальных статусов (`COMPLETED`, `PARTIALLY_COMPLETED`, `CANCELED`).
* **Код (Было / Стало):**
  ```java
  // ДО:
  @Query("DELETE FROM Allocation a WHERE a.createdAt < :cutoffDate")
  int deleteAllocationsOlderThan(LocalDateTime cutoffDate);

  // ПОСЛЕ:
  @Query("""
      DELETE FROM Allocation a
      WHERE a.createdAt < :cutoffDate
        AND a.status IN (
            com.isd.wms.enums.Status.COMPLETED,
            com.isd.wms.enums.Status.PARTIALLY_COMPLETED,
            com.isd.wms.enums.Status.CANCELED
        )
      """)
  int deleteAllocationsOlderThan(LocalDateTime cutoffDate);
  ```
* **Тест:** `DataCleanupJobTest.java`.

---

### 1.6. [APP-01] Маппинг ошибок оптимистической блокировки на HTTP 409 Conflict
* **Коммит:** `8adafbc`
* **В чём заключалась неточность:**
  При параллельных обновлениях версионированных сущностей (`Stock`, `Order`) Hibernate выбрасывал `ObjectOptimisticLockingFailureException` или `OptimisticLockException`. Из-за отсутствия явного обработчика в `GlobalExceptionHandler` клиент получал `HTTP 500 Internal Server Error`, что скрывало бизнес-природу конфликта.
* **Где скрывалась:** `GlobalExceptionHandler.java`.
* **Как устранено:**
  Добавлен обработчик `@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})`, возвращающий стандартизированный ответ `HTTP 409 Conflict` с рекомендацией повторить операцию.
* **Тест:** `GlobalExceptionHandlerTest.java`.

---

### 1.7. [TEST-01] Актуализация устаревших юнит-тестов и сигнатур
* **Коммиты:** `839933b`, `621faa9`
* **В чём заключалась неточность:**
  После эволюции бизнес-логики сервисов аллокации и конструкторов контроллеров старые тесты не компилировались или падали на изменившихся стратегиях выполнения (`PickingAllocationStrategy`, `ReplenishmentAllocationCompletionStrategy`).
* **Где скрывалось:** Тестовые классы `OrderServiceTest`, `ReplenishmentServiceTest`, `AllocationExecutionServiceTest`, `CategoryServiceTest`.
* **Как устранено:**
  Обновлены моки, актуализированы сигнатуры конструкторов и скорректированы assertions под актуальное поведение бизнес-процессов.

---

### 1.8. [INFRA-01] Контейнеризация стека WMS в Docker Compose
* **Коммит:** `d40ea94`
* **В чём заключалась задача:**
  Отсутствовала изолированная среда для локального поднятия полного контура WMS (бэкенд, фронтенд, PostgreSQL, векторный индекс).
* **Как устранено:**
  Создан `docker-compose.yaml` с сервисами `postgres`, `wms-backend` (мультистейдж сборка OpenJDK 21), `wms-frontend` (Nginx + Vue 3) и сетевой изоляцией.

---

### 1.9. [S-1] Запрет аутентификации неактивных пользователей (Account Deactivation Bypass)
* **Коммит:** `b1169d5`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  При аутентификации пользователя через `/api/auth/login` (`AuthService`) и проверке JWT-токена в `JwtRequestFilter` отсутствовала проверка флага активности `user.getIsActive()`. Пользователь, заблокированный или уволенный администратором, мог успешно войти в систему, получить валидный JWT-токен и продолжать вызывать защищенные складские эндпоинты.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/AuthService.java`, `com/isd/wms/service/CustomUserDetailsService.java`, `com/isd/wms/security/JwtRequestFilter.java`.
* **Как устранено:**
  1. В `CustomUserDetailsService.loadUserByUsername` статус активности передан в Spring Security `User(..., enabled=user.getIsActive())`.
  2. В `AuthService.authenticate` добавлена строгая проверка активности учетной записи с выбросом `AccountDeactivatedException`.
  3. В `GlobalExceptionHandler` зарегистрирован маппинг `AccountDeactivatedException` -> `HTTP 403 Forbidden`.
  4. В `JwtRequestFilter` добавлена проверка активности пользователя на каждом входящем запросе.
* **Код (Было / Стало):**
  ```java
  // ДО (AuthService):
  authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
  User user = userRepository.findByUsername(request.username()).orElseThrow(...);
  return generateToken(user);

  // ПОСЛЕ (AuthService):
  User user = userRepository.findByUsername(request.username())
      .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
  if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new AccountDeactivatedException("User account is deactivated");
  }
  authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
  return generateToken(user);
  ```
* **Тест:** `AuthServiceTest.java`, `CustomUserDetailsServiceTest.java`, `AuthControllerTest.java`.

---

### 1.10. [S-3] Искоренение захардкоженных секретов и валидация через SHA-256 Fingerprint
* **Коммиты:** `79b229b`, `5b445a3`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В репозитории присутствовали захардкоженные дефолтные пароли и секреты (`docker-compose.yaml`, `EmailService.java`). Предыдущая проверка в `JwtUtil` (`9bc4ea4`) сравнивала секрет с открытой строкой `default_jwt_dev_secret_key_must_be_changed_in_production_32bytes_min`, что приводило к сохранению скомпрометированного секрета в открытом виде в скомпилированном байткоде и открывало риск утечки при декомпиляции.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/JwtUtil.java`, `com/isd/wms/service/EmailService.java`, `docker-compose.yaml`.
* **Как устранено:**
  1. Удалены жестко закодированные пароли из `EmailService.java` и `docker-compose.yaml`.
  2. В `JwtUtil` открытая текстовая проверка заменена на криптографический SHA-256 фингерпринт скомпрометированного секрета (`b428d00346a0661266e7b57fa0d238ecfef5f0bc59a68b9264c39b7d87bc7d96`).
  3. При запуске в профилях `prod` или `production` наличие скомпрометированного секрета немедленно прерывает работу приложения с `IllegalStateException`.
* **Код (Было / Стало):**
  ```java
  // ДО:
  if (DEFAULT_DEV_SECRET.equals(secretString)) {
      throw new IllegalStateException("Production startup aborted: default JWT secret key cannot be used...");
  }

  // ПОСЛЕ:
  private static final String COMPROMISED_DEV_SECRET_SHA256 =
      "b428d00346a0661266e7b57fa0d238ecfef5f0bc59a68b9264c39b7d87bc7d96";
  ...
  String incomingHash = sha256Hex(secretString);
  if (COMPROMISED_DEV_SECRET_SHA256.equalsIgnoreCase(incomingHash)) {
      throw new IllegalStateException("Production startup aborted: configured JWT secret matches known compromised dev secret fingerprint.");
  }
  ```
* **Тест:** `JwtUtilTest.java`.

---

### 1.11. [S-2] Предотвращение самоактивации неактивных учетных записей через `/register`
* **Коммит:** `2e1f0e9`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В эндпоинте регистрации `/api/auth/register` (`UserService.registerUser`) при получении запроса с логином или email уже существующего пользователя сервис перезаписывал его пароль и безусловно выставлял `userToSave.setIsActive(true)`. Это позволяло любому ранее заблокированному или уволенному сотруднику (включая супервайзеров) разблокировать свою учетную запись без ведома администратора.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/UserService.java`.
* **Как устранено:**
  1. В `UserService.registerUser` добавлена проверка статуса активности существующей учетной записи: если пользователь деактивирован (`!existingUser.getIsActive()`), любая попытка повторной регистрации блокируется с `AccessDeniedException` («Cannot reactivate a deactivated user through registration. Contact an administrator.»).
  2. Добавлен контроль вызывающего контекста: создание или обновление учетных записей с ролью `ROLE_SUPERVISOR` заблокировано для не-супервайзеров.
  3. Новые пользователи создаются с `isActive = false` до прохождения верификации через email-токен.
* **Тест:** `UserServiceReactivationSecurityTest.java` (покрывает сценарии деактивированного пользователя, эскалации ролей и верификации через токен).

---

### 1.12. [B-1] Защита активных заказов от разрушительного cron-удаления
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
* **Код (Было / Стало):**
  ```java
  // ДО (OrderRepository):
  @Query("DELETE FROM Order o WHERE o.createdAt < :cutoffDate")
  int deleteOrdersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

  // ПОСЛЕ (OrderRepository):
  @Query("""
      DELETE FROM Order o
      WHERE o.createdAt < :cutoffDate
        AND o.status IN (com.isd.wms.enums.OrderStatus.COMPLETED, com.isd.wms.enums.OrderStatus.CANCELED)
      """)
  int deleteOrdersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
  ```
* **Тест:** `DataCleanupProtectionIntegrationTest.java`.

---

### 1.13. [D-1] Устранение деструктивных миграций Flyway (`TRUNCATE TABLE ... CASCADE`)
* **Коммит:** `ede81dc`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  В версионированных миграциях Flyway `V19__populate_isd_database.sql` и `V31__seed_warehouse_data_final.sql` на первой же строке выполнялась деструктивная команда:
  ```sql
  TRUNCATE TABLE products, locations, orders, replenishments, stocks RESTART IDENTITY CASCADE;
  ```
  Поскольку эти файлы являлись частью основной миграционной цепочки Flyway, при их выполнении на рабочей или промежуточной базе данных (staging/production) все существующие складские данные безвозвратно удалялись.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/resources/db/migration/V19__populate_isd_database.sql`, `V31__seed_warehouse_data_final.sql`.
* **Как устранено:**
  1. Команды `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` полностью удалены из обеих миграций.
  2. Все вставки данных переведены на идемпотентный синтаксис PostgreSQL `INSERT INTO ... ON CONFLICT DO NOTHING`, что предотвращает коллизии первичных ключей при повторном накатывании и сохраняет целостность существующих данных.
* **Тест:** Проверено применение цепочки миграций Flyway как на чистой базе, так и на предварительно наполненной бизнес-данными БД без потерь.

---

### 1.14. [B-2] Предотвращение потери обновлений (Lost Update) при параллельном отборе строк заказов
* **Коммит:** `b131be1`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  При параллельном отборе товаров несколькими операторами по разным аллокациям одного и того же `OrderLine` метод `PickingOperatorStrategy.executeStep` считывал строку заказа без блокировки, вычислял новый `deliveredQuantity = currentDelivered + pickedQuantity` и сохранял сущность. При одновременном выполнении происходил классический Lost Update: одно из обновлений бесследно затирало другое.
* **Где скрывалась:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/service/allocation/PickingOperatorStrategy.java`, `com/isd/wms/repository/OrderLineRepository.java`.
* **Как устранено:**
  Вместо оптимистической блокировки (которая приводила бы к отмене работы оператора ошибкой 409) внедрена сериализация через пессимистическую блокировку на запись:
  1. В `OrderLineRepository` добавлены методы:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT ol FROM OrderLine ol WHERE ol.task.id = :taskId")
     Optional<OrderLine> findByTaskIdWithLock(@Param("taskId") Long taskId);

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT ol FROM OrderLine ol WHERE ol.id = :id")
     Optional<OrderLine> findByIdWithLock(@Param("id") Long id);
     ```
  2. В `PickingOperatorStrategy` получение `OrderLine` переведено на `findByTaskIdWithLock`, гарантируя, что оба отбора применятся последовательно без потерь количества.
* **Тест:** `OrderLinePickingConcurrencyIntegrationTest.java` (многопоточный тест с одновременным завершением отбора двумя потоками через `CyclicBarrier`; итоговый `deliveredQuantity` равен точно сумме обоих отборов).

---

### 1.15. [B-3] Обеспечение монополии складской ячейки при параллельном размещении товаров
* **Коммит:** `da8d654`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Фундаментальный инвариант WMS гласит: ячейка склада монопольна и не может одновременно содержать остатки разных товаров. Констрейнт `uk_stocks_product_location` защищал только от дублирования одного и того же товара. Если два оператора одновременно размещали товар `A` и товар `B` в одну свободную ячейку, оба потока одновременно фиксировали отсутствие остатков (`stocks.isEmpty() == true`) и вставляли записи, приводя к физическому захвату одной ячейки двумя разными артикулами.
* **Где скрывалась:** `InventoryService.java`, `ReplenishmentOperatorStrategy.java`, `LocationRepository.java`.
* **Как устранено:**
  1. Создана миграция Flyway `V34__add_unique_constraint_stock_active_location.sql` с частичным уникальным индексом:
     ```sql
     CREATE UNIQUE INDEX IF NOT EXISTS uk_stocks_active_location
         ON stocks (location_id)
         WHERE available = true;
     ```
  2. В `LocationRepository` добавлен метод `findByIdWithLock(Long id)` с `LockModeType.PESSIMISTIC_WRITE`.
  3. В `InventoryService.addStock` и `ReplenishmentOperatorStrategy` перед проверкой доступности ячейки захватывается блокировка строки `Location` в БД.
* **Тест:** `LocationProductExclusivityConcurrencyIntegrationTest.java` (10 параллельных потоков пытаются разместить разные продукты в одну локацию: ровно 1 размещается успешно, остальные 9 потоков детерминированно отклоняются).

---

### 1.16. [B-4] Предотвращение гонки между списанием остатков и отбором аллокаций
* **Коммит:** `edb5a9b`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Существовало состояние гонки реального времени: супервайзер через `InventoryAdjustmentApplier` списывал испорченный товар и отменял аллокации, в то время как оператор через `AllocationExecutionService.completeAllocation` завершал физический отбор. При параллельном исполнении оператор мог подтвердить отбор по уже отмененной аллокации, либо `Stock.reservedQuantity` списывался повторно, уходя в отрицательные значения.
* **Где скрывалась:** `AllocationExecutionService.java`, `InventoryAdjustmentPlanner.java`, `InventoryAdjustmentApplier.java`, `AllocationRepository.java`.
* **Как устранено:**
  1. В `AllocationRepository` добавлены методы блокировки `findByIdWithLock` и `findActiveByStockIdWithLock`.
  2. В `AllocationExecutionService.completeAllocation` аллокация загружается через `findByIdWithLock` и проверяется инвариант терминальности (`if (allocation.getStatus() == Status.CANCELED) throw new InvalidRequestException(...)`).
  3. В `InventoryAdjustmentPlanner` и `InventoryAdjustmentApplier` при отмене аллокаций предварительно захватываются пессимистические блокировки на все активные аллокации данного стока.
* **Тест:** `AllocationAdjustmentConcurrencyIntegrationTest.java` (параллельное состязание отбора и списания остатков).

---

### 1.17. [AI-1] Контур авторизации и двухфазное подтверждение для мутирующих AI-инструментов
* **Коммит:** `6ceb759`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Инструменты AI-ассистента (`ChatbotService`) предоставляли возможность мутировать состояние склада (отмена заказов, перемещение остатков) без разграничения привилегий и без подтверждения оператором. Скомпрометированный промпт или галлюцинация LLM могли привести к неконтролируемому удалению или перемещению критических складских ресурсов.
* **Где скрывалась:** `ChatbotService.java`, `OrderAiTools.java`, `InventoryAiTools.java`, `WarehouseAiTools.java`.
* **Как устранено:**
  1. Мутирующие методы изолированы в отдельные классы (`OrderMutatingAiTools`, `InventoryMutatingAiTools`), а безопасные инструменты оставлены в read-only домене.
  2. Создан защитный компонент `AiToolSecurityBoundary`, валидирующий наличие ролей `ROLE_SUPERVISOR` или `ROLE_DEV`.
  3. Внедрен протокол двухфазного подтверждения с криптографическим токеном: вызов мутации формирует `confirmation_token`, и только повторный запрос с этим токеном применяет изменения в БД.
* **Тест:** `AiToolSecurityBoundaryTest.java`, `InventoryAiToolsSecurityTest.java`, `OrderAiToolsSecurityTest.java`.

---

### 1.18. [AI-2] Авторизация на уровне объектов (BOLA / IDOR) и валидация доменных зон в AI-инструментах
* **Коммит:** `75cc3fa`
* **Статус:** `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]`
* **В чём заключалась уязвимость:**
  Проверка общей роли `ROLE_SUPERVISOR` в AI-инструментах не защищала от атак класса BOLA/IDOR: один супервайзер мог отменить чужой заказ или пополнение, передать задачу пользователю без роли `ROLE_OPERATOR`, либо переместить товар в технологически несовместимую зону (например, `DISPATCH`).
* **Где скрывалась:** `AiToolSecurityBoundary.java`, `OrderMutatingAiTools.java`, `InventoryMutatingAiTools.java`, `ReplenishmentAiTools.java`, `OrderRepository.java`.
* **Как устранено:**
  1. В `OrderRepository` добавлен запрос `findSupervisorUsernamesByOrder(Long orderId)`.
  2. В `AiToolSecurityBoundary` реализованы методы проверки владения объектом: `enforceOrderAccess(orderId)` и `enforceReplenishmentAccess(replenishmentId)`.
  3. Внедрен метод `enforceTargetOperator(username)`, проверяющий существование, активность и наличие роли `ROLE_OPERATOR`.
  4. Добавлена валидация целевых зон `validateDestinationZone(Location loc, Zone expectedZone)`.
* **Тест:** `AiToolObjectLevelAuthorizationTest.java` (8 сценариев проверки авторизации на уровне объектов).

---

# ЧАСТЬ 2. СИСТЕМА КОНТЕКСТНОГО ПОИСКА (`wms-code-rag`)

---

### 2.1. [RAG-SEC-01] Аутентификация MCP и устранение утечки токенов через Query (CWE-598, CWE-208)
* **Коммиты:** `64da379`, `cf2b2b1`
* **В чём заключалась уязвимость:**
  Токен передавался в URL (`?token=secret`), оседая в access-логах. Проверка токена осуществлялась не константным сравнением строк. Деструктивный реиндекс не требовал аутентификации.
* **Где скрывалась:** `wms-code-rag/src/mcp_server.py`.
* **Как устранено:**
  Запрещена передача в query; приём токена строго через `Authorization: Bearer` или `X-API-Key`; валидация через `hmac.compare_digest`; реиндекс требует `confirm=True` и `auth_token`.
* **Тест:** `tests/test_finding_01_auth.py`.

---

### 2.2. [RAG-SEC-02] Санитизация секретов, паролей, ключей RSA и исключение чувствительных файлов (CWE-312)
* **Коммиты:** `35970d2`, `a5ee9ca`
* **В чём заключалась уязвимость:**
  Файлы настроек и миграций содержали открытые пароли БД и приватные ключи, которые попадали в открытую базу ChromaDB.
* **Где скрывалась:** `src/chunker.py` (`sanitize_secrets`), `src/indexer.py` (`secret_file_patterns`).
* **Как устранено:**
  Введен многоуровневый фильтр маскирования (`[REDACTED_SECRET]`, `[REDACTED_PRIVATE_KEY]`) для `.properties`, `.yaml`, SQL и RSA-блоков. Краулер индексатора превентивно игнорирует файлы `.env`, `.pem`, `.key`, `keystore`, а также папки `.ssh`, `certificates`.
* **Тест:** `tests/test_finding_02_secrets.py`.

---

### 2.3. [RAG-DATA-01] Ликвидация Ghost Chunks и прунинг устаревших данных
* **Коммит:** `7697bd0`
* **В чём заключалась неточность:**
  ID чанков формировались по строкам (`file:start:end`). Сдвиг строк при редактировании файла оставлял старые чанки в базе навечно ("призраки"), дублируя выдачу.
* **Где скрывалась:** `src/chunker.py`, `src/indexer.py`.
* **Как устранено:**
  ID формируется из MD5-хеша нормализованного контента символа. В индексатор добавлен шаг сверки (`prune_stale_chunks`), удаляющий из ChromaDB все чанки, удаленные или переименованные в коде.
* **Тест:** `tests/test_finding_03_ghost_chunks.py`.

---

### 2.4. [RAG-SEC-03] Защита от косвенных Prompt Injection и изоляция контекста
* **Коммиты:** `1005f2a`, `317431e`
* **В чём заключалась уязвимость:**
  Недоверенный код из репозитория мог содержать закрывающие теги `</untrusted_code_snippet>` или системные директивы, сбивающие LLM с роли.
* **Где скрывалась:** `src/retriever.py` (`format_for_agent`).
* **Как устранено:**
  Введен структурный тег `<untrusted_wms_codebase_context>` с явным машинным указанием инварианта безопасности, регистронезависимая экранизация закрывающих тегов и динамический расчет бэктиков `fence` (длиннее любой цепочки бэктиков в исходном коде).
* **Тест:** `tests/test_finding_04_prompt_injection.py`.

---

### 2.5. [RAG-ALG-01] Устранение несоответствия доменов скоров при фолбэке реранкера
* **Коммиты:** `d866363`, `870b1fe`
* **В чём заключалась неточность:**
  Если cross-encoder не нашел совпадений, система откатывалась на векторные результаты, но фильтровала их по отрицательному порогу cross-encoder (`min_score = -7.0`). Косинусная близость `[0..1]` всегда больше `-7.0`, из-за чего клиенту возвращался случайный шум.
* **Где скрывалась:** `src/retriever.py` (`retrieve`).
* **Как устранено:**
  Фолбэк строго проверяет косинусный порог `similarity_threshold = 0.10`, гарантированно отсекая мусор.
* **Тест:** `tests/test_finding_05_threshold_fallback.py`.

---

### 2.6. [RAG-DOS-01] DoS-защита запросов и неблокирующая потокобезопасная реиндексация
* **Коммит:** `fe7c4fb`
* **В чём заключалась уязвимость:**
  Сверхдлинные строки запросов перегружали эмбеддер; параллельный запуск реиндекса повреждал файлы базы данных.
* **Где скрывалась:** `src/mcp_server.py`, `src/indexer.py`.
* **Как устранено:**
  Длина запроса ограничена `MAX_QUERY_LENGTH = 1000`, `top_n` ограничен диапазоном `[1, 20]`. В `CodebaseIndexer` встроен `threading.Lock().acquire(blocking=False)`, выбрасывающий мгновенную ошибку при попытке параллельного реиндекса.
* **Тест:** `tests/test_finding_06_dos_and_concurrency.py`.

---

### 2.7. [RAG-SEC-04] Защита от Symlink Path Traversal и Read-Only изоляция хоста
* **Коммиты:** `bd34f88`, `d0268e2`
* **В чём заключалась уязвимость:**
  Симлинки внутри репозитория могли вести на файловую систему хоста. В Docker-compose код монтировался на запись.
* **Где скрывалась:** `src/indexer.py`, `docker-compose.yml`.
* **Как устранено:**
  Проверка `candidate_file.resolve().is_relative_to(resolved_target)` с пропуском любых внешних симлинков. Исходный код WMS монтируется в Docker с флагом `:ro` (read-only).
  Все деструктивные тесты (`clear_first=True`, удаление, переиндексация) изолированы в индивидуальные временные каталоги `tmp_path / "chroma_..."`. В `CodeVectorStore._assert_safe_mutation()` встроен защитный барьер, предотвращающий случайную модификацию боевого индекса `data/chroma` во время прогона тестов с выбросом `RuntimeError`.
* **Тест:** `tests/test_finding_07_ro_mounts.py` (`test_docker_compose_mounts_are_read_only`, `test_indexer_skips_symlinks_escaping_codebase_root`, `test_clear_first_operates_only_on_isolated_temporary_directory`, `test_production_chroma_mutation_guard_blocks_accidental_destruction`).

---

### 2.8. [RAG-PARSE-01] Устранение обрывов парсеров Vue 3 и процедур PostgreSQL PL/pgSQL
* **Коммиты:** `4e9b47d`, `73e7768`
* **В чём заключалась неточность:**
  Vue-шаблоны с атрибутами (`#header`, `lang="ts"`) отбрасывались; закрывающий тег `</template>` в комментарии обрывал парсинг. SQL-парсер дробил хранимые процедуры на куски по внутренним точкам с запятой.
* **Где скрывалась:** `src/chunker.py` (`_chunk_vue`, `_chunk_sql`).
* **Как устранено:**
  Vue-парсер поддерживает произвольные атрибуты и игнорирует теги внутри комментариев/строк. SQL-парсер отслеживает долларовые блоки PL/pgSQL (`$$...$$`), сохраняя процедуры целостными.
* **Тест:** `tests/test_finding_08_parser.py`.

---

### 2.9. [RAG-DATA-02] Предотвращение фабрикации сущностей и галлюцинаций в схемах
* **Коммиты:** `41763e6`, `478c474`
* **В чём заключалась неточность:**
  Запрос схемы несуществующей таблицы через фолбэк возвращал случайные существующие таблицы (например, `users`), вынуждая агента галлюцинировать.
* **Где скрывалась:** `src/mcp_server.py` (`get_entity_and_schema`).
* **Как устранено:**
  Внедрен фильтр Entity Relevance Verification со стеммингом и проверкой границ слов (`\b`), гарантирующий возврат только релевантных сущностей.
* **Тест:** `tests/test_finding_09_entity_schema.py`.

---

### 2.10. [RAG-PROTO-01] Чистота потока stdio для JSON-RPC (FastMCP)
* **Коммит:** `96488bb`
* **В чём заключалась уязвимость:**
  Вызовы `print()` и стороннее логирование шли в `stdout`, ломая JSON-RPC сообщения протокола FastMCP.
* **Где скрывалась:** `src/indexer.py`, `src/mcp_server.py`.
* **Как устранено:**
  Весь вывод логов и Rich Console перенаправлен строго в `sys.stderr`.
* **Тест:** `tests/test_finding_10_stdio_cleanliness.py`.

---

### 2.11. [RAG-PROTO-02] Развязка с приватными API FastMCP и Auto-Heal сессий
* **Коммиты:** `323200d`, `5a27f9d`
* **В чём заключалась уязвимость:**
  Привязка к `mcp._server_instances` ломалась при обновлении библиотеки; протухшие сессии возвращали 404 клиентам.
* **Где скрывалась:** `src/mcp_server.py` (`SessionAutoHealMiddleware`).
* **Как устранено:**
  Использование публичных методов API и автоматическое удаление неактивного заголовка `mcp-session-id`.
* **Тест:** `tests/test_finding_11_fastmcp_coupling.py`.

---

### 2.12. [RAG-RET-01] Устранение слепых зон Java: интерфейсы, абстрактные методы, Spring Data JPA, Records
* **Коммит:** `b5b5fbb`
* **В чём заключалась фундаментальная неточность:**
  Парсер Java требовал фигурные скобки `{...}`. Методы интерфейсов, абстрактных классов и Spring Data JPA репозиториев (заканчивающиеся на `;`) пропускались. В `StockRepository.java` 40 строк кода с методами `@Query` отсутствовали в индексе.
* **Где скрывалась:** `src/chunker.py` (`_chunk_java`).
* **Как устранено:**
  Добавлено распознавание сигнатур без тела с завершением на `;`, захват многострочных `@Query("""...""")`, поддержка компактных конструкторов рекордов (`RecordName { ... }`) и вложенных типов (`inner_name`).
* **Тест:** `tests/test_declaration_and_symbol_lookup.py`.

---

### 2.13. [RAG-RET-02] Детерминированный точный поиск символов (`find_symbol_declaration`)
* **Коммит:** `b5b5fbb`
* **В чём заключалась фундаментальная неточность:**
  Семантический поиск по эмбеддингам не может служить оракулом существования: запрос несуществующего класса `InventoryReallocationStrategy` возвращал высокий скор `0.655` на существующих классах аллокации, приводя к галлюцинациям агента о существовании класса.
* **Где скрывалась:** Архитектурное смешение семантического поиска и верификации фактов.
* **Как устранено:**
  Добавлен выделенный инструмент FastMCP `find_symbol_declaration(symbol_name)` для поиска по метаданным. Формулировка `NOT_FOUND` строго ограничена: «Не найдено в индексированном коде WMS».
* **Тест:** `tests/test_declaration_and_symbol_lookup.py`.

---

### 2.14. [RAG-RET-03] Кэш символов, авто-инвалидация, перегрузки и строгий запрет Fuzzy
* **Коммит:** `b5b5fbb`
* **В чём заключалась неточность:**
  Раздельные инстансы `CodeVectorStore` могли приводить к рассинхронизации кэша; требовалась корректная обработка перегрузок методов и одноименных классов в разных пакетах.
* **Где скрывалась:** `src/vector_store.py`, `src/mcp_server.py`.
* **Как устранено:**
  1. `mcp_server.py` объединяет `indexer.store` и `retriever.store` в один общий инстанс `CodeVectorStore`.
  2. Внутрипроцессные мутации (`add_chunks`, `delete_chunks_by_ids`, `clear`, `invalidate_symbol_cache`) немедленно сбрасывают локальный кэш `_symbol_cache = None`.
  3. Внешние и межпроцессные изменения отслеживаются через композитную ревизию хранилища:
     - Персистентный маркер ревизии (`.index_rev` в директории `persist_dir`), обновляемый при каждой мутации любого инстанса.
     - Отпечаток времени модификации файла базы данных SQLite (`chroma.sqlite3` mtime).
     - Динамический счетчик коллекции (`collection.count()`).
     При несовпадении сохраненной ревизии кэш автоматически и детерминированно перестраивается (`_rebuild_symbol_cache`), предотвращая возврат устаревших данных даже при совпадении общего количества чанков.
  4. Бакеты хранят списки `List[CodeChunk]`, возвращая все перегрузки (`FOUND (N declarations)`).
  5. Поиск выполняется строго через хэш-словари Python, исключая подстроки (`Orde` не находит `Order`).
  6. **Границы согласованности (Consistency Boundary):**
     - *Гарантируется:* Полная строгая согласованность в рамках единого FastMCP-процесса и согласованность между несколькими локальными процессами/инстансами на общей файловой системе хранилища ChromaDB.
     - *НЕ гарантируется:* Распределенный сетевой консенсус между изолированными нодами без общей файловой системы, а также чтение незафиксированных транзакций SQLite до сброса на диск.
* **Тест:** `tests/test_declaration_and_symbol_lookup.py` (`test_symbol_cache_edge_cases_and_invalidation`, `test_symbol_cache_normal_lifecycle`, `test_symbol_cache_incremental_reindex_pruning`, `test_symbol_cache_same_count_replacement_across_instances`, `test_production_mcp_shared_store_identity`, `test_symbol_cache_exactness_invariants`).

---

### 2.15. [RAG-RET-04] Разграничение контрактов инструментов MCP
* **Коммит:** `b5b5fbb`
* **В чём заключалась неточность:**
  Агенты путали назначение инструментов, пытаясь использовать `search_wms_code` для проверки наличия классов.
* **Где скрывалось:** Документация и контракты инструментов в `src/mcp_server.py`.
* **Как устранено:**
  В docstring `search_wms_code` четко зафиксировано: отвечает на вопрос *"Какой код концептуально релевантен задаче?"*, а `find_symbol_declaration` — *"Объявлен ли данный точный символ в индексированном коде, и где?"*.

---

# ЧАСТЬ 3. РЕЕСТР ОТКРЫТЫХ ДЕФЕКТОВ И ROADMAP УСТРАНЕНИЯ (SECURITY & ARCHITECTURE BACKLOG)

---

### 3.1. Сводная матрица трассируемости аудита (Audit Traceability Matrix)

| Код | Исходное наименование finding | Домен | Критичность | Статус | Подтверждающий коммит / План решения |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **S-1** | Inactive users authentication bypass | Security | Critical | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `b1169d5` / `AuthServiceTest` |
| **S-2** | Inactive supervisor self-reactivation via `/register` | Security | Critical | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `2e1f0e9` / `UserServiceReactivationSecurityTest` |
| **S-3** | Hardcoded credentials and insecure secret fallbacks | Security | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `79b229b`, `5b445a3` / `JwtUtilTest` |
| **S-4** | Overly broad CORS trust boundary (`allowedOriginPatterns("*")`) | Security | High | `[BACKLOG] PENDING` | Ограничение списком явных доверенных origins |
| **S-5** | JS-readable access-token storage in `localStorage` | Security | Medium | `[BACKLOG] PENDING` | Ревизия auth-flow и отказ от JS-доступного storage |
| **B-1** | Active orders destroyed by scheduled DB cleanup | Data Integrity | Critical | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `d61e887` / `DataCleanupProtectionIntegrationTest` |
| **B-2** | Lost update during order picking (`OrderLine`) | Concurrency | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `b131be1` / `OrderLinePickingConcurrencyIntegrationTest` |
| **B-3** | Cell/location monopoly race | Concurrency | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `da8d654` / `LocationProductExclusivityConcurrencyIntegrationTest` |
| **B-4** | Allocation vs inventory adjustment race | Concurrency | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `edb5a9b` / `AllocationAdjustmentConcurrencyIntegrationTest` |
| **B-5** | Concurrent stock reservation / allocation integrity | Concurrency | High | `[BACKLOG] PENDING` | Анализ конкурентных путей резервации/аллокации остатков |
| **D-1** | Destructive Flyway migrations (`TRUNCATE TABLE ... CASCADE`) | DB Integrity | Critical | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `ede81dc` / Идемпотентные миграции V19, V31 |
| **D-2** | Stock/Location mapping integrity | DB Integrity | Medium | `[BACKLOG] PENDING` | Аудит маппинга сущностей Stock/Location и консистентности JPA |
| **D-3** | `logic_id` uniqueness | DB Integrity | Medium | `[BACKLOG] PENDING` | Обеспечение уникальности логических бизнес-идентификаторов |
| **D-4** | Missing FK indexes | Performance | Medium | `[BACKLOG] PENDING` | Индексация внешних ключей для ликвидации Seq Scan |
| **D-5** | N+1 query problem | Performance | Medium | `[BACKLOG] PENDING` | Оптимизация fetch-стратегий и ликвидация N+1 в запросах JPA |
| **AI-1** | Authorization boundary & confirmation for mutating AI tools | AI Security | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `6ceb759` / `AiToolSecurityBoundaryTest` |
| **AI-2** | Object-level authorization (BOLA/IDOR) in AI tools | AI Security | High | `[IMPLEMENTED — AWAITING ADVERSARIAL VERIFICATION]` | `75cc3fa` / `AiToolObjectLevelAuthorizationTest` |
| **F-1** | Missing centralized 401/403/409 interceptors in frontend | Frontend UX | Medium | `[BACKLOG] PENDING` | Централизованные интерцепторы Axios во фронтенде |

---

### 3.2. Детальный реестр дефектов в бэклоге (Roadmap)

#### 3.2.1. [S-4] Overly broad CORS trust boundary (`allowedOriginPatterns("*")`)
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** High
* **Домен:** Security / Network Boundary
* **Где находится:** `inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/config/WebConfig.java`, `SecurityConfig.java`.
* **Суть проблемы:**
  `allowedOriginPatterns("*")` combined with credentials creates an overly broad cross-origin trust boundary and must be restricted to explicit trusted origins. Конфигурация с wildcard-паттерном при разрешенной передаче учетных данных (`allowCredentials(true)`) динамически отражает заголовок `Origin` клиента в `Access-Control-Allow-Origin`, открывая возможность межсайтовых запросов из недоверенных источников.
* **Целевое решение:**
  Перевести список разрешенных origins на строго типизированное свойство конфигурации окружения (`wms.cors.allowed-origins`) с валидацией доверенных хостов (localhost для разработки, конкретный FQDN домен для продакшена).

#### 3.2.2. [S-5] JS-readable access-token storage in `localStorage`
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Frontend Security / Session Management
* **Где находится:** `inbound-storage-dispatch/wmsFront/src/` (хранилища Pinia, модули авторизации).
* **Суть проблемы:**
  Фронтенд сохраняет JWT-токен в постоянном хранилище браузера `localStorage.setItem("token", ...)`. При возникновении XSS-уязвимости в сторонних NPM-зависимостях или UI-компонентах токен доступа может быть прочитан скриптом злоумышленника.
* **Целевое решение:**
  *Target architecture: migrate away from JS-readable persistent access-token storage; exact mechanism to be selected after auth-flow review.* Перед реализацией требуется комплексный анализ auth flow: жизненный цикл access/refresh токенов, влияние на CORS, CSRF-последствия при использовании кук и требования API-клиентов.

#### 3.2.3. [B-5] Concurrent stock reservation / allocation integrity
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** High
* **Домен:** Business Logic Concurrency
* **Где находится:** Сервисы аллокации и резервации стока (`AllocationService.java`, `StockRepository.java`).
* **Суть проблемы:**
  Потенциальная уязвимость к состоянию гонки при одновременной резервации остатков под несколько крупных заказов: риск оверселлинга (overselling) или отрицательного доступного остатка (`quantity - reservedQuantity < 0`), если проверка доступного остатка и его резервирование не сериализованы атомарно.
* **Целевое решение:**
  Аудит путей резервирования, оценка применения пессимистической блокировки `PESSIMISTIC_WRITE` на записи `Stock` при поиске доступных партий либо атомарного DB-уровневого декремента с проверкой констрейнтов.

#### 3.2.4. [D-2] Stock/Location mapping integrity
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Database Schema / Entity Mapping
* **Где находится:** `Stock.java`, `Location.java`, JPA репозитории и схема PostgreSQL.
* **Суть проблемы:**
  Несоответствие и расхождения между JPA-маппингом сущностей `Stock` и `Location` и реальными реляционными связями в схеме БД. Риск рассинхронизации ссылок при удалении или изменении статуса локаций.
* **Целевое решение:**
  Аудит реляционных связей, валидация внешних ключей и консистентности каскадных политик (`CascadeType`, `orphanRemoval`) на уровне ORM и DDL.

#### 3.2.5. [D-3] `logic_id` uniqueness
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Data Integrity
* **Где находится:** Схема базы данных, сущности со сквозной логической идентификацией.
* **Суть проблемы:**
  Отсутствие уникальных ограничений (UNIQUE constraints) на колонках `logic_id` в таблицах WMS, что создает риск появления записей-дубликатов с одинаковым бизнес-идентификатором в рамках одного склада.
* **Целевое решение:**
  Анализ использования `logic_id` в предметной области WMS, добавление уникальных индексов Flyway и валидация на уровне сервисного слоя.

#### 3.2.6. [D-4] Missing FK indexes
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Performance / Database
* **Где находится:** Миграции Flyway (`db/migration`), внешние ключи таблиц `orders`, `order_lines`, `allocations`, `stocks`, `replenishments`.
* **Суть проблемы:**
  В PostgreSQL создание внешнего ключа (`FOREIGN KEY`) автоматически не создает B-Tree индекс на дочерней таблице. При выполнении JOIN-запросов и каскадных проверках целостности СУБД вынуждена выполнять полное последовательное сканирование (Sequential Scan), что приводит к резкому росту задержек при увеличении объема записей.
* **Целевое решение:**
  Формирование миграции Flyway для создания покрывающих индексов на всех внешних ключах WMS-схемы.

#### 3.2.7. [D-5] N+1 query problem
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Performance / ORM
* **Где находится:** REST контроллеры и сервисы выборки заказов, строк заказов и задач (`OrderService`, `AllocationExecutionService`).
* **Суть проблемы:**
  Ленивая загрузка (`FetchType.LAZY`) связей `@ManyToOne` и `@OneToMany` при выборке списков заказов или задач приводит к лавинообразному выполнению отдельных SQL-запросов на каждую строку (классический дефект N+1), многократно перегружая пул соединений с БД.
* **Целевое решение:**
  Профилирование SQL-логов, применение `JOIN FETCH` в запросах Spring Data JPA или использование `@EntityGraph` для пакетной загрузки связанных сущностей в одном запросе.

#### 3.2.8. [F-1] Missing centralized 401/403/409 interceptors in frontend
* **Статус:** `[BACKLOG] PENDING`
* **Критичность:** Medium
* **Домен:** Frontend Architecture & UX
* **Где находится:** `inbound-storage-dispatch/wmsFront/src/api/` (Axios configuration).
* **Суть проблемы:**
  Отсутствие глобального интерцептора ответов в HTTP-клиенте фронтенда:
  - При `401 Unauthorized` (истечение срока JWT) пользователь не перенаправляется автоматически на страницу логина, а интерфейс начинает возвращать каскадные ошибки.
  - При `403 Forbidden` отсутствует информативное уведомление о недостатке прав.
  - При `409 Conflict` (конкурентный конфликт параллелизма) интерфейс не сообщает пользователю о необходимости обновить данные, оставляя форму в зависшем состоянии.
* **Целевое решение:**
  Внедрение централизованного Axios response interceptor с обработкой кодов 401 (очистка сессии и редирект), 403 (уведомление о правах) и 409 (диалог обновления данных).

