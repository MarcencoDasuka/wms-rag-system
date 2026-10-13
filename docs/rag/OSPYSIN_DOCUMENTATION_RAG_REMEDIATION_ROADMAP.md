# Ospysin Documentation RAG — Комплексное исследование и дорожная карта исправлений

**Дата исследования:** 13 октября 2026 г.  
**Рабочее дерево Git (исходное состояние):** Branch `main`, чистая синхронизация с `origin/main`. Незакоммиченные изменения:
- `modified:` `.gitignore`, `wms-code-rag/config.yaml`, `wms-code-rag/docker-compose.yml`, `wms-code-rag/src/config.py`, `wms-code-rag/src/mcp_server.py`, `wms-code-rag/src/retriever.py`
- `untracked:` `wms-code-rag/eval/`, `wms-code-rag/src/doc_chunker.py`, `wms-code-rag/src/doc_indexer.py`, `wms-code-rag/src/doc_service.py`, `wms-code-rag/tests/test_ospysin_docs.py`  
**Ревизия документации Ospysin:** `052701ec936f255ff03b8713a9294432942f4e57` (репозиторий `../Ospysin`, 21 файл спецификаций и ADR).  
**Целевой статус документа:** Инженерная дорожная карта и доказательная база для последующих фаз remediation. Никакие изменения в коде, конфигурациях и данных в рамках данной задачи не производились.

---

## 1. Executive Summary

Настоящий документ представляет собой комплексное, доказательное инженерное исследование текущей интеграции подсистемы **Ospysin Documentation RAG** в инфраструктуру сервера **WMS Code RAG**.

