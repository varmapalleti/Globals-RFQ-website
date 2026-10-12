package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;
import com.globalrfq.identity.audit.service.AuditEvent;
import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.dto.request.ForgotPasswordRequest;
import com.globalrfq.identity.auth.dto.request.ResetPasswordRequest;
import com.globalrfq.identity.auth.dto.request.VerifyOtpRequest;
import com.globalrfq.identity.auth.dto.response.ResetTokenResponse;
import com.globalrfq.identity.common.config.AppSecurityProperties;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.util.ClientInfo;
import com.globalrfq.identity.common.util.HashUtil;
import com.globalrfq.identity.common.util.MaskingUtil;
import com.globalrfq.identity.common.util.OtpGenerator;
import com.globalrfq.identity.common.util.TokenGenerator;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationTemplate;
import com.globalrfq.identity.notification.service.NotificationService;
import com.globalrfq.identity.token.entity.PasswordResetOtp;
import com.globalrfq.identity.token.repository.PasswordResetOtpRepository;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.entity.EmailType;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final long RESET_TOKEN_MINUTES = 10;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppSecurityProperties props;
    private final String dummyHash;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetOtpRepository otpRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                NotificationService notificationService,
                                AuditService auditService,
                                AppSecurityProperties props) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.props = props;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public void requestOtp(ForgotPasswordRequest request, ClientInfo client) {
        String email = normalize(request.email());
        User user = findEligibleUser(request.emailType(), email).orElse(null);

        if (user == null) {
            passwordEncoder.encode(OtpGenerator.sixDigits());
            audit(AuditEventType.PASSWORD_RESET_OTP_FAILED, AuditOutcome.FAILURE, null,
                    Map.of("reason", "NO_ELIGIBLE_ACCOUNT", "email", MaskingUtil.maskEmail(email)));
            return;
        }

        Instant now = Instant.now();
        long recent = otpRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
        if (recent >= props.otpMaxRequestsPerHour()) {
            audit(AuditEventType.PASSWORD_RESET_RATE_LIMITED, AuditOutcome.FAILURE, user.getId(),
                    Map.of("email", MaskingUtil.maskEmail(email)));
            return;
        }

        otpRepository.invalidateActive(user.getId(), now);

        String otp = OtpGenerator.sixDigits();
        PasswordResetOtp row = new PasswordResetOtp();
        row.setUser(user);
        row.setEmailType(request.emailType());
        row.setDeliveryEmail(email);
        row.setOtpHash(passwordEncoder.encode(otp));
        row.setExpiresAt(now.plus(Duration.ofMinutes(props.otpExpiryMinutes())));
        row.setRequestedIp(client.ipAddress());
        otpRepository.save(row);

        notificationService.send(NotificationRequest.email(
                NotificationTemplate.PASSWORD_RESET_OTP, email,
                Map.of("fullName", user.getFullName(), "otp", otp,
                        "expiryMinutes", String.valueOf(props.otpExpiryMinutes())),
                "otp"));

        audit(AuditEventType.PASSWORD_RESET_OTP_SENT, AuditOutcome.SUCCESS, user.getId(),
                Map.of("emailType", request.emailType().name()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ResetTokenResponse verifyOtp(VerifyOtpRequest request, ClientInfo client) {
        String email = normalize(request.email());
        User user = findEligibleUser(request.emailType(), email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.otp(), dummyHash);
            throw otpInvalid();
        }

        Instant now = Instant.now();
        PasswordResetOtp otp = otpRepository.findFirstByUserIdAndUsedFalseOrderByIdDesc(user.getId()).orElse(null);
        if (otp == null || otp.getVerifiedAt() != null || otp.getExpiresAt().isBefore(now)
                || otp.getEmailType() != request.emailType()
                || !otp.getDeliveryEmail().equalsIgnoreCase(email)
                || otp.getAttemptCount() >= props.otpMaxAttempts()) {
            passwordEncoder.matches(request.otp(), dummyHash);
            throw otpInvalid();
        }

        if (!passwordEncoder.matches(request.otp(), otp.getOtpHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            if (otp.getAttemptCount() >= props.otpMaxAttempts()) {
                otp.setUsed(true);
                otp.setUsedAt(now);
            }
            audit(AuditEventType.PASSWORD_RESET_OTP_FAILED, AuditOutcome.FAILURE, user.getId(),
                    Map.of("reason", "WRONG_OTP", "attempt", otp.getAttemptCount()));
            throw otpInvalid();
        }

        String rawToken = TokenGenerator.randomToken();
        otp.setVerifiedAt(now);
        otp.setResetTokenHash(HashUtil.sha256Hex(rawToken));
        otp.setResetTokenExpiresAt(now.plus(Duration.ofMinutes(RESET_TOKEN_MINUTES)));

        audit(AuditEventType.PASSWORD_RESET_OTP_VERIFIED, AuditOutcome.SUCCESS, user.getId(), null);
        return new ResetTokenResponse(rawToken, Duration.ofMinutes(RESET_TOKEN_MINUTES).toSeconds());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetOtp otp = otpRepository
                .findByResetTokenHashWithUser(HashUtil.sha256Hex(request.resetToken()))
                .orElseThrow(this::sessionInvalid);
        Instant now = Instant.now();

        if (otp.isUsed() || otp.getVerifiedAt() == null || otp.getResetTokenExpiresAt() == null
                || otp.getResetTokenExpiresAt().isBefore(now)) {
            throw sessionInvalid();
        }
        User user = otp.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw sessionInvalid();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "New password must be different from your current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        otp.setUsed(true);
        otp.setUsedAt(now);
        refreshTokenRepository.revokeAllByUserId(user.getId());

        audit(AuditEventType.PASSWORD_RESET_SUCCESS, AuditOutcome.SUCCESS, user.getId(), null);
        sendPasswordChangedNotice(user, now);
    }

    private void sendPasswordChangedNotice(User user, Instant when) {
        Map<String, String> vars = Map.of("fullName", user.getFullName(), "changedAt", TIME_FORMAT.format(when));
        notificationService.send(NotificationRequest.email(
                NotificationTemplate.PASSWORD_CHANGED, user.getOfficialEmail(), vars));
        if (user.getPersonalEmail() != null && user.isPersonalEmailVerified()) {
            notificationService.send(NotificationRequest.email(
                    NotificationTemplate.PASSWORD_CHANGED, user.getPersonalEmail(), vars));
        }
    }

    private Optional<User> findEligibleUser(EmailType type, String email) {
        Optional<User> found = type == EmailType.OFFICIAL
                ? userRepository.findByOfficialEmailIgnoreCase(email)
                : userRepository.findByPersonalEmailIgnoreCase(email);
        return found
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> type == EmailType.OFFICIAL ? u.isOfficialEmailVerified() : u.isPersonalEmailVerified());
    }

    private void audit(AuditEventType type, AuditOutcome outcome, Long userId, Map<String, Object> metadata) {
        auditService.record(AuditEvent.of(type, outcome, "PASSWORD_RESET", null, userId, metadata));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private BusinessException otpInvalid() {
        return new BusinessException(ErrorCode.OTP_INVALID, "Invalid or expired code");
    }

    private BusinessException sessionInvalid() {
        return new BusinessException(ErrorCode.TOKEN_INVALID,
                "Your reset session is invalid or has expired. Please start again");
    }
}