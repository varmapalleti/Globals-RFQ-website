package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.dto.request.ForgotPasswordRequest;
import com.globalrfq.identity.common.config.AppSecurityProperties;
import com.globalrfq.identity.common.util.ClientInfo;
import com.globalrfq.identity.notification.service.NotificationService;
import com.globalrfq.identity.token.repository.PasswordResetOtpRepository;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.entity.EmailType;
import com.globalrfq.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private UserRepository users;
    @Mock private PasswordResetOtpRepository otps;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwords;
    @Mock private NotificationService notifications;
    @Mock private AuditService audit;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        when(passwords.encode(anyString())).thenReturn("test-dummy-hash");
        service = new PasswordResetService(users, otps, refreshTokens, passwords, notifications, audit,
                new AppSecurityProperties(5, 15, 10, 5, 5, 24, false));
    }

    @Test
    void forgotPasswordForUnknownEmailDoesNotRevealAccountExistenceOrSendNotice() {
        when(users.findByOfficialEmailIgnoreCase("unknown@example.test")).thenReturn(Optional.empty());

        service.requestOtp(new ForgotPasswordRequest(EmailType.OFFICIAL, "Unknown@Example.Test"),
                new ClientInfo("127.0.0.1", "JUnit"));

        verify(audit).record(any());
        verifyNoInteractions(notifications);
        verify(otps, never()).save(any());
    }
}
