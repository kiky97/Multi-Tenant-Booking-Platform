package com.booking.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.jwt.secret=test-container-jwt-secret-with-32-plus-bytes!",
                "app.stripe.secret-key=sk_test_container",
                "app.stripe.webhook-secret=whsec_container",
                "app.stripe.publishable-key=pk_test_container",
                "app.admin.bootstrap.enabled=false",
                "spring.jpa.hibernate.ddl-auto=validate"
        })
class PostgresSchemaIntegrationTest {

    private static final UUID SEEDED_HAIR_SALON_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDate TEST_DATE = LocalDate.of(2030, 1, 1);
    private static final LocalDateTime TEST_DATE_TIME = LocalDateTime.of(2030, 1, 1, 12, 0);
    private static final AtomicInteger UNIQUE_SEQUENCE = new AtomicInteger(10_000);

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("booking_engine")
            .withUsername("booking_engine")
            .withPassword("booking_engine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void flywayMigrationShouldCreateValidatedPostgresSchemaWithSeedData() {
        Integer failedMigrations = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE success = FALSE
                        """,
                Integer.class);

        Integer seededSalonCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hair_salon WHERE id = ?",
                Integer.class,
                SEEDED_HAIR_SALON_ID);

        Integer seededSalonHoursCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hair_salon_hour WHERE hair_salon_id = ?",
                Integer.class,
                SEEDED_HAIR_SALON_ID);

        assertThat(failedMigrations).isZero();
        assertThat(seededSalonCount).isEqualTo(1);
        assertThat(seededSalonHoursCount).isEqualTo(7);
    }

    @Test
    void activeBlacklistIndexesShouldRejectDuplicateContactsButAllowInactiveHistory() {
        String suffix = UUID.randomUUID().toString();
        String email = "blocked-" + suffix + "@example.com";
        String phone = "+353" + suffix.replace("-", "").substring(0, 10);

        insertBlacklistEntry(true, email, email, null, null);
        insertBlacklistEntry(true, null, null, phone, phone);

        assertThatThrownBy(() -> insertBlacklistEntry(true, email, email, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_blacklist_entry_email_active");
        assertThatThrownBy(() -> insertBlacklistEntry(true, null, null, phone, phone))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_blacklist_entry_phone_active");

        insertBlacklistEntry(false, email, email, null, null);
        insertBlacklistEntry(false, null, null, phone, phone);
    }

    @Test
    void blacklistContactCheckConstraintShouldRejectEntriesWithoutNormalizedContact() {
        assertThatThrownBy(() -> insertBlacklistEntry(true, "Missing Normalized", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_booking_blacklist_entry_contact");
    }

    @Test
    void stripePaymentIntentIndexesShouldRejectDuplicateNonNullIdsButAllowNullPaymentReferences() {
        UUID employeeId = insertEmployee();
        UUID treatmentId = insertTreatment();
        String bookingPaymentIntentId = "pi_booking_" + UUID.randomUUID().toString().replace("-", "");
        String slotHoldPaymentIntentId = "pi_hold_" + UUID.randomUUID().toString().replace("-", "");

        insertBooking(employeeId, treatmentId, bookingPaymentIntentId);
        assertThatThrownBy(() -> insertBooking(employeeId, treatmentId, bookingPaymentIntentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_stripe_payment_intent_id");

        insertBooking(employeeId, treatmentId, null);
        insertBooking(employeeId, treatmentId, null);

        insertSlotHold(employeeId, treatmentId, slotHoldPaymentIntentId);
        assertThatThrownBy(() -> insertSlotHold(employeeId, treatmentId, slotHoldPaymentIntentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_slot_hold_stripe_payment_intent_id");

        insertSlotHold(employeeId, treatmentId, null);
        insertSlotHold(employeeId, treatmentId, null);
    }

    private void insertBlacklistEntry(
            boolean active,
            String email,
            String emailNormalized,
            String phone,
            String phoneNormalized) {

        jdbcTemplate.update(
                """
                        INSERT INTO booking_blacklist_entry
                            (id, active, email, email_normalized, phone, phone_normalized, reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                active,
                email,
                emailNormalized,
                phone,
                phoneNormalized,
                "container-test");
    }

    private UUID insertEmployee() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO employee (id, name, display_order, bookable)
                        VALUES (?, ?, ?, TRUE)
                        """,
                id,
                "Container Test Employee " + id,
                UNIQUE_SEQUENCE.getAndIncrement());
        return id;
    }

    private UUID insertTreatment() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO treatment (id, name, duration_minutes, price, description, display_order)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id,
                "Container Test Treatment " + id,
                30,
                BigDecimal.valueOf(25),
                "Container test treatment",
                UNIQUE_SEQUENCE.getAndIncrement());
        return id;
    }

    private void insertBooking(UUID employeeId, UUID treatmentId, String paymentIntentId) {
        jdbcTemplate.update(
                """
                        INSERT INTO booking
                            (id, employee_id, treatment_id, customer_name, booking_date,
                             start_time, end_time, status, stripe_payment_intent_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                employeeId,
                treatmentId,
                "Container Test Customer",
                TEST_DATE.plusDays(UNIQUE_SEQUENCE.getAndIncrement()),
                LocalTime.of(10, 0),
                LocalTime.of(10, 30),
                "CONFIRMED",
                paymentIntentId);
    }

    private void insertSlotHold(UUID employeeId, UUID treatmentId, String paymentIntentId) {
        jdbcTemplate.update(
                """
                        INSERT INTO slot_hold
                            (id, employee_id, treatment_id, booking_date, start_time, end_time,
                             hold_scope, expires_at, stripe_payment_intent_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                employeeId,
                treatmentId,
                TEST_DATE.plusDays(UNIQUE_SEQUENCE.getAndIncrement()),
                LocalTime.of(11, 0),
                LocalTime.of(11, 30),
                "PUBLIC",
                TEST_DATE_TIME.plusMinutes(15),
                paymentIntentId);
    }
}
