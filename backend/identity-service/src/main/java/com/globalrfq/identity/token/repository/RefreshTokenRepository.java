package com.globalrfq.identity.token.repository;

import com.globalrfq.identity.token.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select rt from RefreshToken rt join fetch rt.user u left join fetch u.roles where rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.user.id = :userId and rt.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
