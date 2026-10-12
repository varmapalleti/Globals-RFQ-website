package com.globalrfq.identity.token.repository;

import com.globalrfq.identity.token.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findFirstByUserIdAndUsedFalseOrderByIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    @Modifying
    @Query("update PasswordResetOtp o set o.used = true, o.usedAt = :now where o.user.id = :userId and o.used = false")
    int invalidateActive(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("select o from PasswordResetOtp o join fetch o.user u left join fetch u.roles where o.resetTokenHash = :hash")
    Optional<PasswordResetOtp> findByResetTokenHashWithUser(@Param("hash") String hash);
}
