package com.globalrfq.identity.user.service;

import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserAccessPolicy {
    public static final int SUPER_ADMIN_RANK = 1;
    public static final int ADMIN_RANK = 2;
    public static final int BUSINESS_ROLE_RANK = 3;

    private final UserRepository users;

    public UserAccessPolicy(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User actor(AuthenticatedUser principal) {
        return users.findWithRolesById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required"));
    }

    public int rank(User user) {
        return user.getRoles().stream().mapToInt(Role::getRank).min().orElse(Integer.MAX_VALUE);
    }

    public Set<RoleName> assignableRoles(User actor) {
        int actorRank = rank(actor);
        if (actorRank != SUPER_ADMIN_RANK && actorRank != ADMIN_RANK) {
            return Set.of();
        }
        return Arrays.stream(RoleName.values())
                .filter(name -> name != RoleName.SUPER_ADMIN)
                .filter(name -> actorRank < rankFor(name))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void assertCanManage(User actor, User target) {
        if (actor.getId().equals(target.getId())) {
            throw denied();
        }
        if (rank(actor) >= rank(target)) {
            throw denied();
        }
    }

    public void assertCanAssign(User actor, Set<RoleName> roles) {
        Set<RoleName> allowed = assignableRoles(actor);
        if (roles == null || roles.isEmpty() || !allowed.containsAll(roles)) {
            throw denied();
        }
    }

    public boolean isStrictlyBelow(User target, User actor) {
        int actorRank = rank(actor);
        return !target.getRoles().isEmpty() && target.getRoles().stream().allMatch(role -> role.getRank() > actorRank);
    }

    public void assertSuperAdmin(User actor) {
        if (rank(actor) != SUPER_ADMIN_RANK) {
            throw denied();
        }
    }

    private int rankFor(RoleName name) {
        return switch (name) {
            case SUPER_ADMIN -> SUPER_ADMIN_RANK;
            case ADMIN -> ADMIN_RANK;
            default -> BUSINESS_ROLE_RANK;
        };
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
    }
}
