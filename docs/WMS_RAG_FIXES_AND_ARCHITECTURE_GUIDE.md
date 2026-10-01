# Полный справочник архитектурных исправлений и безопасности проекта (WMS + Code RAG)

> **Статус документа:** Исчерпывающий реестр **всех** исправлений кодовой базы репозитория, охватывающий как ядро WMS (`inbound-storage-dispatch`), так и интеллектуальную систему контекстного поиска (`wms-code-rag`).  
> **Основание:** Анализ полного графа коммитов Git (`git log`), данных технических аудитов и результатов верификации.

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
* **Тест:** `tests/test_finding_07_ro_mounts.py`.

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
  1. `mcp_server.py` объединяет `indexer.store` и `retriever.store`.
  2. `CodeVectorStore` автоматически инвалидирует кэш при `self._symbol_cache_count != self.collection.count()`.
  3. Бакеты хранят списки `List[CodeChunk]`, возвращая все перегрузки (`FOUND (N declarations)`).
  4. Поиск выполняется строго через хэш-словари Python, исключая подстроки (`Orde` не находит `Order`).
* **Тест:** `tests/test_declaration_and_symbol_lookup.py` (`test_symbol_cache_edge_cases_and_invalidation`).

---

### 2.15. [RAG-RET-04] Разграничение контрактов инструментов MCP
* **Коммит:** `b5b5fbb`
* **В чём заключалась неточность:**
  Агенты путали назначение инструментов, пытаясь использовать `search_wms_code` для проверки наличия классов.
* **Где скрывалось:** Документация и контракты инструментов в `src/mcp_server.py`.
* **Как устранено:**
  В docstring `search_wms_code` четко зафиксировано: отвечает на вопрос *"Какой код концептуально релевантен задаче?"*, а `find_symbol_declaration` — *"Объявлен ли данный точный символ в индексированном коде, и где?"*.
