package com.globalrfq.identity.auth.service;

import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.common.config.AppSecurityProperties;
import com.globalrfq.identity.common.config.JwtProperties;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.security.JwtService;
import com.globalrfq.identity.common.util.ClientInfo;
import com.globalrfq.identity.notification.service.NotificationService;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.repository.UserRepository;
import com.globalrfq.identity.auth.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtService jwt;
    @Mock private AuditService audit;
    @Mock private NotificationService notifications;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwords.encode(anyString())).thenReturn("test-dummy-hash");
        service = new AuthService(users, refreshTokens, passwords, jwt,
                new JwtProperties("dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcyE=", "test", 15, 30),
                new AppSecurityProperties(5, 15, 10, 5, 5, 24, false), audit, notifications);
    }

    @Test
    void loginForUnknownEmailUsesDummyPasswordCheckAndAuditsFailure() {
        when(users.findForLoginByOfficialEmail("missing@example.test")).thenReturn(Optional.empty());
        when(passwords.matches("bad-password", "test-dummy-hash")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.login(
                new LoginRequest("Missing@Example.Test", "bad-password"),
                new ClientInfo("127.0.0.1", "JUnit")));

        verify(passwords).matches("bad-password", "test-dummy-hash");
        verify(audit).record(any());
        verifyNoInteractions(refreshTokens, jwt, notifications);
    }
}
