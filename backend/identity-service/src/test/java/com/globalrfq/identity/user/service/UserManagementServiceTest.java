package com.globalrfq.identity.user.service;

import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.service.EmailVerificationService;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.role.repository.RoleRepository;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {
    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private UserAccessPolicy policy;
    @Mock private PasswordEncoder passwords;
    @Mock private EmailVerificationService emailVerification;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private AuditService audit;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(users, roles, policy, passwords, emailVerification, refreshTokens, audit);
    }

    @Test
    void assignableRolesReturnsOnlyPolicyApprovedRolesInRankOrder() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "admin@example.test", Set.of("ADMIN"));
        User actor = new User();
        Role manager = new Role();
        manager.setName(RoleName.QUOTATION_MANAGER);
        manager.setDescription("Prepare quotations");
        manager.setRank(3);
        Role admin = new Role();
        admin.setName(RoleName.ADMIN);
        admin.setDescription("Manage system users");
        admin.setRank(2);
        when(policy.actor(principal)).thenReturn(actor);
        when(policy.assignableRoles(actor)).thenReturn(Set.of(RoleName.QUOTATION_MANAGER, RoleName.ADMIN));
        when(roles.findAllByNameIn(anyCollection())).thenReturn(List.of(manager, admin));

        var result = service.assignableRoles(principal);

        assertEquals(List.of(RoleName.ADMIN, RoleName.QUOTATION_MANAGER),
                result.stream().map(option -> option.name()).toList());
        verify(roles).findAllByNameIn(Set.of(RoleName.QUOTATION_MANAGER, RoleName.ADMIN));
    }
}
