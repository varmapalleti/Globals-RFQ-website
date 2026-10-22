package com.globalrfq.identity.user.service;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;
import com.globalrfq.identity.audit.service.AuditEvent;
import com.globalrfq.identity.audit.service.AuditService;
import com.globalrfq.identity.auth.service.EmailVerificationService;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.exception.ResourceNotFoundException;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.common.util.TokenGenerator;
import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.role.repository.RoleRepository;
import com.globalrfq.identity.token.repository.RefreshTokenRepository;
import com.globalrfq.identity.user.dto.*;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserManagementService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserAccessPolicy policy;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;

    public UserManagementService(UserRepository users, RoleRepository roles, UserAccessPolicy policy,
                                 PasswordEncoder passwordEncoder, EmailVerificationService emailVerificationService,
                                 RefreshTokenRepository refreshTokens, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleOption> assignableRoles(AuthenticatedUser principal) {
        User actor = policy.actor(principal);
        Set<RoleName> allowed = policy.assignableRoles(actor);
        return roles.findAllByNameIn(allowed).stream()
                .sorted(Comparator.comparing(Role::getRank).thenComparing(role -> role.getName().name()))
                .map(RoleOption::from).toList();
    }

    @Transactional
    public UserDetailsResponse create(AuthenticatedUser principal, CreateUserRequest request) {
        User actor = policy.actor(principal);
        Set<RoleName> requestedRoles = Set.copyOf(request.roles());
        policy.assertCanAssign(actor, requestedRoles);

        String official = normalize(request.officialEmail());
        String personal = normalize(request.personalEmail());
        if (official.equals(personal) || users.existsByOfficialEmailIgnoreCase(official)
                || users.existsByPersonalEmailIgnoreCase(official)
                || users.existsByOfficialEmailIgnoreCase(personal)
                || users.existsByPersonalEmailIgnoreCase(personal)) {
            throw duplicate("An account already uses one of these email addresses");
        }

        List<Role> assigned = roles.findAllByNameIn(requestedRoles);
        if (assigned.size() != requestedRoles.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "One or more selected roles are not configured");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setOfficialEmail(official);
        user.setPersonalEmail(personal);
        user.setPhone(blankToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(TokenGenerator.randomToken()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setMustChangePassword(true);
        user.setCreatedBy(actor.getId());
        user.getRoles().addAll(assigned);
        User saved = users.save(user);

        emailVerificationService.sendInvite(saved, actor.getId());
        audit.record(AuditEvent.of(AuditEventType.USER_CREATED, AuditOutcome.SUCCESS,
                "CREATE_USER", actor.getId(), saved.getId(), Map.of("roleCount", assigned.size())));
        return UserDetailsResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDetailsResponse> list(AuthenticatedUser principal, String search,
                                                   RoleName roleName, UserStatus status,
                                                   int page, int size, String sortBy, String direction) {
        User actor = policy.actor(principal);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), sort(sortBy, direction));
        Specification<User> spec = scopedToActor(actor);
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), term),
                    cb.like(cb.lower(root.get("officialEmail")), term),
                    cb.like(cb.lower(root.get("personalEmail")), term)));
        }
        if (roleName != null) {
            spec = spec.and((root, query, cb) -> {
                Join<User, Role> role = root.join("roles");
                query.distinct(true);
                return cb.equal(role.get("name"), roleName);
            });
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<User> pageOfUsers = users.findAll(spec, pageable);
        List<Long> ids = pageOfUsers.getContent().stream().map(User::getId).toList();
        Map<Long, User> withRoles = users.findWithRolesByIdIn(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, duplicate) -> first));
        Page<UserDetailsResponse> result = pageOfUsers.map(user -> UserDetailsResponse.from(withRoles.get(user.getId())));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public UserDetailsResponse get(AuthenticatedUser principal, long id) {
        User actor = policy.actor(principal);
        User target = target(id);
        policy.assertCanManage(actor, target);
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public UserDetailsResponse update(AuthenticatedUser principal, long id, UpdateUserRequest request) {
        if (!request.hasChanges()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "At least one profile field must be provided");
        }
        User actor = policy.actor(principal);
        User target = target(id);
        policy.assertCanManage(actor, target);
        boolean personalChanged = false;

        if (request.fullName() != null) {
            if (request.fullName().isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Full name cannot be blank");
            target.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) target.setPhone(blankToNull(request.phone()));
        if (request.personalEmail() != null) {
            String personal = normalize(request.personalEmail());
            if (!Objects.equals(personal, target.getPersonalEmail())) {
                if (personal.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Personal email cannot be blank");
                if (personal.equalsIgnoreCase(target.getOfficialEmail())
                        || users.existsByPersonalEmailIgnoreCase(personal)
                        || users.existsByOfficialEmailIgnoreCase(personal)) {
                    throw duplicate("An account already uses this email address");
                }
                target.setPersonalEmail(personal);
                target.setPersonalEmailVerified(false);
                target.setPersonalEmailVerifiedAt(null);
                personalChanged = true;
            }
        }
        users.save(target);
        if (personalChanged) {
            emailVerificationService.sendVerification(target, com.globalrfq.identity.user.entity.EmailType.PERSONAL);
            audit.record(AuditEvent.of(AuditEventType.PERSONAL_EMAIL_CHANGED, AuditOutcome.SUCCESS,
                    "CHANGE_PERSONAL_EMAIL", actor.getId(), target.getId(), null));
        }
        audit.record(AuditEvent.of(AuditEventType.USER_PROFILE_UPDATED, AuditOutcome.SUCCESS,
                "UPDATE_PROFILE", actor.getId(), target.getId(), null));
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public UserDetailsResponse replaceRoles(AuthenticatedUser principal, long id, AssignRolesRequest request) {
        User actor = policy.actor(principal);
        User target = target(id);
        policy.assertCanManage(actor, target);
        Set<RoleName> assignedNames = Set.copyOf(request.roles());
        policy.assertCanAssign(actor, assignedNames);
        List<Role> assigned = roles.findAllByNameIn(assignedNames);
        if (assigned.size() != assignedNames.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "One or more selected roles are not configured");
        }
        Set<RoleName> priorNames = target.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        if (priorNames.equals(assignedNames)) return UserDetailsResponse.from(target);
        target.getRoles().clear();
        target.getRoles().addAll(assigned);
        audit.record(AuditEvent.of(AuditEventType.USER_ROLES_CHANGED, AuditOutcome.SUCCESS,
                "REPLACE_ROLES", actor.getId(), target.getId(), Map.of(
                        "previousRoles", priorNames.stream().map(Enum::name).sorted().toList(),
                        "newRoles", assignedNames.stream().map(Enum::name).sorted().toList())));
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public UserDetailsResponse freeze(AuthenticatedUser principal, long id, String reason) {
        User actor = policy.actor(principal);
        policy.assertSuperAdmin(actor);
        User target = target(id);
        policy.assertCanManage(actor, target);
        if (target.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "User is already frozen");
        }
        target.setStatusBeforeDisabled(target.getStatus());
        target.setStatus(UserStatus.DISABLED);
        target.setStatusReason(reason.trim());
        target.setStatusChangedBy(actor.getId());
        target.setStatusChangedAt(Instant.now());
        refreshTokens.revokeAllByUserId(target.getId());
        audit.record(AuditEvent.of(AuditEventType.USER_STATUS_CHANGED, AuditOutcome.SUCCESS,
                "FREEZE", actor.getId(), target.getId(), Map.of("reason", reason.trim(), "status", "DISABLED")));
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public UserDetailsResponse unfreeze(AuthenticatedUser principal, long id) {
        User actor = policy.actor(principal);
        policy.assertSuperAdmin(actor);
        User target = target(id);
        policy.assertCanManage(actor, target);
        if (target.getStatus() != UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "User is not frozen");
        }
        UserStatus restored = target.getStatusBeforeDisabled() == null
                ? UserStatus.ACTIVE : target.getStatusBeforeDisabled();
        target.setStatus(restored);
        target.setStatusBeforeDisabled(null);
        target.setStatusReason(null);
        target.setStatusChangedBy(actor.getId());
        target.setStatusChangedAt(Instant.now());
        audit.record(AuditEvent.of(AuditEventType.USER_STATUS_CHANGED, AuditOutcome.SUCCESS,
                "UNFREEZE", actor.getId(), target.getId(), Map.of("status", restored.name())));
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public UserDetailsResponse unlock(AuthenticatedUser principal, long id) {
        User actor = policy.actor(principal);
        User target = target(id);
        policy.assertCanManage(actor, target);
        if (target.getStatus() == UserStatus.DISABLED) throw new BusinessException(ErrorCode.BAD_REQUEST, "Frozen users cannot be unlocked");
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        if (target.getStatus() == UserStatus.LOCKED) target.setStatus(UserStatus.ACTIVE);
        audit.record(AuditEvent.of(AuditEventType.USER_UNLOCKED, AuditOutcome.SUCCESS,
                "UNLOCK", actor.getId(), target.getId(), null));
        return UserDetailsResponse.from(target);
    }

    @Transactional
    public void resendInvite(AuthenticatedUser principal, long id) {
        User actor = policy.actor(principal);
        User target = target(id);
        policy.assertCanManage(actor, target);
        if (target.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only pending users can receive a new invitation");
        }
        emailVerificationService.sendInvite(target, actor.getId());
    }

    private User target(long id) {
        return users.findWithRolesById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Specification<User> scopedToActor(User actor) {
        int actorRank = policy.rank(actor);
        if (actorRank >= UserAccessPolicy.BUSINESS_ROLE_RANK) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You do not have permission to manage users");
        }
        return (root, query, cb) -> {
            Subquery<Long> privileged = query.subquery(Long.class);
            var scopedUser = privileged.from(User.class);
            Join<User, Role> assignedRole = scopedUser.join("roles");
            privileged.select(scopedUser.get("id"))
                    .where(cb.equal(scopedUser.get("id"), root.get("id")),
                            cb.lessThanOrEqualTo(assignedRole.get("rank"), actorRank));
            return cb.not(cb.exists(privileged));
        };
    }

    private Sort sort(String field, String direction) {
        Set<String> allowed = Set.of("fullName", "officialEmail", "createdAt", "status");
        String safeField = allowed.contains(field) ? field : "createdAt";
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(safeDirection, safeField).and(Sort.by(safeDirection, "id"));
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException duplicate(String message) { return new BusinessException(ErrorCode.DUPLICATE_RESOURCE, message); }
}
