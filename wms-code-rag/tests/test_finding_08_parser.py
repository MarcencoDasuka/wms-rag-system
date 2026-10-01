from pathlib import Path
from src.chunker import CodeAwareChunker


def test_java_parser_handles_nested_parentheses_and_braces_in_strings(tmp_path: Path):
    """Verify Java chunker correctly extracts methods with nested annotations and braces in strings/comments."""
    java_code = '''package com.isd.wms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

public class AllocationController {

    private final String config = "init";

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and (hasPermission(#id, 'READ') or #userId == principal.id)")
    public ResponseEntity<String> getAllocations(
        @PathVariable("id") Long id,
        @RequestParam(name = "filter", defaultValue = "active") String filter
    ) throws Exception {
        String template = "format {0} and {1}";
        String closingBraceStr = "}";
        char c = '}';
        // A tricky comment with { and } braces
        /* Multi-line comment {
           closing } */
        if (id > 0) {
            System.out.println("Valid allocation requested: " + id);
        }
        return ResponseEntity.ok(closingBraceStr);
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createAllocation(@RequestBody String payload) {
        System.out.println("Created: " + payload);
        return ResponseEntity.ok().build();
    }
}
'''
    file_path = tmp_path / "AllocationController.java"
    file_path.write_text(java_code, encoding="utf-8")

    chunker = CodeAwareChunker()
    chunks = chunker.chunk_file(file_path, "AllocationController.java")

    method_chunks = [c for c in chunks if c.chunk_type == "method"]
    method_names = [c.symbol_name for c in method_chunks]

    # Verify both methods are detected
    assert "AllocationController.getAllocations" in method_names
    assert "AllocationController.createAllocation" in method_names

    alloc_chunk = next(c for c in method_chunks if c.symbol_name == "AllocationController.getAllocations")

    # Invariant 1: Complex annotation with nested parentheses must be preserved
    assert "@PreAuthorize(\"hasRole('ADMIN') and (hasPermission(#id, 'READ') or #userId == principal.id)\")" in alloc_chunk.content

    # Invariant 2: String with closing brace must NOT prematurely terminate the method
    assert 'String closingBraceStr = "}";' in alloc_chunk.content
    assert "return ResponseEntity.ok(closingBraceStr);" in alloc_chunk.content

    # Invariant 3: Subsequent method must start cleanly and not be eaten or corrupted
    create_chunk = next(c for c in method_chunks if c.symbol_name == "AllocationController.createAllocation")
    assert "public ResponseEntity<Void> createAllocation" in create_chunk.content
    assert "Created: " in create_chunk.content


def test_sql_parser_preserves_stored_procedures_and_semicolons_in_strings(tmp_path: Path):
    """Verify SQL chunker does not chop procedures with internal semicolons or strings with semicolons."""
    sql_code = '''-- Migration V5: Create tables and stock procedure
CREATE TABLE stock (
    id BIGINT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    qty INT NOT NULL
);

CREATE OR REPLACE PROCEDURE update_warehouse_stock(p_sku VARCHAR, p_amount INT)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE stock SET qty = qty - p_amount WHERE sku = p_sku;
    INSERT INTO stock_audit_log (msg) VALUES ('Stock adjusted; sku=' || p_sku);
    COMMIT;
END;
$$;

ALTER TABLE stock ADD COLUMN notes VARCHAR(100) DEFAULT 'init;value';
'''
    file_path = tmp_path / "V5__stock_proc.sql"
    file_path.write_text(sql_code, encoding="utf-8")

    chunker = CodeAwareChunker()
    chunks = chunker.chunk_file(file_path, "V5__stock_proc.sql")

    assert len(chunks) == 3, f"Expected 3 SQL statements, got {len(chunks)}"

    symbols = [c.symbol_name for c in chunks]
    assert "stock" in symbols
    assert "update_warehouse_stock" in symbols

    proc_chunk = next(c for c in chunks if c.symbol_name == "update_warehouse_stock")

    # The entire procedure with its internal semicolons must be intact
    assert "CREATE OR REPLACE PROCEDURE update_warehouse_stock" in proc_chunk.content
    assert "UPDATE stock SET qty = qty - p_amount WHERE sku = p_sku;" in proc_chunk.content
    assert "'Stock adjusted; sku=' || p_sku" in proc_chunk.content
    assert "COMMIT;" in proc_chunk.content
    assert "END;" in proc_chunk.content

    alter_chunk = next(c for c in chunks if "ALTER TABLE" in c.content)
    assert "DEFAULT 'init;value';" in alter_chunk.content


def test_vue_parser_handles_template_attributes_and_script(tmp_path: Path):
    """Verify Vue chunker extracts template with attributes without truncation."""
    vue_code = '''<template lang="html">
  <div class="warehouse-container">
    <header-bar title="WMS Dashboard" />
    <section class="task-list">
      <div v-for="task in tasks" :key="task.id" class="task-card">
        <h3>{{ task.name }}</h3>
        <p>{{ task.description }}</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
const tasks = ref([]);
onMounted(async () => {
  tasks.value = await fetchTasks();
});
</script>

<style scoped>
.warehouse-container { padding: 16px; }
</style>
'''
    file_path = tmp_path / "WarehouseDashboard.vue"
    file_path.write_text(vue_code, encoding="utf-8")

    chunker = CodeAwareChunker()
    chunks = chunker.chunk_file(file_path, "WarehouseDashboard.vue")

    assert len(chunks) == 2
    types = {c.chunk_type for c in chunks}
    assert "vue_template" in types
    assert "vue_script" in types

    template_chunk = next(c for c in chunks if c.chunk_type == "vue_template")
    assert '<template lang="html">' in template_chunk.content
    assert "<h3>{{ task.name }}</h3>" in template_chunk.content

    script_chunk = next(c for c in chunks if c.chunk_type == "vue_script")
    assert '<script setup lang="ts">' in script_chunk.content
    assert "tasks.value = await fetchTasks();" in script_chunk.content
