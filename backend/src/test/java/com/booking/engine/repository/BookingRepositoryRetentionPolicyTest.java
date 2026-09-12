package com.booking.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.engine.entity.BookingStatus;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class BookingRepositoryRetentionPolicyTest {

    @Test
    void anonymizationShouldClearCustomerPiiButKeepPaymentAuditFieldsAvailable() throws NoSuchMethodException {
        String query = queryValue(
                "anonymizeRetainedFinancialAuditBookingsBefore",
                LocalDate.class,
                List.class,
                String.class);
        String setClause = query.substring(query.indexOf("SET"), query.indexOf("WHERE"));

        assertThat(setClause)
                .contains(
                        "b.customerName = :anonymizedCustomerName",
                        "b.customerEmail = NULL",
                        "b.customerPhone = NULL",
                        "b.holdClientIp = NULL",
                        "b.holdClientDeviceId = NULL",
                        "b.holdAccessTokenHash = NULL");
        assertThat(setClause)
                .doesNotContain(
                        "stripePaymentIntentId",
                        "stripePaymentStatus",
                        "holdAmount",
                        "paymentCapturedAt",
                        "paymentReleasedAt");
        assertThat(query).contains("OR b.stripePaymentStatus = 'succeeded'");
    }

    @Test
    void oldPaidBookingsShouldNotBePhysicallyDeletedByRetentionCleanup() throws NoSuchMethodException {
        String query = queryValue(
                "deleteExpiredUnpaidBookingsBefore",
                LocalDate.class,
                BookingStatus.class);

        assertThat(query)
                .contains(
                        "DELETE FROM BookingEntity b",
                        "b.status = :expiredStatus",
                        "b.paymentCapturedAt IS NULL",
                        "b.stripePaymentStatus IS NULL OR b.stripePaymentStatus <> 'succeeded'");
        assertThat(query)
                .doesNotContain(
                        "b.status IN",
                        "b.paymentCapturedAt IS NOT NULL",
                        "b.stripePaymentStatus = 'succeeded'");
    }

    private static String queryValue(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = BookingRepository.class.getMethod(methodName, parameterTypes);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        return query.value();
    }
}
