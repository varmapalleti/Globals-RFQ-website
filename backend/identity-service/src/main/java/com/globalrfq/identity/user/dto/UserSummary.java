package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.user.entity.User;

import java.util.List;

public record UserSummary(
        Long id,
        String fullName,
        String officialEmail,
        boolean officialEmailVerified,
        boolean personalEmailVerified,
        String status,
        boolean mustChangePassword,
        List<String> roles
) {
    public static UserSummary from(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .sorted()
                .toList();
        return new UserSummary(user.getId(), user.getFullName(), user.getOfficialEmail(),
                user.isOfficialEmailVerified(), user.isPersonalEmailVerified(),
                user.getStatus().name(), user.isMustChangePassword(), roles);
    }
}
