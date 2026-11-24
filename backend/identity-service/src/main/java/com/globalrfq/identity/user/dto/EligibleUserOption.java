package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.user.entity.User;

public record EligibleUserOption(
        Long id,
        String fullName,
        String officialEmail
) {
    public static EligibleUserOption from(User user) {
        return new EligibleUserOption(user.getId(), user.getFullName(), user.getOfficialEmail());
    }
}
