package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlywayProductionMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Path APPLICATION_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final String INITIAL_MIGRATION = "V1__init_schema.sql";
    private static final String LEGACY_ADMIN_HASH = "$2a$10$/b8Kp9eds4FUCFT6ETS9jO2HiucVz4k3MUBdkP1BNCmX9iHZKJUmW";
    private static final String SEEDED_HAIR_SALON_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void migrationDirectoryContainsOnlyInitialProductionMigration() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            List<String> migrations = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();

            assertThat(migrations).containsExactly(INITIAL_MIGRATION);
        }
    }

    @Test
    void initialProductionMigrationContainsFinalSchemaAndNoSeededAdminCredentials() throws IOException {
        String sql = Files.readString(MIGRATION_DIR.resolve(INITIAL_MIGRATION));

        assertThat(sql).doesNotContain("INSERT INTO admin_user");
        assertThat(sql).doesNotContain(LEGACY_ADMIN_HASH);
        assertThat(sql).doesNotContain("is_barber BOOLEAN");
        assertThat(sql).contains("bookable BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(sql).contains("slot_locked BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(sql).contains("hold_access_token_hash VARCHAR(128)");
        assertThat(sql).contains("CREATE TABLE employee_treatment");
        assertThat(sql).contains("CREATE TABLE slot_hold");
        assertThat(sql).contains("failed_login_attempts INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("token_version INTEGER NOT NULL DEFAULT 0");
    }

    @Test
    void defaultHairSalonConfigurationMatchesSeededSingletonSalon() throws IOException {
        String sql = Files.readString(MIGRATION_DIR.resolve(INITIAL_MIGRATION));
        String applicationProperties = Files.readString(APPLICATION_PROPERTIES);

        assertThat(sql).contains("'" + SEEDED_HAIR_SALON_ID + "'");
        assertThat(applicationProperties).contains("APP_HAIR_SALON_ID:" + SEEDED_HAIR_SALON_ID);
    }
}
