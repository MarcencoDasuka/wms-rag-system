from pathlib import Path
from unittest.mock import MagicMock
from src.chunker import CodeChunk
from src.mcp_server import get_entity_and_schema, retriever


def test_get_entity_and_schema_retrieves_both_sql_ddl_and_jpa_entity():
    """Verify that get_entity_and_schema retrieves both SQL DDL migrations and JPA Java entities."""
    sql_chunk = CodeChunk(
        id="sql-alloc-01",
        file_path="src/main/resources/db/migration/V1__allocations.sql",
        file_name="V1__allocations.sql",
        language="sql",
        chunk_type="sql_schema",
        symbol_name="allocations",
        content="-- Migration: V1__allocations.sql\nCREATE TABLE allocations (id BIGINT PRIMARY KEY, task_id BIGINT NOT NULL, quantity INT);",
        start_line=1,
        end_line=2,
        metadata={"table_or_index": "allocations", "chunk_type": "sql_schema", "language": "sql"}
    )

    jpa_chunk = CodeChunk(
        id="java-alloc-01",
        file_path="src/main/java/com/isd/wms/entity/Allocation.java",
        file_name="Allocation.java",
        language="java",
        chunk_type="class_summary",
        symbol_name="Allocation",
        content="// Package: com.isd.wms.entity\n@Entity\n@Table(name = \"allocations\")\npublic class Allocation {\n    @Id private Long id;\n    private Integer quantity;\n}",
        start_line=1,
        end_line=10,
        metadata={"class": "Allocation", "chunk_type": "class_summary", "language": "java"}
    )

    original_retrieve = retriever.retrieve
    try:
        def mock_retrieve(query: str, top_n: int = 4, where_filter: dict = None):
            results = []
            if where_filter and where_filter.get("chunk_type") == "sql_schema":
                results.append((sql_chunk, 0.95))
            elif where_filter and where_filter.get("language") == "java":
                results.append((jpa_chunk, 0.90))
            return results

        retriever.retrieve = MagicMock(side_effect=mock_retrieve)

        response = get_entity_and_schema("allocations")

        # Invariant 1: Response must contain SQL schema DDL
        assert "CREATE TABLE allocations" in response
        assert "V1__allocations.sql" in response

        # Invariant 2: Response must contain Java JPA entity definition
        assert "@Entity" in response
        assert "class Allocation" in response
        assert "Allocation.java" in response

        # Invariant 3: Both where_filters were invoked (not just sql_schema)
        calls = retriever.retrieve.call_args_list
        where_filters = [c.kwargs.get("where_filter") for c in calls if "where_filter" in c.kwargs]
        assert {"chunk_type": "sql_schema"} in where_filters
        assert {"language": "java"} in where_filters

    finally:
        retriever.retrieve = original_retrieve
