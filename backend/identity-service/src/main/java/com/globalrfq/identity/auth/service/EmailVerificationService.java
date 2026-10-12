package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;
import com.globalrfq.identity.audit.service.AuditEvent;
import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.dto.request.ResendVerificationRequest;
import com.globalrfq.identity.common.config.AppSecurityProperties;
import com.globalrfq.identity.common.config.FrontendProperties;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.util.HashUtil;
import com.globalrfq.identity.common.util.TokenGenerator;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationTemplate;
import com.globalrfq.identity.notification.service.NotificationService;
import com.globalrfq.identity.token.entity.EmailVerificationToken;
import com.globalrfq.identity.token.repository.EmailVerificationTokenRepository;
import com.globalrfq.identity.user.entity.EmailType;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppSecurityProperties props;
    private final FrontendProperties frontend;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    NotificationService notificationService,
                                    AuditService auditService,
                                    AppSecurityProperties props,
                                    FrontendProperties frontend) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.props = props;
        this.frontend = frontend;
    }

    @Transactional
    public void sendVerification(User user, EmailType type) {
        String email = type == EmailType.OFFICIAL ? user.getOfficialEmail() : user.getPersonalEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        tokenRepository.invalidateActive(user.getId(), type, now);

        String raw = TokenGenerator.randomToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setEmailType(type);
        token.setEmail(email.toLowerCase());
        token.setTokenHash(HashUtil.sha256Hex(raw));
        token.setExpiresAt(now.plus(Duration.ofHours(props.verificationTokenExpiryHours())));
        tokenRepository.save(token);

        String link = frontend.baseUrl() + "/verify-email?token=" + raw;
        notificationService.send(NotificationRequest.email(
                NotificationTemplate.VERIFY_EMAIL, email,
                Map.of("fullName", user.getFullName(),
                        "emailLabel", type == EmailType.OFFICIAL ? "official" : "personal",
                        "expiryHours", String.valueOf(props.verificationTokenExpiryHours()),
                        "verificationLink", link),
                "verificationLink"));

        auditService.record(AuditEvent.of(AuditEventType.EMAIL_VERIFICATION_SENT, AuditOutcome.SUCCESS,
                "VERIFY_EMAIL", null, user.getId(), Map.of("emailType", type.name())));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository
                .findByTokenHashWithUser(HashUtil.sha256Hex(rawToken))
                .orElseThrow(this::invalidLink);
        Instant now = Instant.now();
        User user = token.getUser();

        String currentEmail = token.getEmailType() == EmailType.OFFICIAL
                ? user.getOfficialEmail() : user.getPersonalEmail();
        if (token.isUsed() || token.getExpiresAt().isBefore(now)
                || currentEmail == null || !currentEmail.equalsIgnoreCase(token.getEmail())) {
            throw invalidLink();
        }

        token.setUsed(true);
        token.setUsedAt(now);

        if (token.getEmailType() == EmailType.OFFICIAL) {
            user.setOfficialEmailVerified(true);
            user.setOfficialEmailVerifiedAt(now);
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setStatus(UserStatus.ACTIVE);
            }
        } else {
            user.setPersonalEmailVerified(true);
            user.setPersonalEmailVerifiedAt(now);
        }

        auditService.record(AuditEvent.of(AuditEventType.EMAIL_VERIFIED, AuditOutcome.SUCCESS,
                "VERIFY_EMAIL", user.getId(), user.getId(), Map.of("emailType", token.getEmailType().name())));
    }

    @Transactional
    public void resend(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = (request.emailType() == EmailType.OFFICIAL
                ? userRepository.findByOfficialEmailIgnoreCase(email)
                : userRepository.findByPersonalEmailIgnoreCase(email)).orElse(null);
        if (user == null || user.getStatus() == UserStatus.DISABLED) {
            return;
        }
        boolean alreadyVerified = request.emailType() == EmailType.OFFICIAL
                ? user.isOfficialEmailVerified() : user.isPersonalEmailVerified();
        if (alreadyVerified) {
            return;
        }
        long recent = tokenRepository.countByUserIdAndEmailTypeAndCreatedAtAfter(
                user.getId(), request.emailType(), Instant.now().minus(Duration.ofHours(1)));
        if (recent >= props.otpMaxRequestsPerHour()) {
            return;
        }
        sendVerification(user, request.emailType());
    }

    private BusinessException invalidLink() {
        return new BusinessException(ErrorCode.TOKEN_INVALID, "This verification link is invalid or has expired");
    }
}