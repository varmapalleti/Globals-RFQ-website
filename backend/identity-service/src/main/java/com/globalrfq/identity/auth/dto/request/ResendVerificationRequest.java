package com.globalrfq.identity.auth.dto.request;

import com.globalrfq.identity.user.entity.EmailType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotNull EmailType emailType,
        @NotBlank @Email @Size(max = 190) String email
) {}