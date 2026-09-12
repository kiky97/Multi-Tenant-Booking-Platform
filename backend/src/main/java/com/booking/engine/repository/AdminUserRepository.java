package com.booking.engine.repository;

import com.booking.engine.entity.AdminUserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for admin user authentication data.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    Optional<AdminUserEntity> findByUsernameAndActiveTrue(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUserEntity u where u.username = :username and u.active = true")
    Optional<AdminUserEntity> findByUsernameAndActiveTrueForUpdate(@Param("username") String username);

    Optional<AdminUserEntity> findByUsername(String username);

    boolean existsByActiveTrue();

    boolean existsByUsername(String username);
}
