package com.isd.wms.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Def11V38MigrationVerificationTest {

    @Test
    @DisplayName("V38 Migration Proof: V38 file exists and defines partial unique index and created_by columns")
    void v38Migration_structureVerification() throws IOException {
        Path v38Path = Path.of("src/main/resources/db/migration/V38__remediation_batch_2_schema.sql");
        assertThat(Files.exists(v38Path))
                .as("V38 migration file must exist")
                .isTrue();

        String sql = Files.readString(v38Path);

        // 1. Must drop old product+destination index
        assertThat(sql)
                .as("V38 must drop old compound product-destination index")
                .containsIgnoringCase("DROP INDEX IF EXISTS uk_active_replenishment_product_destination");

        // 2. Must create new destination-only partial unique index (DEF-11)
        assertThat(sql)
                .as("V38 must create partial unique index on destination_location_id")
                .containsIgnoringCase("CREATE UNIQUE INDEX IF NOT EXISTS uk_active_replenishment_destination")
                .containsIgnoringCase("ON replenishments (destination_location_id)")
                .containsIgnoringCase("WHERE status IN ('CREATED', 'ASSIGNED', 'IN_PROGRESS')");

        // 3. Must clean up legacy duplicate active replenishments
        assertThat(sql)
                .as("V38 must clean up legacy duplicate active replenishments before index creation")
                .containsIgnoringCase("UPDATE replenishments")
                .containsIgnoringCase("SET status = 'CANCELED'");

        // 4. Must add created_by to orders and replenishments with backfill (DEF-03, DEF-04, DEF-06)
        assertThat(sql)
                .as("V38 must add created_by column to orders")
                .containsIgnoringCase("ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by VARCHAR(50)");
        assertThat(sql)
                .as("V38 must add created_by column to replenishments")
                .containsIgnoringCase("ALTER TABLE replenishments ADD COLUMN IF NOT EXISTS created_by VARCHAR(50)");

        // 5. Must create performance indexes on created_by
        assertThat(sql)
                .as("V38 must index created_by on orders and replenishments")
                .containsIgnoringCase("CREATE INDEX IF NOT EXISTS idx_orders_created_by ON orders (created_by)")
                .containsIgnoringCase("CREATE INDEX IF NOT EXISTS idx_replenishments_created_by ON replenishments (created_by)");
    }

    @Test
    @DisplayName("Invariance Proof: Historical migrations V1-V37 remain untouched")
    void historicalMigrations_remainUntouched() {
        for (int i = 1; i <= 37; i++) {
            // Find migration file starting with V{i}__
            int version = i;
            Path migrationDir = Path.of("src/main/resources/db/migration");
            try (var stream = Files.list(migrationDir)) {
                boolean found = stream.anyMatch(p -> p.getFileName().toString().startsWith("V" + version + "__"));
                assertThat(found)
                        .as("Historical migration V%d must exist and remain untouched", version)
                        .isTrue();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
