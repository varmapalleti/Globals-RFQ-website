package com.globalrfq.identity.auth.dto.request;

import com.globalrfq.identity.user.entity.EmailType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotNull(message = "Select official or personal email") EmailType emailType,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 190) String email
) {}