package com.isd.wms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ForeignKeyIndexesIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("D-4: All foreign keys across all warehouse tables in PostgreSQL have corresponding index coverage")
    void allForeignKeysInDatabase_haveCorrespondingIndex() {
        String sql = """
            SELECT
                c.conrelid::regclass::text AS child_table,
                a.attname AS fk_column,
                c.conname AS fk_constraint_name,
                c.confrelid::regclass::text AS parent_table
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.contype = 'f'
              AND c.connamespace = 'public'::regnamespace
              AND NOT EXISTS (
                  SELECT 1
                  FROM pg_index pi
                  WHERE pi.indrelid = c.conrelid
                    AND pi.indkey[0] = a.attnum
                    AND pi.indpred IS NULL
              )
            ORDER BY child_table, fk_column
            """;

        List<Map<String, Object>> unindexedFks = jdbcTemplate.queryForList(sql);

        assertThat(unindexedFks)
            .as("Expected all foreign key columns to have corresponding indexes, but found unindexed: %s", unindexedFks)
            .isEmpty();
    }

    @Test
    @DisplayName("D-4: All 19 explicit foreign key indexes created by V36 exist in PostgreSQL catalog")
    void allExplicitV36Indexes_existInPgCatalog() {
        List<String> expectedIndexes = List.of(
            "idx_products_category_id",
            "idx_stocks_location_id",
            "idx_orders_destination_location_id",
            "idx_order_lines_order_id",
            "idx_order_lines_product_id",
            "idx_order_lines_task_id",
            "idx_replenishments_destination_location_id",
            "idx_replenishments_product_id",
            "idx_replenishments_task_id",
            "idx_tasks_operator_id",
            "idx_tasks_supervisor_id",
            "idx_allocations_stock_id",
            "idx_allocations_task_id",
            "idx_transport_units_order_id",
            "idx_transport_units_replenishment_id",
            "idx_inventory_history_destination_location_id",
            "idx_inventory_history_source_location_id",
            "idx_inventory_history_product_id",
            "idx_inventory_history_user_id"
        );

        String sql = "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?";

        for (String indexName : expectedIndexes) {
            List<String> matches = jdbcTemplate.queryForList(sql, String.class, indexName);
            assertThat(matches)
                .as("Index %s should exist in pg_indexes", indexName)
                .containsExactly(indexName);
        }
    }

    @Test
    @DisplayName("D-4: PostgreSQL optimizer can utilize FK index on order_lines.order_id")
    void orderLinesIndex_isUtilizedByPlanner() {
        // Force index scan preference in local session
        jdbcTemplate.execute("SET enable_seqscan = OFF;");
        try {
            List<String> planLines = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM order_lines WHERE order_id = 999999",
                String.class
            );
            String fullPlan = String.join("\n", planLines);
            assertThat(fullPlan).containsAnyOf("Index Scan", "Bitmap Heap Scan", "idx_order_lines_order_id");
        } finally {
            jdbcTemplate.execute("RESET enable_seqscan;");
        }
    }
}
