package com.isd.wms.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class Def20V39MigrationVerificationTest {

    private static final Path MIGRATIONS_DIR = Paths.get("src", "main", "resources", "db", "migration");

    @Test
    @DisplayName("DEF-20 & Batch 3: V39 migration file defines versioning and case-insensitive unique indexes")
    void v39MigrationFile_containsRequiredDdl() throws IOException {
        Path v39Path = MIGRATIONS_DIR.resolve("V39__remediation_batch_3_schema.sql");
        assertThat(Files.exists(v39Path))
            .as("Migration V39__remediation_batch_3_schema.sql must exist")
            .isTrue();

        String sql = Files.readString(v39Path, StandardCharsets.UTF_8);

        assertThat(sql)
            .as("V39 must add version column to orders")
            .containsIgnoringCase("ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;");

        assertThat(sql)
            .as("V39 must add version column to replenishments")
            .containsIgnoringCase("ALTER TABLE replenishments ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;");

        assertThat(sql)
            .as("V39 must create unique index on lower(username)")
            .containsIgnoringCase("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_lower ON users (LOWER(username));");

        assertThat(sql)
            .as("V39 must create unique index on lower(email)")
            .containsIgnoringCase("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (LOWER(email));");
    }

    @Test
    @DisplayName("Historical Invariant: Migrations V1 through V38 must remain intact and immutable")
    void historicalMigrations_remainIntactAndImmutable() throws IOException {
        assertThat(Files.exists(MIGRATIONS_DIR))
            .as("Migration directory must exist")
            .isTrue();

        try (Stream<Path> stream = Files.list(MIGRATIONS_DIR)) {
            List<String> migrationFiles = stream
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".sql"))
                .sorted()
                .toList();

            for (int i = 1; i <= 38; i++) {
                String prefix = "V" + i + "__";
                boolean found = migrationFiles.stream().anyMatch(name -> name.startsWith(prefix));
                assertThat(found)
                    .as("Historical migration file with prefix " + prefix + " must be present and not removed")
                    .isTrue();
            }

            // Verify V1-V38 are not empty
            for (int i = 1; i <= 38; i++) {
                String prefix = "V" + i + "__";
                String fileName = migrationFiles.stream().filter(name -> name.startsWith(prefix)).findFirst().orElseThrow();
                Path filePath = MIGRATIONS_DIR.resolve(fileName);
                long fileSize = Files.size(filePath);
                assertThat(fileSize)
                    .as("Migration " + fileName + " must not be empty")
                    .isGreaterThan(0);
            }
        }
    }
}
