package com.globalrfq.identity.bootstrap;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;
import com.globalrfq.identity.audit.service.AuditEvent;
import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.common.config.BootstrapProperties;
import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.role.repository.RoleRepository;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final BootstrapProperties props;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SuperAdminSeeder(BootstrapProperties props, UserRepository userRepository,
                            RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.props = props;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.superAdminEmail();
        String password = props.superAdminPassword();

        if (email == null || email.isBlank() || password == null || password.length() < 12) {
            log.warn("Super admin seeding skipped: set BOOTSTRAP_ADMIN_EMAIL and a password of 12+ characters");
            return;
        }
        if (userRepository.existsByOfficialEmailIgnoreCase(email)) {
            return;
        }

        Role role = roleRepository.findByName(RoleName.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing; check V2 migration"));

        User admin = new User();
        admin.setFullName(props.superAdminName() == null ? "System Administrator" : props.superAdminName());
        admin.setOfficialEmail(email.trim().toLowerCase());
        admin.setOfficialEmailVerified(true);
        admin.setOfficialEmailVerifiedAt(Instant.now());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(true);
        admin.getRoles().add(role);
        User saved = userRepository.save(admin);

        auditService.record(AuditEvent.of(AuditEventType.USER_CREATED, AuditOutcome.SUCCESS,
                "BOOTSTRAP", null, saved.getId(), null));
        log.info("Bootstrap SUPER_ADMIN created with id={}", saved.getId());
    }
}