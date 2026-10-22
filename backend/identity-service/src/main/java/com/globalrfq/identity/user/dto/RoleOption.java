package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.role.entity.Role;
import com.globalrfq.identity.role.entity.RoleName;

public record RoleOption(RoleName name, String description) {
    public static RoleOption from(Role role) {
        return new RoleOption(role.getName(), role.getDescription());
    }
}
