package com.booking.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

class SlotHoldRepositoryRetentionPolicyTest {

    @Test
    void expiredUnpaidSlotHoldsShouldBePhysicallyDeletedByCleanup() throws NoSuchMethodException {
        Method method = method("deleteExpiredUnpaidSlotHoldsBefore", LocalDateTime.class);
        String query = normalizeQuery(queryValue(method));

        assertThat(query)
                .isEqualTo("DELETE FROM SlotHoldEntity s "
                        + "WHERE s.active = TRUE "
                        + "AND s.expiresAt < :now "
                        + "AND s.paymentCapturedAt IS NULL "
                        + "AND (s.stripePaymentStatus IS NULL OR s.stripePaymentStatus <> 'succeeded')");
        assertThat(method.getAnnotation(Modifying.class)).isNotNull();
    }

    @Test
    void expiredPaidOrSucceededSlotHoldsShouldNotBePhysicallyDeletedByCleanup() throws NoSuchMethodException {
        String query = normalizeQuery(queryValue(method("deleteExpiredUnpaidSlotHoldsBefore", LocalDateTime.class)));

        assertThat(query)
                .contains(
                        "s.paymentCapturedAt IS NULL",
                        "s.stripePaymentStatus IS NULL OR s.stripePaymentStatus <> 'succeeded'");
        assertThat(query)
                .doesNotContain(
                        "s.paymentCapturedAt IS NOT NULL",
                        "s.stripePaymentStatus = 'succeeded'");
    }

    private static Method method(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SlotHoldRepository.class.getMethod(methodName, parameterTypes);
    }

    private static String queryValue(Method method) {
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        return query.value();
    }

    private static String normalizeQuery(String query) {
        return query.replaceAll("\\s+", " ").trim();
    }
}
