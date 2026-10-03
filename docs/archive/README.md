# Archive Documentation / Архив документации

Этот каталог содержит исторические аналитические и верификационные артефакты, созданные в ходе разработки, аудита безопасности и первичной оценки качества системы WMS Codebase RAG.

Данные документы сохранены для обеспечения воспроизводимости, истории аудита (compliance) и ретроспективного анализа. Для работы с актуальным состоянием проекта используйте ссылки ниже.

---

## Связь с актуальной документацией

| Архивный документ | Назначение / Описание | Актуальный документ взамен |
| :--- | :--- | :--- |
| [`WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](WMS_RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) | Исторический объединенный реестр 29 решений и Roadmap 8 дефектов аудита. Разделен на два специализированных руководства. | [`docs/WMS_FIXES_AND_ARCHITECTURE_GUIDE.md`](../WMS_FIXES_AND_ARCHITECTURE_GUIDE.md),<br>[`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`WMS_CODE_RAG_TECHNICAL_AUDIT.md`](WMS_CODE_RAG_TECHNICAL_AUDIT.md) | Первичный архитектурный и security-аудит (дефекты 01–11). Все замечания устранены и покрыты тестами. | [`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) |
| [`RAG_RETRIEVAL_EVALUATION.md`](RAG_RETRIEVAL_EVALUATION.md) | Первый замер качества поиска на 36 запросах (индекс 1,163 чанка, до AST-парсера Java). | [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) |
| [`RAG_RETRIEVAL_EVALUATION_REVIEW.md`](RAG_RETRIEVAL_EVALUATION_REVIEW.md) | Методологический аудит («Adversarial Review») первого замера, обосновавший разделение Strict/Lenient метрик. | [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) |

---

## Актуальный контур документации
1. [`README.md`](../../README.md) — обзор монорепозитория и точка входа.
2. [`wms-code-rag/README.md`](../../wms-code-rag/README.md) — руководство по MCP-серверу RAG, инструменты и запуск.
3. [`inbound-storage-dispatch/README.md`](../../inbound-storage-dispatch/README.md) — спецификация целевой системы WMS (доменные процессы, REST API, миграции).
4. [`docs/WMS_FIXES_AND_ARCHITECTURE_GUIDE.md`](../WMS_FIXES_AND_ARCHITECTURE_GUIDE.md) — актуальный реестр архитектурных решений, верификация глобального аудита, метрики и новые дефекты ядра WMS.
5. [`docs/RAG_FIXES_AND_ARCHITECTURE_GUIDE.md`](../RAG_FIXES_AND_ARCHITECTURE_GUIDE.md) — актуальный реестр архитектурных решений, безопасность и AST-парсинг подсистемы Code RAG.
6. [`docs/RAG_POST_FIX_EVALUATION.md`](../RAG_POST_FIX_EVALUATION.md) — подтвержденный верификационный baseline качества поиска (1,261 чанк, Hit@1 83.3%).
