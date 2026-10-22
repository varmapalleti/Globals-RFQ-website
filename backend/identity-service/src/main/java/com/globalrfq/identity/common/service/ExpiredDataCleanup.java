package com.globalrfq.identity.common.service;

import com.globalrfq.identity.audit.repository.SecurityAuditLogRepository;
import com.globalrfq.identity.common.config.CleanupProperties;
import com.globalrfq.identity.token.repository.EmailVerificationTokenRepository;
import com.globalrfq.identity.token.repository.PasswordResetOtpRepository;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ExpiredDataCleanup {
    private static final Logger log = LoggerFactory.getLogger(ExpiredDataCleanup.class);

    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetOtpRepository resetOtps;
    private final SecurityAuditLogRepository auditLogs;
    private final CleanupProperties properties;

    public ExpiredDataCleanup(RefreshTokenRepository refreshTokens,
                              EmailVerificationTokenRepository verificationTokens,
                              PasswordResetOtpRepository resetOtps,
                              SecurityAuditLogRepository auditLogs,
                              CleanupProperties properties) {
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.resetOtps = resetOtps;
        this.auditLogs = auditLogs;
        this.properties = properties;
    }

    @Scheduled(cron = "0 17 2 * * *", zone = "UTC")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        int refreshCount = refreshTokens.deleteExpired(now);
        int verificationCount = verificationTokens.deleteByExpiresAtBefore(now);
        int otpCount = resetOtps.deleteByExpiresAtBefore(now);
        int auditCount = 0;
        if (properties.auditRetentionDays() > 0) {
            auditCount = auditLogs.deleteOlderThan(now.minus(properties.auditRetentionDays(), ChronoUnit.DAYS));
        }
        if (refreshCount + verificationCount + otpCount + auditCount > 0) {
            log.info("Expired identity data cleanup removed refreshTokens={}, verificationTokens={}, resetOtps={}, auditLogs={}",
                    refreshCount, verificationCount, otpCount, auditCount);
        }
    }
}
