package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.user.entity.User;

import java.time.Instant;
import java.util.List;

public record UserDetailsResponse(
        Long id,
        String fullName,
        String officialEmail,
        boolean officialEmailVerified,
        String personalEmail,
        boolean personalEmailVerified,
        String phone,
        String status,
        String statusReason,
        Long statusChangedBy,
        Instant statusChangedAt,
        boolean mustChangePassword,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserDetailsResponse from(User user) {
        return new UserDetailsResponse(user.getId(), user.getFullName(), user.getOfficialEmail(),
                user.isOfficialEmailVerified(), user.getPersonalEmail(), user.isPersonalEmailVerified(),
                user.getPhone(), user.getStatus().name(), user.getStatusReason(), user.getStatusChangedBy(),
                user.getStatusChangedAt(), user.isMustChangePassword(), user.getRoles().stream()
                .map(Role::getName).map(Enum::name).sorted().toList(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
