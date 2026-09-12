package com.booking.engine.repository;

import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
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
 * Repository for temporary slot reservations.
 */
@Repository
public interface SlotHoldRepository extends JpaRepository<SlotHoldEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SlotHoldEntity s WHERE s.id = :id")
    Optional<SlotHoldEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SlotHoldEntity s WHERE s.stripePaymentIntentId = :paymentIntentId")
    Optional<SlotHoldEntity> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    @Query("""
            SELECT s
            FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.employee.id = :employeeId
              AND s.bookingDate = :bookingDate
              AND s.expiresAt > :now
            """)
    List<SlotHoldEntity> findActiveByEmployeeIdAndBookingDate(
            @Param("employeeId") UUID employeeId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT s
            FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.holdScope = :holdScope
              AND s.holdClientDeviceId = :holdClientDeviceId
              AND s.expiresAt > :now
            """)
    List<SlotHoldEntity> findActiveByScopeAndHoldClientDeviceId(
            @Param("holdScope") SlotHoldScope holdScope,
            @Param("holdClientDeviceId") String holdClientDeviceId,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(s)
            FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.holdScope = :holdScope
              AND s.holdClientIp = :clientIp
              AND s.expiresAt > :now
            """)
    long countActiveByScopeAndHoldClientIp(
            @Param("holdScope") SlotHoldScope holdScope,
            @Param("clientIp") String clientIp,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(s)
            FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.holdScope = :holdScope
              AND s.holdClientDeviceId = :clientDeviceId
              AND s.expiresAt > :now
            """)
    long countActiveByScopeAndHoldClientDeviceId(
            @Param("holdScope") SlotHoldScope holdScope,
            @Param("clientDeviceId") String clientDeviceId,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(s)
            FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.holdScope = :holdScope
              AND s.employee.id = :employeeId
              AND s.bookingDate = :bookingDate
              AND s.startTime = :startTime
              AND s.endTime = :endTime
              AND s.expiresAt > :now
            """)
    long countActiveByScopeAndSlot(
            @Param("holdScope") SlotHoldScope holdScope,
            @Param("employeeId") UUID employeeId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("now") LocalDateTime now);

    /**
     * Physically deletes only expired unpaid slot holds. Paid holds are retained
     * so payment reconciliation can still create the final booking.
     *
     * @param now cutoff timestamp for expiration
     * @return number of deleted rows
     */
    @Modifying
    @Query("""
            DELETE FROM SlotHoldEntity s
            WHERE s.active = TRUE
              AND s.expiresAt < :now
              AND s.paymentCapturedAt IS NULL
              AND (s.stripePaymentStatus IS NULL OR s.stripePaymentStatus <> 'succeeded')
            """)
    int deleteExpiredUnpaidSlotHoldsBefore(@Param("now") LocalDateTime now);
}
