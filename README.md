# Warehouse Management System (WMS) & Codebase RAG Platform

Комплексный проект современной системы управления складом (WMS) со встроенной автономной подсистемой семантического анализа кодовой базы (Codebase RAG) через протокол Model Context Protocol (MCP).

---

## Архитектура репозитория

```text
.
├── inbound-storage-dispatch/           # Ядро WMS-системы (Clean Copy)
│   ├── wmsBack/                        # Spring Boot 3 бэкенд (Java 21, JPA, Flyway, PostgreSQL)
│   └── wmsFront/                       # Vue 3 фронтенд (Vite, PrimeVue, Pinia, TypeScript)
│
├── wms-code-rag/                       # Контейнеризированный сервис Codebase RAG + MCP
│   ├── src/                            # Движок RAG: AST-чанкинг, ONNX-эмбеддер, ChromaDB, реранкер
│   ├── Dockerfile                      # Легковесный образ (Python 3.11-slim, ONNX runtime)
│   ├── docker-compose.yml              # Запуск с volume-маунтом WMS в режиме :ro
│   ├── requirements.txt                # Зависимости сервиса
│   └── config.yaml                     # Настройки моделей, векторов и сервера
│
└── .gitignore                          # Правила исключения временных файлов и сборки
```

---

## 1. Подсистема WMS (`inbound-storage-dispatch`)

* **Backend:** Java 21, Spring Boot, Spring Data JPA, Flyway-миграции, PostgreSQL.
* **Frontend:** Vue 3 Composition API, Vite, PrimeVue.
* **Функционал:** Управление процессами приемки (Inbound), хранения и аллокации (Storage), комплектации и отгрузки (Dispatch).

### Запуск WMS локально
* **Бэкенд:**
  ```bash
  cd "inbound-storage-dispatch/wmsBack"
  ./mvnw clean spring-boot:run
  ```
* **Фронтенд:**
  ```bash
  cd "inbound-storage-dispatch/wmsFront"
  npm install
  npm run dev
  ```

---

## 2. Подсистема Codebase RAG & MCP (`wms-code-rag`)

Автономный сервис семантического поиска и индексации кодовой базы, разработанный в качестве внешнего когнитивного слоя (Capability Boundary) для AI-агента Antigravity.

* **Чанкинг:** С сохранением структуры классов/методов Java, блоков `<script>/<template>` Vue и DDL-миграций SQL.
* **Эмбеддер:** `sentence-transformers/all-MiniLM-L6-v2` через оптимизированный ONNX-рантайм (CPU, ~100 МБ RAM).
* **Векторное хранилище:** ChromaDB с косинусным расстоянием.
* **Реранкинг:** Cross-Encoder (`ms-marco-MiniLM-L-6-v2`).
* **Протокол:** FastMCP / Streamable HTTP (спецификация 2024-11-05).

### Запуск RAG-контейнера
```bash
cd wms-code-rag
docker compose up -d
```
Сервер поднимается на порту `8000` и доступен по адресу:
* Healthcheck: `http://localhost:8000/health`
* MCP Streamable HTTP: `http://localhost:8000/sse`

---

## 3. Подключение к AI-агенту (Antigravity)

Сервер регистрируется в конфигурации MCP (`~/.gemini/config/mcp_config.json`):
```json
{
  "mcpServers": {
    "wms-code-rag": {
      "serverUrl": "http://localhost:8000/sse"
    }
  }
}
```

### Доступные MCP-инструменты агента
* `search_wms_code(query, top_n)` — семантический поиск по Java и Vue коду.
* `find_symbol_declaration(symbol_name)` — детерминированная проверка объявления точного символа в проиндексированном коде (классы, интерфейсы, методы, рекорды, вложенные типы).
* `get_entity_and_schema(table_or_entity)` — получение структуры таблиц, DDL и JPA-сущностей.
* `search_wms_security(topic)` — специализированный поиск по безопасности (JWT, PreAuthorize, роли).
* `get_rag_status()` — метрики и статус векторного хранилища.
* `reindex_wms_codebase()` — полная переиндексация кодовой базы.
