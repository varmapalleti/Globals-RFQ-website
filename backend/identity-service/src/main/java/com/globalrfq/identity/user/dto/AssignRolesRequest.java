package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.role.entity.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignRolesRequest(@NotEmpty Set<RoleName> roles) {}
