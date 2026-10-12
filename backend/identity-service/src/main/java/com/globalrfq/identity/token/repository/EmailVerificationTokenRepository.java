package com.globalrfq.identity.token.repository;

import com.globalrfq.identity.token.entity.EmailVerificationToken;
import com.globalrfq.identity.user.entity.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Query("select t from EmailVerificationToken t join fetch t.user where t.tokenHash = :hash")
    Optional<EmailVerificationToken> findByTokenHashWithUser(@Param("hash") String hash);

    long countByUserIdAndEmailTypeAndCreatedAtAfter(Long userId, EmailType emailType, Instant since);

    @Modifying
    @Query("update EmailVerificationToken t set t.used = true, t.usedAt = :now "
            + "where t.user.id = :userId and t.emailType = :type and t.used = false")
    int invalidateActive(@Param("userId") Long userId, @Param("type") EmailType type, @Param("now") Instant now);
}