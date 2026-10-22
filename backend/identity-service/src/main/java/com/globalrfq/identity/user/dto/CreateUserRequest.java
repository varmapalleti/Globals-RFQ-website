package com.globalrfq.identity.user.dto;

import com.globalrfq.identity.role.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 190) String officialEmail,
        @NotBlank @Email @Size(max = 190) String personalEmail,
        @Size(max = 25) String phone,
        @NotEmpty Set<RoleName> roles
) {}
