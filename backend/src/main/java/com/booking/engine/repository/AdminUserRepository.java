package com.booking.engine.repository;

import com.booking.engine.entity.User;
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
public interface AdminUserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndActiveTrue(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUserEntity u where u.username = :username and u.active = true")
    Optional<User> findByUsernameAndActiveTrueForUpdate(@Param("username") String username);

    Optional<User> findByUsername(String username);

    boolean existsByActiveTrue();

    boolean existsByUsername(String username);
}