### 1.1. Ключевые результаты исследования
1. **Функциональная работоспособность:** Базовый контур интеграции **дееспособен и проверен в runtime**. В Docker-контейнере `wms-code-rag-server` сервер FastMCP успешно поднимается, регистрирует все 3 специализированных инструмента (`search_ospysin_docs`, `get_ospysin_doc`, `reindex_ospysin_docs`) и обрабатывает живые запросы через транспорт SSE.
2. **Изоляция векторных коллекций:** Инвариант изоляции между индексами склада WMS (`wms_codebase_knowledge`, 1826 чанков) и документации Ospysin (`ospysin_docs_knowledge`, 246 чанков) **полностью подтверждён**. Коллекции разнесены физически и логически; переиндексация и удаление чанков строго изолированы.
3. **Превосходство специализированного чанкера:** Новый [`MarkdownSpecChunker`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py#L81-L145) превосходит прежний легаси-чанкер WMS: на эталонном positive-наборе из 28 запросов достигнуты метрики **Hit@1 = 1.000, Hit@4 = 1.000, MRR = 1.000** (против Hit@1 = 0.893, MRR = 0.902 у baseline).
4. **Критические точки отказа и дефекты:**
   - **DEF-01 (Порог отсечения, P1):** Порог отсечения реранкера [`min_score: -7.0`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml#L30) унаследован из кода WMS и является чрезмерно мягким для документации. Ложные предпосылки (запросы про Kafka, Redis/Celery) и несуществующие функции (push-уведомления) получают скоры от `-3.5` до `-4.0` и проникают в контекст агента как валидные результаты. При отключении реранкера порог `similarity_threshold: 0.10` пропускает **100% нерелевантных запросов**.
   - **DEF-02 (Ревизия Git в Docker, P1):** В production Docker-контейнере утилита `git` отсутствует, а каталог `.git` не смонтирован. Метаданные `source_commit` перманентно равны `"unknown"`, нарушая инвариант трассируемости источника.
   - **DEF-03 (Тестовая среда, P1):** Разрыв между хостовым и контейнерным окружением: 5 файлов тестов WMS и 1 интеграционный тест падают на хосте из-за отсутствия пакета `mcp`, а в Docker тесты не смонтированы и `pytest` не установлен.
   - **DEF-04 (Производительность `get_doc`, P2):** Метод [`get_ospysin_doc`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L94-L152) при каждом вызове выгружает всю коллекцию (246 чанков) из ChromaDB в память Python, не используя фильтрацию `where={"doc_id": ...}`.
   - **DEF-05 (Семантика XML-разметки, P2):** Контекст документации выдаётся под тегом `<untrusted_wms_codebase_context>`, что дезориентирует внешние LLM-агенты.
   - **DEF-06 (Контракт CLI-флагов, P2):** Асимметрия флага `--auto-index`: фоновая индексация Ospysin стартует при пустой базе всегда, игнорируя флаг командной строки.
   - **DEF-07 (Маскирование ассертов в тестах, P2):** В тестах `test_ospysin_docs.py` проверка поиска содержит условие `or out.startswith("NO_MATCH")`, что делает тест "зелёным" даже при полном падении качества retrieval.
   - **DEF-08 (Отсутствие dev-контейнера, P2):** Отсутствует воспроизводимый профиль запуска тестов FastMCP внутри контейнера.
   - **DEF-09 (Устойчивость к ошибкам I/O, P3):** Падение чтения одного файла прерывает весь цикл индексации вместо изолированного логирования ошибки.

---

## 2. Scope, Methodology & Limitations

### 2.1. Границы исследования (Scope)
- **Исследуемые модули:** Вся подсистема Ospysin Docs RAG ([`doc_chunker.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py), [`doc_indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py), [`doc_service.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py)), точки сопряжения в [`config.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/config.py), [`config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml), [`mcp_server.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py), [`retriever.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py), контур развертывания ([`Dockerfile`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile), [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml)), тестовый набор [`tests/test_ospysin_docs.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py) и evaluation-модуль [`eval/run_eval.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/eval/run_eval.py).
- **Смежные модули (read-only аудит на совместимость):** Поисковик кода WMS ([`indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/indexer.py), [`chunker.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/chunker.py), [`vector_store.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/vector_store.py)), существующие тесты WMS.

### 2.2. Методология проверки
1. **Evidence-First & Zero Mutation:** Никаких правок кода, конфигов или сброса индексов. Все динамические проверки проводились либо через read-only вызовы живого MCP-сервера, либо во временных disposable-директориях (`tempfile.TemporaryDirectory`).
2. **Runtime Falsification:** Проверка утверждений предыдущего аудита прямыми обращениями к контейнеру через `docker exec`, проверкой файловых дескрипторов и инспекцией логов.
3. **Adversarial Retrieval Evaluation:** Создание независимой матрицы из 23 запросов по 12 ортогональным категориям сложности с фиксацией точных сырых скоров кросс-энкодера и векторного сходства.
4. **Статический анализ путей и границ безопасности:** Трассировка вызовов от точки входа MCP до базы ChromaDB, анализ регулярных выражений, экранирования и потоков данных.

### 2.3. Ограничения окружения (Limitations)
- На хостовой машине установлен Python 3.14.0 без пакета `mcp` (библиотека FastMCP не входит в системный Python). Вследствие этого тесты, импортирующие `src.mcp_server`, не могут быть собраны `pytest` на хосте без симуляции окружения.
- В боевом Docker-контейнере установлен Python 3.11 с пакетом `mcp`, но отсутствует тестовый фреймворк `pytest`, и каталог `tests/` не смонтирован. Запуск pytest внутри боевого контейнера невозможен без модификации образа.
- Доступные тесты запускались на хосте выборочно с изоляцией от несовместимых модулей.

---

## 3. Current Architecture

Подсистема документации спроектирована как надстройка над ядром WMS Code RAG, использующая разделяемый пул ресурсов (Embedder, Reranker, ChromaDB Client), но изолированные пространства хранения и сервисные слои.

```
                              +-------------------------------------------+
                              |      MCP Client / AI Assistant (LLM)     |
                              +-------------------------------------------+
                                                    |
                                          HTTP SSE / stdio
                                                    v
+-------------------------------------------------------------------------------------------------------+
|  wms-code-rag (FastMCP Server, port 8000)                                                             |
|                                                                                                       |
|   [WMS Code Tools]                                 [Ospysin Docs Tools]                               |
|   - search_wms_code                                - search_ospysin_docs                              |
|   - find_symbol_declaration                        - get_ospysin_doc                                  |
|   - get_entity_and_schema                          - reindex_ospysin_docs                             |
|   - search_wms_security                            - get_rag_status (комбинированный статус)          |
|   - reindex_wms_codebase                                                                              |
|           |                                                 |                                         |
|           v                                                 v                                         |
|     CodeRetriever                                   OspysinDocsService                                |
|     (WMS Code store)                                (Isolated docs logic)                             |
|           |                                          /              \                                 |
|           |                    CodeRetriever (docs store)      OspysinDocsIndexer                     |
|           |                                 |                           |                             |
|           |                                 |                 MarkdownSpecChunker                     |
|           |                                 |                 (Heading & Table aware)                 |
|           \_________________________________+___________________________/                             |
|                                             |                                                         |
|                       +---------------------+---------------------+                                   |
|                       | Shared Embedder     | Shared Reranker     |                                   |
|                       | (all-MiniLM-L6-v2)  | (ms-marco-MiniLM)   |                                   |
|                       +---------------------+---------------------+                                   |
|                                             |                                                         |
|                       +---------------------+---------------------+                                   |
|                       | PersistentClient (data/chroma)            |                                   |
|                       +-------------------------------------------+                                   |
|                          /                                     \                                      |
|            Collection: wms_codebase_knowledge    Collection: ospysin_docs_knowledge                  |
|            (Java, Vue, SQL chunks)               (Markdown Spec & ADR chunks)                         |
+-------------------------------------------------------------------------------------------------------+
```

### 3.1. Ключевые компоненты
- **[`doc_chunker.py:MarkdownSpecChunker`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_chunker.py#L81):** Heading-aware парсер спецификаций Markdown. Строит стек заголовков H1–H6, префиксирует каждый чанк строкой хлебных крошек `[rel_path | H1 > H2 > H3]`. Обеспечивает атомарность таблиц и блоков кода, повторяет заголовок Markdown-таблицы при разбиении, переоткрывает code fences.
- **[`doc_indexer.py:OspysinDocsIndexer`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L52):** Сканирует каталог спецификаций (`../Ospysin/docs`), фильтрует симлинки, скрытые каталоги и посторонние расширения, генерирует детерминированные SHA-256 chunk ID, рассчитывает векторные эмбеддинги пакетами по 32 элемента, выполняет upsert и удаляет устаревшие чанки через `prune_stale_chunks()`. Записывает метаданные индексации в `.ospysin_index_meta.json`.
- **[`doc_service.py:OspysinDocsService`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L55):** Сервисный слой без зависимостей от FastMCP (для прямого модульного тестирования). Предоставляет семантический поиск с проверкой пустого индекса, детерминированный поиск документа/секции `get_doc()` с нормализацией ключей (`adr-3`, `05`, `tool-catalog`) и безопасную переиндексацию с флагом `confirm=True`.
- **[`mcp_server.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py):** Точка входа сервера. При `config.ospysin.enabled: true` инициирует экземпляр `ospysin_service` и регистрирует 3 инструмента с валидацией длины запросов (`validate_query`) и ограничением диапазона возвращаемых чанков (`clamp_top_n`).

---

## 4. Current Runtime and Configuration State

### 4.1. Фактический статус контейнера и портов
Команда `docker ps` подтверждает работу контейнера:
```
CONTAINER ID   IMAGE                       STATUS                    PORTS                      NAMES
754461787fe6   wms-code-rag-wms-code-rag   Up 10 minutes (healthy)   127.0.0.1:8000->8000/tcp   wms-code-rag-server
```
- Endpoint `/health`: отвечает HTTP 200 OK.
- Transport: HTTP SSE на `http://127.0.0.1:8000/sse`.

### 4.2. Фактическое состояние хранилищ ChromaDB
Через прямой вызов `get_rag_status()` получены параметры обоих индексов:
- **WMS Codebase Index:**
  - Collection: `wms_codebase_knowledge`
  - Total Indexed Chunks: **1826**
  - Embedding Model: `sentence-transformers/all-MiniLM-L6-v2`
  - Reranker: `cross-encoder/ms-marco-MiniLM-L-6-v2` (Enabled: True)
  - ChromaDB Path: `/app/data/chroma`
- **Ospysin Docs Index:**
  - Collection: `ospysin_docs_knowledge`
  - Docs Dir: `/workspace/ospysin-docs`
  - Total Indexed Chunks: **246**
  - Source Revision: **`unknown`** *(подтверждение DEF-02)*
  - Indexed At: `2026-10-13T11:12:17.960814+00:00`

### 4.3. Конфигурационные файлы
Файл [`wms-code-rag/config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml):
```yaml
codebase:
  target_dir: "../inbound-storage-dispatch"
  # ...
embeddings:
  default_model: "sentence-transformers/all-MiniLM-L6-v2"
  device: "cpu"

reranking:
  enabled: true
  model_name: "cross-encoder/ms-marco-MiniLM-L-6-v2"
  top_n: 4
  min_score: -7.0   # Общий порог для WMS и Ospysin (DEF-01)

vector_db:
  persist_dir: "data/chroma"
  collection_name: "wms_codebase_knowledge"

retrieval:
  default_top_k: 12
  similarity_threshold: 0.10

server:
  host: "127.0.0.1"
  port: 8000
  auth_token: ""

ospysin:
  enabled: true
  docs_dir: "../Ospysin/docs"
  collection_name: "ospysin_docs_knowledge"
  chunk_max_chars: 1200
  chunk_overlap_chars: 150
```

---

## 5. Verified Working Components

Следующие элементы интеграции **прямо проверены исполнением тестов, команд и runtime-вызовов**:

1. **Регистрация инструментов в FastMCP (`[VERIFIED]`):**
   - Вызов `asyncio.run(mcp.list_tools())` в Docker вернул полный список из 9 инструментов.
2. **Семантический поиск `search_ospysin_docs` (`[VERIFIED]`):**
   - Запрос `"SSRF protection policy"` вернул релевантные выдержки из `08-THREAT-MODEL.md` (score 0.567) и `07-POLICY-ENGINE.md` (score 0.482).
3. **Детерминированный поиск `get_ospysin_doc` (`[VERIFIED]`):**
   - `get_ospysin_doc("adr-1")` возвращает все 6 чанков ADR 0001 с меткой `relevance="exact"`.
   - `get_ospysin_doc("5", "dns_lookup")` точно изолирует спецификацию утилиты `dns_lookup`.
   - `get_ospysin_doc("model")` возвращает маркер неоднозначности `AMBIGUOUS` со списком кандидатов (`03-DOMAIN-MODEL.md`, `08-THREAT-MODEL.md`, `11-DATA-MODEL.md`).
   - `get_ospysin_doc("5", "non_existent")` возвращает `SECTION_NOT_FOUND` с алфавитным перечнем доступных секций.
4. **Защита от Path Traversal (`[VERIFIED]`):**
   - Попытки передачи `../../etc/passwd` и `..\..\..\windows\win.ini` возвращают `NOT_FOUND` и список легитимных файлов спецификации.
5. **Безопасность разметки и экранирование инъекций (`[VERIFIED]`):**
   - Входные параметры санитизируются через `_esc()` (устранение `<` и `>`).
   - Попытки преждевременного закрытия тегов `</untrusted_wms_codebase_context>` нейтрализуются регулярными выражениями с экранированием слэша.
   - Двойные/тройные обратные кавычки в теле документов динамически оборачиваются внешним блоком кавычек увеличенной длины (`max_backticks + 1`).
6. **Read-Only Mount в Docker (`[VERIFIED]`):**
   - Попытка создания файла внутри смонтированного каталога документации `touch /workspace/ospysin-docs/test_write` отклонена ядром Linux с ошибкой `Read-only file system`.
7. **Защита от случайной очистки боевого индекса (`[VERIFIED]`):**
   - Метод `_assert_safe_mutation()` в `vector_store.py` прерывает выполнение тестов с `RuntimeError`, если тест пытается выполнить мутацию боевой директории `data/chroma`.
8. **Независимость коллекций (`[VERIFIED]`):**
   - Вызовы `store.clear()` для WMS и Ospysin адресуют строго свои коллекции.

---

## 6. Complete Findings Register

| ID | Краткое название | Статус | Приоритет | Уверенность | Затронутый компонент |
|:---|:---|:---:|:---:|:---:|:---|
| **DEF-01** | Слишком мягкий порог отсечения реранкера (`min_score: -7.0`) и отсутствие изоляции от WMS | **CONFIRMED** | **P1** | Высокая | `config.yaml`, `retriever.py`, `doc_service.py` |
| **DEF-02** | Потеря `source_commit` в Docker (`unknown`) из-за отсутствия git и `.git` | **CONFIRMED** | **P1** | Высокая | `Dockerfile`, `docker-compose.yml`, `doc_indexer.py` |
| **DEF-03** | Разрыв тестовой инфраструктуры и падение 6 тестов из-за `import mcp` | **CONFIRMED** | **P1** | Высокая | `tests/*`, `Dockerfile`, `docker-compose.yml` |
| **DEF-04** | Неэффективный полный перебор всей коллекции при каждом вызове `get_doc` | **CONFIRMED** | **P2** | Высокая | `doc_service.py` (`_all_chunks`) |
| **DEF-05** | Несоответствие XML-тега `<untrusted_wms_codebase_context>` документации Ospysin | **CONFIRMED** | **P2** | Высокая | `retriever.py` (`format_for_agent`) |
| **DEF-06** | Асимметрия обработки флага `--auto-index` для Ospysin | **CONFIRMED** | **P2** | Высокая | `mcp_server.py` (L459) |
| **DEF-07** | Фиктивный ассерт `or out.startswith("NO_MATCH")` в `test_ospysin_docs.py` | **CONFIRMED** | **P2** | Высокая | `tests/test_ospysin_docs.py` (L305) |
| **DEF-08** | Отсутствие изолированного Docker-профиля для FastMCP integration тестов | **CONFIRMED** | **P2** | Средняя | `docker-compose.yml`, `Dockerfile` |
| **DEF-09** | Прерывание индексации всех документов при ошибке I/O на одном файле | **CONFIRMED** | **P3** | Средняя | `doc_indexer.py` (L140-145) |

---

## 7. Detailed Defect Analysis

### DEF-01: Слишком мягкий порог отсечения реранкера (`min_score: -7.0`) и отсутствие изолированного порога для документации

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P1`** (Critical Retrieval Quality Risk)
* **Уровень уверенности:** Высокая (подтверждено набором из 23 adversarial-запросов и измерением сырых логитов модели).
* **Точное местоположение:**
  - [`wms-code-rag/config.yaml:30`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml#L30): `min_score: -7.0` в секции `reranking`.
  - [`wms-code-rag/src/retriever.py:70-75`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L70-L75): использование единого `self.config.reranking.min_score`.
* **Доказательства:**
  1. Запрос `mobile push notifications for findings` возвращает чанк `10-UI-UX-SPEC.md` с оценкой `-3.939` (проходит порог `-7.0`).
  2. Запрос `how to configure the Redis cluster broker for Celery workers in Ospysin` возвращает `12-DEPLOYMENT.md` со скором `-3.564`.
  3. Запрос `migrating Ospysin microservices to Apache Kafka event streaming` возвращает `14-ROADMAP.md` со скором `-3.859`.
  4. При отключении реранкера (`--no-rerank`) порог `similarity_threshold: 0.10` пропускает абсолютно все 3 негативных запроса (`0/3` true negatives).
* **Текущее поведение:** Порог `-7.0` пропускает в выдачу чанки, где встречаются общие слова запроса, даже если контекст кардинально противоречит запросу или описывает иные подсистемы.
* **Ожидаемое поведение:** На запросы по несуществующим функциям и технологиям поиск должен возвращать `NO_MATCH`.
* **Почему это проблема:** Агент на базе LLM получает нерелевантные фрагменты текста, помеченные как найденные доказательства, и начинает галлюцинировать о наличии в архитектуре Redis, Celery или push-уведомлений.
* **Предлагаемое решение:**
  1. Разделить конфигурацию порогов для исходного кода и архитектурной документации. Ввести параметр `config.ospysin.min_score`.
  2. На основе калибровки установить базовый порог отсечения документации в диапазоне `[-2.5, -2.0]`. При таком пороге все ложные предпосылки отсекаются (их скоры `-3.5`...`-4.0`), а легитимные запросы сохраняются (их скоры `>= +1.4` для явных и `>= -1.0` для глубоких концептов).
* **Затронутые файлы:** [`config.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/config.py), [`config.yaml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/config.yaml), [`retriever.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py), [`doc_service.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py).
* **Риски исправления:** Слишком высокий порог (например, `> 0.0`) может отсечь абстрактные смысловые запросы без ключевых слов. Требуется повторный прогон `run_eval.py` и калибровочной матрицы.
* **Критерии приёмки:** Hit@4 на positive-наборе >= 0.95; True Negatives = 3/3; запросы по Redis/Kafka возвращают `NO_MATCH`.

---

### DEF-02: Потеря метаданных ревизии Git в Docker (`source_commit="unknown"`)

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P1`** (Data Provenance & Traceability)
* **Уровень уверенности:** Высокая (подтверждено `docker exec`).
* **Точное местоположение:**
  - [`wms-code-rag/Dockerfile:11-13`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile#L11-L13): отсутствует установка пакета `git`.
  - [`wms-code-rag/docker-compose.yml:25`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml#L25): смонтирован путь `../Ospysin/docs:/workspace/ospysin-docs:ro`. Каталог `.git` находится в `../Ospysin/.git` и внутрь контейнера не попадает.
  - [`wms-code-rag/src/doc_indexer.py:26-50`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L26-L50): метод `resolve_git_revision` выполняет `subprocess.run(["git", "-C", str(docs_dir), "rev-parse", "HEAD"])`.
* **Доказательства:**
  1. `docker exec wms-code-rag-server which git` завершается с кодом 1 (`git not found`).
  2. `get_rag_status` возвращает `Source Revision: unknown`.
  3. Все чанки в ChromaDB имеют метаданные `source_commit: "unknown"`.
* **Текущее поведение:** Индексатор внутри контейнера всегда устанавливает ревизию `"unknown"`.
* **Ожидаемое поведение:** Индексатор фиксирует точный Git SHA (например, `052701ec936f255ff03b8713a9294432942f4e57`).
* **Почему это проблема:** Невозможно верифицировать, какая именно версия спецификаций проиндексирована в контейнере, изменялись ли файлы локально (`-dirty`) и соответствуют ли ответы текущему коммиту репозитория.
* **Сравнение вариантов решения:**
  - *Вариант A (Монтирование git + пакет в Dockerfile):* Добавить `git` в `apt-get install`, смонтировать `../Ospysin:/workspace/ospysin:ro` и запускать `git` внутри. *Минусы:* Увеличение размера образа, лишний бинарник в production-контейнере, необходимость настройки `safe.directory` для не-root пользователя `appuser`.
  - *Вариант B (Передача через env/файл ревизии — Рекомендуемый):* Индексатор читает переменную окружения `OSPYSIN_SOURCE_COMMIT`, либо читает файл `.git-revision`, генерируемый хостом перед запуском / при CI-сборке. Если переменная/файл есть — берется она. Если нет — fallback на команду `git`. *Плюсы:* Соответствие Minimal Change Principle, отсутствие необходимости ставить Git в контейнер, безопасность и предсказуемость.
* **Затронутые файлы:** [`doc_indexer.py`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py), [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml).
* **Критерии приёмки:** `get_rag_status` в контейнере выдает 40-значный хэш коммита или SHA с суффиксом `-dirty`.

---

### DEF-03: Разрыв тестовой инфраструктуры и падение 6 тестов из-за отсутствия FastMCP на хосте

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P1`** (CI/CD & Regression Barrier)
* **Уровень уверенности:** Высокая (подтверждено запуском `pytest`).
* **Точное местоположение:**
  - [`tests/test_declaration_and_symbol_lookup.py:631`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_declaration_and_symbol_lookup.py#L631): прямой импорт `from src.mcp_server import indexer, retriever`.
  - `tests/test_finding_01_auth.py:6`
  - `tests/test_finding_06_dos_and_concurrency.py:7`
  - `tests/test_finding_09_entity_schema.py:4`
  - `tests/test_finding_11_fastmcp_coupling.py:2`
  - `tests/test_security_invariants_suite.py:20`
* **Доказательства:**
  1. На хосте `python -m pytest tests/` выдает 5 ошибок collection и 1 failure из-за `ModuleNotFoundError: No module named 'mcp'`.
  2. В контейнере `pytest` не установлен, `/app/tests` отсутствует.
* **Текущее поведение:** Полный регрессионный сьют проекта невозможно запустить стандартными средствами без ручной установки FastMCP в хостовый интерпретатор.
* **Ожидаемое поведение:** Тесты должны без сбоев выполняться как в контейнерной среде, так и на хосте (с пропуском специфичных для MCP тестов, если FastMCP не установлен).
* **Почему это проблема:** Любой CI-пайплайн или сторонний разработчик не могут верифицировать систему одной командой `pytest`.
* **Предлагаемое решение:**
  1. В `tests/test_declaration_and_symbol_lookup.py:630` и 5 проблемных файлах использовать `pytest.importorskip("mcp")` на уровне модуля или фикстуры.
  2. Создать тестовый профиль Docker Compose (`docker-compose.test.yml`) или команду запуска тестов в контейнере с установкой dev-зависимостей (`pytest`, `httpx`).
* **Критерии приёмки:** Команда `python -m pytest tests/` на хосте завершается с кодом 0 (PASSED/SKIPPED без FAILED и ERRORS).

---

### DEF-04: Неэффективный полный перебор всей коллекции при каждом вызове `get_doc`

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P2`** (Performance & Scalability)
* **Уровень уверенности:** Высокая (анализ кода [`doc_service.py:83-88`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L83-L88)).
* **Точное местоположение:**
  - [`wms-code-rag/src/doc_service.py:83-88, 95`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_service.py#L83-L88):
    ```python
    def _all_chunks(self) -> List[CodeChunk]:
        data = self.store.collection.get(include=["documents", "metadatas"])
        # ...
        return [_chunk_from_row(i, d, m) for i, d, m in zip(ids, docs, metas)]
    ```
* **Доказательства:** В методе `get_doc()` на строке 95 безусловно вызывается `chunks = self._all_chunks()`. Все 246 документов и метаданных поднимаются из SQLite ChromaDB при каждом детерминированном запросе.
* **Текущее поведение:** Полная выборка всех записей таблицы векторов и их десериализация в Python-объекты.
* **Ожидаемое поведение:** Если пользователь запрашивает конкретный номер документа (`"05"`) или ADR (`"adr-3"`), запрос в ChromaDB должен содержать фильтр:
  `collection.get(where={"doc_id": value}, include=["documents", "metadatas"])`.
  Список доступных документов для ветки `NOT_FOUND` должен кешироваться при первом обращении или браться из легкого мета-запроса `get(include=[])`.
* **Почему это проблема:** При текущем объёме (246 чанков) задержка составляет единицы миллисекунд. Однако при росте документации до нескольких тысяч чанков это приведет к повышенному потреблению CPU и памяти на каждый вызов инструмента.
* **Предлагаемое решение:**
  1. Для `kind in ("adr", "num")` выполнять `collection.get(where={"doc_id": value})`.
  2. Кешировать список доступных файлов `available_documents` в экземпляре `OspysinDocsService` после индексации.
* **Критерии приёмки:** `get_doc` обращается только к чанкам целевого документа; тесты `test_get_doc_*` проходят без изменений внешнего контракта.

---

### DEF-05: Несоответствие XML-тега `<untrusted_wms_codebase_context>` документации Ospysin

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P2`** (Domain Clarity & Prompt Boundaries)
* **Уровень уверенности:** Высокая.
* **Точное местоположение:** [`wms-code-rag/src/retriever.py:97, 137`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/retriever.py#L97): хардкод тегов `<untrusted_wms_codebase_context>` в методе `format_for_agent`.
* **Доказательства:** Вывод `search_ospysin_docs` и `get_ospysin_doc` оборачивает сниппеты документации в теги контекста кодовой базы WMS:
  ```xml
  <!-- BEGIN UNTRUSTED REPOSITORY CONTEXT -->
  <untrusted_wms_codebase_context>
  ...
  </untrusted_wms_codebase_context>
  ```
* **Текущее поведение:** Контекст документации называется контекстом кодовой базы WMS.
* **Ожидаемое поведение:** Тег должен отражать природу данных: `<untrusted_ospysin_docs_context>`.
* **Почему это проблема:** Модель-ассистент downstream может интерпретировать спецификации внешней системы как внутренний исходный код склада WMS, допуская семантическую путаницу в рассуждениях.
* **Предлагаемое решение:** Добавить необязательный параметр `context_tag: str = "untrusted_wms_codebase_context"` в метод `format_for_agent` и передавать `"untrusted_ospysin_docs_context"` из `doc_service.py`.
* **Критерии приёмки:** Ответы Ospysin содержат `<untrusted_ospysin_docs_context>`, ответы WMS сохраняют `<untrusted_wms_codebase_context>`.

---

### DEF-06: Асимметрия обработки флага `--auto-index` для Ospysin

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P2`** (Lifecycle Contract Consistency)
* **Уровень уверенности:** Высокая.
* **Точное местоположение:** [`wms-code-rag/src/mcp_server.py:450-469`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/mcp_server.py#L450-L469):
  - L450: `if args.auto_index and indexer.store.count() == 0:` (для WMS)
  - L459: `if ospysin_service is not None and ospysin_service.store.count() == 0:` (для Ospysin — `args.auto_index` отсутствует).
* **Доказательства:** Прямой анализ кода функции `main()`.
* **Текущее поведение:** Если сервер запускается без флага `--auto-index`, индексация WMS блокируется, но индексация Ospysin все равно неявно запускается в фоновом потоке, если коллекция пуста.
* **Ожидаемое поведение:** Фоновая автоиндексация должна подчиняться флагу командной строки `--auto-index` единообразно для обоих сервисов.
* **Предлагаемое решение:** Привести проверку к виду: `if args.auto_index and ospysin_service is not None and ospysin_service.store.count() == 0:`.
* **Критерии приёмки:** При запуске с `--no-auto-index` на пустой базе фоновый поток `initial-ospysin-indexer` не создается.

---

### DEF-07: Маскирование проверки качества поиска в `test_ospysin_docs.py`

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P2`** (Test Assertions Integrity)
* **Уровень уверенности:** Высокая.
* **Точное местоположение:** [`wms-code-rag/tests/test_ospysin_docs.py:305`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py#L305):
  ```python
  assert "BEGIN UNTRUSTED REPOSITORY CONTEXT" in out or out.startswith("NO_MATCH")
  ```
* **Доказательства:** В юнит-тестах используется `FakeEmbedder`, генерирующий псевдослучайные векторы на основе sha256-хэша. При таком подходе векторная близость случайна, и автор теста замаскировал возможное отсутствие результатов дизъюнкцией `or out.startswith("NO_MATCH")`.
* **Текущее поведение:** Тест успешен как при нахождении документа, так и при полном провале поиска.
* **Ожидаемое поведение:** Тест должен явно разделять проверку формата ответа (wrapping) и семантическую проверку поиска (retrieval quality), либо использовать предсказуемые синтетические векторы, где косинусное сходство детерминированно превышает порог.
* **Предлагаемое решение:** Сконструировать в `FakeEmbedder` управляемую матрицу подобия для тестовых ключевых слов.

---

### DEF-08: Отсутствие изолированного Docker-профиля для FastMCP integration тестов

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P2`** (Engineering Infrastructure)
* **Уровень уверенности:** Средняя.
* **Точное местоположение:** [`docker-compose.yml`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/docker-compose.yml), [`Dockerfile`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/Dockerfile).
* **Текущее поведение:** Dockerfile собирает минимальный production-образ без средств тестирования. Запустить сквозные тесты MCP в изолированном контейнере невозможно.
* **Предлагаемое решение:** Добавить target `testing` в многоэтапный Dockerfile или отдельный `docker-compose.test.yml`, монтирующий каталог `tests` и запускающий `pytest`.

---

### DEF-09: Прерывание индексации всех документов при ошибке I/O на одном файле

* **Статус:** **`CONFIRMED`**
* **Приоритет:** **`P3`** (Fault Tolerance)
* **Уровень уверенности:** Средняя.
* **Точное местоположение:** [`wms-code-rag/src/doc_indexer.py:140-145`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/src/doc_indexer.py#L140-L145).
* **Доказательства:**
  ```python
  for file_path in files:
      rel_path = file_path.relative_to(docs_dir).as_posix()
      for chunk in self.chunker.chunk_file(file_path, rel_path):
  ```
  Если на одном из файлов возникнет `PermissionError` или повреждение дескриптора ОС, всё исключение всплывёт наружу, прервав индексацию остальных 20 файлов.
* **Предлагаемое решение:** Обернуть `chunk_file` в `try-except OSError` с логированием `logger.warning("Skipping unreadable doc: %s", exc)` и продолжением цикла.

---

## 8. Retrieval Evaluation: Results, Interpretation & Gaps

### 8.1. Сопоставление с эталоном (28 positive, 3 negative)
Сравнение выполнено в строгой изоляции на одинаковом наборе документов:

| Конфигурация | Чанков | Hit@1 | Hit@4 | MRR | True Negatives (0 совпадений) | Ложные срабатывания |
|---|:---:|:---:|:---:|:---:|:---:|:---|
| **Baseline (legacy chunker, rerank=ON)** | 250 | 0.893 | 0.929 | 0.902 | 2/3 (66.7%) | N03 (`mobile push notifications`), промахи Q07, Q09 |
| **New Chunker (rerank=ON, min_score=-7.0)** | 246 | **1.000** | **1.000** | **1.000** | 2/3 (66.7%) | N03 (`mobile push notifications`) |
| **New Chunker (rerank=OFF, sim_thresh=0.10)**| 246 | 0.821 | 0.964 | 0.887 | **0/3 (0.0%)** | N01, N02, N03 — все пробивают порог 0.10 |

### 8.2. Независимая adversarial-матрица (23 запроса)
Результаты исполнения [`run_adversarial_eval.py`](file:///C:/Users/наш%20компухтер/.gemini/antigravity/brain/e1d66c19-e3e2-4a41-8193-018dae7b708f/scratch/run_adversarial_eval.py) подтвердили следующие распределения скоров кросс-энкодера:

```
[Диапазон скоров реранкера cross-encoder/ms-marco-MiniLM-L-6-v2]
-10.0   -8.0    -7.0 (порог)  -4.0     -2.5 (оптимум)  0.0     +2.0    +4.0    +7.0
  |-------|-------|-------------|---------|-------------|-------|-------|-------|
          ^                     ^         ^                     ^
    Нерелевантные       Ложные предпосылки  Рекомендуемый    Концепты    Точные совпадения
    (склад WMS,         (Redis, Kafka,     порог            и абстракции (инструменты,
     кулинария)         push-уведомления)                                API, таблицы)
```

1. **Точные совпадения (инструменты, эндпоинты, таблицы):** Скоры от `+6.2` до `+7.7`.
2. **Семантические концепты:** Скоры от `+1.4` до `+2.5`.
3. **Глубокие абстракции без пересечения слов:** Скоры от `-1.0` до `+0.5` (например, `adr-0003` получил `-0.912`).
4. **Ложные предпосылки и негативы со смежными терминами:** Скоры от `-3.5` до `-4.0`.
5. **Абсолютно чужие предметные области:** Скоры ниже `-7.0` (отсечены).

### 8.3. Интерпретация случая "Out-of-Scope Features"
Запрос `automatic exploit generation and payload execution` вернул фрагмент `01-SCOPE.md#Out-of-Scope Features & Deferral Rationale` со скором `+2.503`.  
**Инженерный вывод:** Это **НЕ false positive**, а корректный ответ retrieval-системы. Документация действительно содержит раздел, прямо заявляющий, что генерация эксплойтов исключена из проекта Ospysin. Попадание данного документа в контекст полезно для агента, так как позволяет дать обоснованный ответ о границах системы.

---

## 9. Data Provenance and Index Lifecycle

### 9.1. Инварианты жизненного цикла
1. **Детерминированность chunk ID:** Хэш рассчитывается по формуле:  
   `sha256(f"{rel_path}:spec:{heading_path}:{occ}:{idx}")[:32]`.  
   Номера строк (`start_line`, `end_line`) исключены из хэша. Правка пустых строк или добавление абзаца в начало документа не приводит к появлению дубликатов (ghost chunks).
2. **Атомарность переиндексации:** Метод `scan_and_index()` сначала валидирует входные параметры, собирает чанки и рассчитывает эмбеддинги. Только после этого вызывается `store.clear()` или `store.add_chunks()`. Любая ошибка до момента генерации эмбеддингов оставляет старый индекс нетронутым.
3. **Удаление устаревших данных:** Метод `prune_stale_chunks()` находит разницу между множеством активных ID и всеми ID в коллекции, гарантированно удаляя чанки переименованных или удаленных файлов.

---

## 10. Test Infrastructure and Coverage

### 10.1. Текущее покрытие
- Модульные тесты чанкера: Иерархия H1–H6, H4–H6, fences, atomic tables, duplicate headings, private key redaction — **покрыты полностью** ([`test_ospysin_docs.py:78-166`](file:///c:/Users/наш%20компухтер/Desktop/Rag'n%20project/wms-code-rag/tests/test_ospysin_docs.py#L78-L166)).
- Тесты изоляции коллекций: Разделение WMS и Ospysin — **покрыто** (L170-190).
- Сервисные тесты: `get_doc`, нормализация ключей, защита от path traversal, инъекции разметки — **покрыты** (L228-291).
- Пробелы в тестировании:
  - Отсутствуют тесты реального семантического поиска с валидацией скоров.
  - Отсутствуют тесты CLI флага `--auto-index`.
  - Отсутствуют тесты механизма `resolve_git_revision` при недоступности Git.

---

## 11. Security, Isolation and Untrusted Context

1. **Разграничение доверия (Untrusted Boundary):** Все фрагменты, возвращаемые инструментами, позиционируются как пассивные данные и оборачиваются в комментарии с предупреждением о недопустимости исполнения директив из текста.
2. **Экранирование тегов:** Защита от попыток закрытия контекста доказана тестом `test_get_doc_neutralises_context_boundary_injection`.
3. **Read-Only безопасность:** Документы Ospysin физически недоступны для перезаписи из контейнера.

---

## 12. Remediation Roadmap by Phase

План исправлений разбит на 4 последовательные фазы по принципу минимизации рисков и приоритетности.

```
+---------------------------------------------------------------------------------------+
| ФАЗА 1: Стабилизация тестового контура и версионирования (P1)                         |
| [DEF-02, DEF-03]                                                                      |
| - pytest.importorskip("mcp") в зависимых тестах                                       |
| - Механизм OSPYSIN_SOURCE_COMMIT / git-fallback                                       |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| ФАЗА 2: Калибровка качества поиска и изоляция порогов (P1)                            |
| [DEF-01, DEF-07]                                                                      |
| - Конфигурационный параметр ospysin.min_score (-2.5)                                  |
| - Индивидуальный порог в CodeRetriever для документации                               |
| - Устранение маскирующих ассертов в тестах                                            |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| ФАЗА 3: Оптимизация производительности и унификация контрактов (P2)                  |
| [DEF-04, DEF-05, DEF-06]                                                              |
| - Точечный запрос where={"doc_id": ...} в get_doc                                     |
| - Параметризация XML-тега <untrusted_ospysin_docs_context>                            |
| - Синхронизация флага --auto-index                                                    |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| ФАЗА 4: Устойчивость к ошибкам и Dev/CI инфраструктура (P2, P3)                       |
| [DEF-08, DEF-09]                                                                      |
| - Изолированная обработка OSError при чтении файлов в индексаторе                     |
| - docker-compose.test.yml для автоматического запуска полного сьюта в CI              |
+---------------------------------------------------------------------------------------+
```

---

### ФАЗА 1: Стабилизация тестового контура и версионирования (P1)
* **Цель:** Обеспечить 100% прохождение существующих тестов на любой машине и восстановить аудит ревизии Git в Docker.
* **Включённые дефекты:** **DEF-02, DEF-03**.
* **Предпосылки:** Отсутствуют.
* **Точные области кода:**
  - `wms-code-rag/tests/test_declaration_and_symbol_lookup.py:631`
  - `wms-code-rag/tests/test_finding_01_auth.py:6`
  - `wms-code-rag/tests/test_finding_06_dos_and_concurrency.py:7`
  - `wms-code-rag/tests/test_finding_09_entity_schema.py:4`
  - `wms-code-rag/tests/test_finding_11_fastmcp_coupling.py:2`
  - `wms-code-rag/tests/test_security_invariants_suite.py:20`
  - `wms-code-rag/src/doc_indexer.py:26-50`
  - `wms-code-rag/docker-compose.yml:14`
* **Последовательность действий:**
  1. В 6 тестовых файлах добавить `pytest.importorskip("mcp")` перед импортом `src.mcp_server`.
  2. В `doc_indexer.py:resolve_git_revision` добавить приоритетную проверку переменной окружения `os.environ.get("OSPYSIN_SOURCE_COMMIT")` и файла `.git-revision`.
  3. В `docker-compose.yml` пробросить переменную `OSPYSIN_SOURCE_COMMIT=${OSPYSIN_SOURCE_COMMIT:-unknown}`.
* **Тесты до и после:** `python -m pytest tests/ -v`.
* **Критерии завершения:** Все 55 тестов выполняются успешно (PASSED или SKIPPED), 0 ERRORS; в Docker статус сообщает реальный SHA.

---

### ФАЗА 2: Калибровка качества поиска и изоляция порогов (P1)
* **Цель:** Устранить ложноположительные срабатывания на запросах с ложными предпосылками и отделить порог документации от WMS.
* **Включённые дефекты:** **DEF-01, DEF-07**.
* **Предпосылки:** Завершена Фаза 1.
* **Точные области кода:**
  - `wms-code-rag/config.yaml` (секция `ospysin`)
  - `wms-code-rag/src/config.py:OspysinDocsConfig`
  - `wms-code-rag/src/retriever.py:CodeRetriever`
  - `wms-code-rag/src/doc_service.py:OspysinDocsService`
  - `wms-code-rag/tests/test_ospysin_docs.py`
* **Последовательность действий:**
  1. Добавить `min_score: float = -2.5` в `OspysinDocsConfig` и `config.yaml`.
  2. В `CodeRetriever.retrieve()` поддержать параметр `min_score_override: Optional[float] = None`.
  3. В `OspysinDocsService.search()` передавать `self.config.ospysin.min_score`.
  4. Переработать `test_search_returns_wrapped_results_with_provenance`, убрав `or out.startswith("NO_MATCH")`.
* **Тесты до и после:** Прогон `eval/run_eval.py` и `run_adversarial_eval.py`.
* **Критерии завершения:** Hit@4 >= 0.96; True Negatives = 3/3; запросы про Redis/Kafka возвращают `NO_MATCH`.

---

### ФАЗА 3: Оптимизация производительности и унификация контрактов (P2)
* **Цель:** Исключить избыточные сканирования базы данных, скорректировать XML-разметку и синхронизировать контракт CLI.
* **Включённые дефекты:** **DEF-04, DEF-05, DEF-06**.
* **Предпосылки:** Завершена Фаза 2.
* **Точные области кода:**
  - `wms-code-rag/src/doc_service.py:83-115`
  - `wms-code-rag/src/retriever.py:format_for_agent`
  - `wms-code-rag/src/mcp_server.py:459`
* **Последовательность действий:**
  1. В `doc_service.py:get_doc` переписать поиск по `doc_id` на точечный `collection.get(where={"doc_id": value})`.
  2. Кешировать список доступных документов для сообщения `NOT_FOUND`.
  3. Добавить параметр `root_tag` в `format_for_agent` и передавать `"untrusted_ospysin_docs_context"` для Ospysin.
  4. В `mcp_server.py:459` добавить проверку `args.auto_index`.
* **Критерии завершения:** Тесты `test_ospysin_docs.py` подтверждают правильность ответов `get_doc`; вывод содержит правильный XML-тег; `--no-auto-index` подавляет индексацию.

---

### ФАЗА 4: Устойчивость к ошибкам и Dev/CI инфраструктура (P2, P3)
* **Цель:** Повысить отказоустойчивость индексатора и предоставить готовый контейнерный контур для CI.
* **Включённые дефекты:** **DEF-08, DEF-09**.
* **Предпосылки:** Завершена Фаза 3.
* **Точные области кода:**
  - `wms-code-rag/src/doc_indexer.py:140`
  - `wms-code-rag/docker-compose.test.yml` (новый файл)
* **Последовательность действий:**
  1. В `doc_indexer.py` изолировать чтение отдельного файла в `try-except OSError`.
  2. Создать `docker-compose.test.yml` для автоматизированного прогона тестов в окружении с установленным FastMCP.
* **Критерии завершения:** Ошибка чтения тестового файла с некорректными правами не срывает индексацию остальных спецификаций; тесты успешно стартуют через Docker Compose.

---

## 13. Dependency Matrix

| Задача / Дефект | Зависит от | Блокирует |
|:---|:---|:---|
| **DEF-03 (Тесты FastMCP)** | Нет | DEF-07, DEF-08, надежность CI |
| **DEF-02 (`source_commit`)** | Нет | Трассируемость релизов |
| **DEF-01 (Порог реранкера)** | DEF-03 | Финальную приёмку качества retrieval |
| **DEF-07 (Ассерты в тестах)** | DEF-01, DEF-03 | Валидацию Фазы 2 |
| **DEF-04 (Оптимизация `get_doc`)** | DEF-07 | Масштабирование базы знаний |
| **DEF-05 (XML-тег)** | Нет | Устранение двусмысленности промпта LLM |
| **DEF-06 (Флаг `--auto-index`)** | Нет | Предсказуемость старта сервера |
| **DEF-09 (I/O в индексаторе)** | Нет | Отказоустойчивость при поврежденных файлах |
| **DEF-08 (Dev-контейнер)** | DEF-03 | Автоматизацию регрессионного тестирования |

---

## 14. Risk and Priority Matrix

```
   ВЛИЯНИЕ
      ^
      |  [DEF-01: Ложные срабатывания RAG]    [DEF-03: Падение тестового сьюта]
 High |  [DEF-02: Потеря source_commit]
      |
      |  [DEF-04: Полный скан в get_doc]       [DEF-07: Маскировка ассертов]
 Med  |  [DEF-05: Ошибка XML-тега]             [DEF-06: Асимметрия auto-index]
      |  [DEF-08: Отсутствие dev-контейнера]
      |
 Low  |  [DEF-09: Исключения I/O в indexer]
      |
      +------------------------------------------------------------------------>
         Низкая                     Средняя                    Высокая
                                СЛОЖНОСТЬ ИСПРАВЛЕНИЯ
```

---

## 15. Acceptance Criteria

Интеграция считается полностью исправленной и готовой к промышленной эксплуатации только при выполнении следующих критериев:

1. **Retrieval Precision & Discrimination:**
   - Hit@4 на позитивном наборе `ospysin_queries.yaml` >= 0.95.
   - 100% истинно отрицательных запросов (N01, N02, N03) возвращают `NO_MATCH`.
   - Запросы с ложными предпосылками о наличии Redis, Celery, Kafka возвращают `NO_MATCH`.
2. **Provenance Traceability:**
   - Команда `get_rag_status` в контейнере Docker возвращает реальный хэш коммита спецификаций документации.
3. **Test Suite Green Bar:**
   - Команда `python -m pytest tests/` на хосте выполняется с кодом возврата 0 (без FAILED и ERRORS).
   - Все 22 модульных теста `test_ospysin_docs.py` выполняются со строгими ассертами (без fallback на `NO_MATCH`).
4. **Contract Uniformity:**
   - Ответы инструментов документации используют контейнер `<untrusted_ospysin_docs_context>`.
   - Флаг `--no-auto-index` отключает фоновую индексацию обоих сервисов.
5. **Zero WMS Regressions:**
   - Все функциональные возможности поиска по коду WMS сохраняют 100% работоспособность.

---

## 16. Regression Strategy for WMS Code RAG

Для исключения регрессий в существующей системе WMS Code RAG при реализации исправлений должны соблюдаться правила:

1. **Изоляция конфигурации:** Все изменения порогов реранкера должны локализоваться строго в объекте `config.ospysin` и не затрагивать `config.retrieval` или `config.reranking`.
2. **Обратная совместимость `format_for_agent`:** Параметр `exact` и новый параметр `root_tag` должны иметь значения по умолчанию, идентичные прежнему поведению WMS (`exact=False`, `root_tag="untrusted_wms_codebase_context"`).
3. **Неприкосновенность хранилища WMS:** Перед выполнением любых тестов гарантировать активность защиты `_assert_safe_mutation()`. Никакие операции над коллекцией `ospysin_docs_knowledge` не должны обращаться к `wms_codebase_knowledge`.
4. **Контрольный прогон сьюта WMS:** После каждой фазы изменений запускать полный набор тестов поиска по коду WMS:
   - `test_declaration_and_symbol_lookup.py`
   - `test_finding_02_secrets.py`
   - `test_finding_03_ghost_chunks.py`
   - `test_finding_04_prompt_injection.py`
   - `test_finding_05_threshold_fallback.py`
   - `test_finding_07_ro_mounts.py`
   - `test_finding_08_parser.py`
   - `test_finding_10_stdio_cleanliness.py`

---

## 17. Unresolved Questions and Decisions

Перед началом реализации Фазы 1 и Фазы 2 необходимо утвердить следующие инженерные решения:

1. **Решение по передаче Git SHA (DEF-02):**
   - *Вариант 1:* Автоматическая генерация файла `wms-code-rag/data/.git-revision` скриптом запуска перед стартом Docker.
   - *Вариант 2:* Передача `OSPYSIN_SOURCE_COMMIT` через `.env` / `docker-compose.yml`.
   - *Рекомендация:* Принять Вариант 2 как наиболее простой и декларативный.
2. **Выбор точного порога `ospysin.min_score` (DEF-01):**
   - Провести валидацию на расширенной выборке из 50 запросов в диапазоне от `-3.0` до `-2.0`.
   - *Рекомендация:* Установить `-2.5` в качестве базового значения по умолчанию.
3. **Поведение `get_doc` при поиске по неполному имени файла (DEF-04):**
   - Сохранить ли поиск по подстроке имени (например, `"catalog"` -> `05-TOOL-CATALOG.md`), или требовать точного указания stem?
   - *Рекомендация:* Сохранить подстрочный поиск через кешированный в памяти маппинг стемов, не нагружая ChromaDB.

---

## 18. Final Checklist

Перед началом последующих задач по исправлению проверить:
- [ ] Рабочее дерево Git не содержит случайных правок.
- [ ] Настоящий документ доступен по пути `docs/rag/OSPYSIN_DOCUMENTATION_RAG_REMEDIATION_ROADMAP.md`.
- [ ] Все участники команды согласны с предложенным порядком фаз (Фаза 1 -> Фаза 2 -> Фаза 3 -> Фаза 4).
- [ ] Выбран вариант передачи ревизии Git для Docker.
- [ ] Подготовлены изолированные окружения для прогона тестов.
