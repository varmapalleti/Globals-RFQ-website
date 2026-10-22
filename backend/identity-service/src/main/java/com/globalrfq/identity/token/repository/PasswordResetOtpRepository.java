package com.globalrfq.identity.token.repository;

import com.globalrfq.identity.token.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOtp> findFirstByUserIdAndUsedFalseOrderByIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    @Modifying
    @Query("update PasswordResetOtp o set o.used = true, o.usedAt = :now where o.user.id = :userId and o.used = false")
    int invalidateActive(@Param("userId") Long userId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PasswordResetOtp o join fetch o.user u left join fetch u.roles where o.resetTokenHash = :hash")
    Optional<PasswordResetOtp> findByResetTokenHashWithUser(@Param("hash") String hash);

    int deleteByExpiresAtBefore(Instant cutoff);
}
