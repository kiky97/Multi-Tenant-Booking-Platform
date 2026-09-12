package com.booking.engine.repository;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link BookingEntity} persistence and queries.
 */
@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    List<BookingEntity> findByEmployeeIdAndBookingDateAndStatusIn(
            UUID employeeId,
            LocalDate bookingDate,
            List<BookingStatus> statuses);

    Optional<BookingEntity> findByStripePaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.stripePaymentIntentId = :paymentIntentId")
    Optional<BookingEntity> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEmployeeIdAndStatusIn(UUID employeeId, List<BookingStatus> statuses);

    /**
     * Checks whether the employee still has future bookings for the treatment.
     *
     * @param employeeId  employee identifier
     * @param treatmentId treatment identifier
     * @param statuses    booking statuses to match
     * @param today       current booking date
     * @param nowTime     current booking time
     * @return true when future bookings exist for the employee-treatment pair
     */
    @Query("""
            SELECT COUNT(b) > 0
            FROM BookingEntity b
            WHERE b.employee.id = :employeeId
              AND b.treatment.id = :treatmentId
              AND b.status IN :statuses
              AND (
                    b.bookingDate > :today
                    OR (b.bookingDate = :today AND b.endTime > :nowTime)
              )
            """)
    boolean existsFutureBookingsByEmployeeIdAndTreatmentIdAndStatusIn(
            @Param("employeeId") UUID employeeId,
            @Param("treatmentId") UUID treatmentId,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("today") LocalDate today,
            @Param("nowTime") LocalTime nowTime);

    List<BookingEntity> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime now);

    /**
     * Finds pending bookings whose hold window has expired and payment has not
     * been captured successfully yet.
     *
     * @param status booking status to match
     * @param now    cutoff timestamp for expiration
     * @return matching expired unpaid bookings
     */
    @Query("""
                SELECT b
                FROM BookingEntity b
                WHERE b.status = :status
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt < :now
                AND b.paymentCapturedAt IS NULL
                AND (b.stripePaymentStatus IS NULL OR b.stripePaymentStatus <> 'succeeded')
            """)
    List<BookingEntity> findExpiredUnpaidPendingBookings(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Reconciles paid pending bookings into confirmed state in one update
     * statement.
     *
     * @param currentStatus pending status to replace
     * @param newStatus     target confirmed status
     * @param capturedAt    fallback capture timestamp for rows missing one
     * @return number of updated rows
     */
    @Modifying
    @Query("""
                UPDATE BookingEntity b
                SET b.status = :newStatus,
                    b.expiresAt = NULL,
                    b.paymentReleasedAt = NULL,
                    b.slotLocked = FALSE,
                    b.paymentCapturedAt = COALESCE(b.paymentCapturedAt, :capturedAt)
                WHERE b.active = TRUE
                AND b.status = :currentStatus
                AND (b.paymentCapturedAt IS NOT NULL OR b.stripePaymentStatus = 'succeeded')
            """)
    int reconcilePaidPendingBookings(
            @Param("currentStatus") BookingStatus currentStatus,
            @Param("newStatus") BookingStatus newStatus,
            @Param("capturedAt") LocalDateTime capturedAt);

    /**
     * Finds pending bookings whose payment has already succeeded and therefore
     * should be reconciled into confirmed state.
     *
     * @param status booking status to match
     * @return matching paid pending bookings
     */
    @Query("""
                SELECT b
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND (b.paymentCapturedAt IS NOT NULL OR b.stripePaymentStatus = 'succeeded')
            """)
    List<BookingEntity> findPaidPendingBookings(@Param("status") BookingStatus status);

    /**
     * Expires unpaid pending bookings in one update statement.
     *
     * @param currentStatus pending status to replace
     * @param newStatus     target expired status
     * @param now           cutoff timestamp for expiration
     * @return number of updated rows
     */
    @Modifying
    @Query("""
                UPDATE BookingEntity b
                SET b.status = :newStatus,
                    b.expiresAt = NULL
                WHERE b.status = :currentStatus
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt < :now
                AND b.paymentCapturedAt IS NULL
                AND (b.stripePaymentStatus IS NULL OR b.stripePaymentStatus <> 'succeeded')
            """)
    int expireUnpaidPendingBookings(
            @Param("currentStatus") BookingStatus currentStatus,
            @Param("newStatus") BookingStatus newStatus,
            @Param("now") LocalDateTime now);

    /**
     * Marks confirmed bookings as done when their end date/time is already in the
     * past.
     *
     * @param today         current date
     * @param nowTime       current local time
     * @param currentStatus status to replace
     * @param newStatus     target status
     * @return number of updated rows
     */
    @Modifying
    @Query("""
                UPDATE BookingEntity b
                SET b.status = :newStatus
                WHERE b.status = :currentStatus
                AND (
                    b.bookingDate < :today
                    OR (b.bookingDate = :today AND b.endTime < :nowTime)
                )
            """)
    int completeConfirmedBookings(
            LocalDate today,
            LocalTime nowTime,
            BookingStatus currentStatus,
            BookingStatus newStatus);

    /**
     * Finds all active bookings with employee and treatment eagerly loaded.
     *
     * @return active bookings with related entities
     */
    @Query("""
                SELECT b
                FROM BookingEntity b
                JOIN FETCH b.employee
                JOIN FETCH b.treatment
                WHERE b.active = TRUE
            """)
    List<BookingEntity> findAllActiveWithEmployeeAndTreatment();

    List<BookingEntity> findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc();

    List<BookingEntity> findByHoldClientDeviceIdAndStatus(
            String holdClientDeviceId,
            BookingStatus status);

    long countByActiveTrueAndStatus(BookingStatus status);

    /**
     * Counts active unpaid slot holds for a client IP address.
     *
     * @param clientIp client IP address
     * @param status   hold status to match
     * @param now      current timestamp
     * @return number of active unpaid holds
     */
    @Query("""
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND b.paymentCapturedAt IS NULL
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt > :now
                AND b.holdClientIp = :clientIp
            """)
    long countActiveUnpaidHoldsByClientIp(
            @Param("clientIp") String clientIp,
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Counts active unpaid slot holds for a client device identifier.
     *
     * @param clientDeviceId persistent client device identifier
     * @param status         hold status to match
     * @param now            current timestamp
     * @return number of active unpaid holds
     */
    @Query("""
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND b.paymentCapturedAt IS NULL
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt > :now
                AND b.holdClientDeviceId = :clientDeviceId
            """)
    long countActiveUnpaidHoldsByClientDeviceId(
            @Param("clientDeviceId") String clientDeviceId,
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Counts active unpaid booking holds for one employee/date/time slot.
     *
     * @param employeeId  employee identifier
     * @param bookingDate booking date
     * @param startTime   slot start time
     * @param endTime     slot end time
     * @param status      hold status to match
     * @param now         current timestamp
     * @return number of active unpaid holds for the slot
     */
    @Query("""
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND b.paymentCapturedAt IS NULL
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt > :now
                AND b.employee.id = :employeeId
                AND b.bookingDate = :bookingDate
                AND b.startTime = :startTime
                AND b.endTime = :endTime
            """)
    long countActiveUnpaidHoldsForSlot(
            @Param("employeeId") UUID employeeId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Anonymizes customer and hold-access PII for retained financial audit rows
     * while preserving Stripe identifiers, amounts, statuses, timestamps, and
     * booking/treatment/employee links needed for payment disputes.
     *
     * @param cutoffDate             exclusive cutoff date
     * @param retainedStatuses       booking statuses retained for financial audit
     * @param anonymizedCustomerName replacement customer name
     * @return number of anonymized rows
     */
    @Modifying
    @Query("""
            UPDATE BookingEntity b
            SET b.customerName = :anonymizedCustomerName,
                b.customerEmail = NULL,
                b.customerPhone = NULL,
                b.holdClientIp = NULL,
                b.holdClientDeviceId = NULL,
                b.holdAccessTokenHash = NULL
            WHERE b.bookingDate < :cutoffDate
              AND (
                    b.status IN :retainedStatuses
                    OR b.paymentCapturedAt IS NOT NULL
                    OR b.stripePaymentStatus = 'succeeded'
              )
              AND (
                    (b.customerName IS NOT NULL AND b.customerName <> :anonymizedCustomerName)
                    OR b.customerEmail IS NOT NULL
                    OR b.customerPhone IS NOT NULL
                    OR b.holdClientIp IS NOT NULL
                    OR b.holdClientDeviceId IS NOT NULL
                    OR b.holdAccessTokenHash IS NOT NULL
              )
            """)
    int anonymizeRetainedFinancialAuditBookingsBefore(
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("retainedStatuses") List<BookingStatus> retainedStatuses,
            @Param("anonymizedCustomerName") String anonymizedCustomerName);

    /**
     * Physically deletes only old unpaid expired booking holds. Paid, confirmed,
     * and completed bookings are retained for financial audit/dispute review.
     *
     * @param cutoffDate    exclusive cutoff date
     * @param expiredStatus expired booking status
     * @return number of deleted rows
     */
    @Modifying
    @Query("""
            DELETE FROM BookingEntity b
            WHERE b.bookingDate < :cutoffDate
              AND b.status = :expiredStatus
              AND b.paymentCapturedAt IS NULL
              AND (b.stripePaymentStatus IS NULL OR b.stripePaymentStatus <> 'succeeded')
            """)
    int deleteExpiredUnpaidBookingsBefore(
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("expiredStatus") BookingStatus expiredStatus);
}
