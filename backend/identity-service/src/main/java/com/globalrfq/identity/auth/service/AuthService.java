package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;
import com.globalrfq.identity.audit.service.AuditEvent;
import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.dto.request.LoginRequest;
import com.globalrfq.identity.auth.dto.response.AuthResponse;
import com.globalrfq.identity.auth.dto.response.AuthResult;
import com.globalrfq.identity.common.config.AppSecurityProperties;
import com.globalrfq.identity.common.config.JwtProperties;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.exception.ResourceNotFoundException;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.common.security.JwtService;
import com.globalrfq.identity.common.util.ClientInfo;
import com.globalrfq.identity.common.util.HashUtil;
import com.globalrfq.identity.common.util.TokenGenerator;
import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.token.entity.RefreshToken;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.dto.UserSummary;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AppSecurityProperties securityProperties;
    private final AuditService auditService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       AppSecurityProperties securityProperties,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }


    public AuthResult login(LoginRequest request, ClientInfo client) {
        String email = request.officialEmail().trim().toLowerCase();
        User user = userRepository.findByOfficialEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditFailure(null, "USER_NOT_FOUND", email);
            throw invalidCredentials();
        }

        if (user.isLockedNow() || user.getStatus() == UserStatus.LOCKED) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditFailure(user.getId(), "ACCOUNT_LOCKED", email);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, email);
            throw invalidCredentials();
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            auditFailure(user.getId(), "ACCOUNT_DISABLED", email);
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION || !user.isOfficialEmailVerified()) {
            auditFailure(user.getId(), "EMAIL_NOT_VERIFIED", email);
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_VERIFIED,
                    "Please verify your official email before signing in");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        AuthResult result = buildAuthResult(user, client);
        auditService.record(AuditEvent.of(AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "LOGIN", user.getId(), user.getId(), null));
        return result;
    }

    // noRollbackFor: the "revoke everything" step on token reuse must be committed.
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) {
            throw tokenInvalid();
        }
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashWithUser(HashUtil.sha256Hex(rawToken))
                .orElseThrow(this::tokenInvalid);
        User user = stored.getUser();

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            auditService.record(AuditEvent.of(AuditEventType.TOKEN_REUSE_DETECTED, AuditOutcome.FAILURE,
                    "REFRESH", user.getId(), user.getId(), null));
            log.warn("Refresh token reuse detected for userId={}", user.getId());
            throw tokenInvalid();
        }
        if (stored.isExpired()) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Session expired. Please sign in again");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            stored.setRevoked(true);
            throw tokenInvalid();
        }

        String newRaw = createRefreshToken(user, client);
        stored.setRevoked(true);
        stored.setReplacedBy(HashUtil.sha256Hex(newRaw));

        String access = jwtService.generateAccessToken(user.getId(), user.getOfficialEmail(), roleNames(user));
        auditService.record(AuditEvent.of(AuditEventType.TOKEN_REFRESHED, AuditOutcome.SUCCESS,
                "REFRESH", user.getId(), user.getId(), null));
        return new AuthResult(
                new AuthResponse(access, "Bearer", jwtService.accessTokenTtlSeconds(), UserSummary.from(user)),
                newRaw, refreshMaxAgeSeconds());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashWithUser(HashUtil.sha256Hex(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            auditService.record(AuditEvent.of(AuditEventType.LOGOUT, AuditOutcome.SUCCESS,
                    "LOGOUT", token.getUser().getId(), token.getUser().getId(), null));
        });
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(AuthenticatedUser principal) {
        User user = userRepository.findWithRolesById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));
        return UserSummary.from(user);
    }

    private AuthResult buildAuthResult(User user, ClientInfo client) {
        String access = jwtService.generateAccessToken(user.getId(), user.getOfficialEmail(), roleNames(user));
        String refresh = createRefreshToken(user, client);
        return new AuthResult(
                new AuthResponse(access, "Bearer", jwtService.accessTokenTtlSeconds(), UserSummary.from(user)),
                refresh, refreshMaxAgeSeconds());
    }

    private String createRefreshToken(User user, ClientInfo client) {
        String raw = TokenGenerator.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(HashUtil.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(jwtProperties.refreshTokenExpiryDays())));
        token.setIpAddress(client.ipAddress());
        token.setUserAgent(client.userAgent());
        refreshTokenRepository.save(token);
        return raw;
    }

    private void registerFailedAttempt(User user, String email) {
        int attempts = user.getFailedLoginAttempts() + 1;
        boolean lock = attempts >= securityProperties.maxFailedLoginAttempts();
        if (lock) {
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(securityProperties.lockDurationMinutes())));
            user.setFailedLoginAttempts(0);
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);

        auditFailure(user.getId(), "INVALID_PASSWORD", email);
        if (lock) {
            auditService.record(AuditEvent.of(AuditEventType.ACCOUNT_LOCKED, AuditOutcome.SUCCESS,
                    "LOCK", null, user.getId(),
                    Map.of("lockMinutes", securityProperties.lockDurationMinutes())));
            log.warn("Account locked after repeated failures: userId={}", user.getId());
        }
    }

    private void auditFailure(Long userId, String reason, String email) {
        auditService.record(AuditEvent.of(AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE,
                "LOGIN", null, userId, Map.of("reason", reason, "email", email)));
    }

    private java.util.List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).map(Enum::name).toList();
    }

    private long refreshMaxAgeSeconds() {
        return Duration.ofDays(jwtProperties.refreshTokenExpiryDays()).toSeconds();
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    private BusinessException tokenInvalid() {
        return new BusinessException(ErrorCode.TOKEN_INVALID, "Invalid or expired session. Please sign in again");
    }
}