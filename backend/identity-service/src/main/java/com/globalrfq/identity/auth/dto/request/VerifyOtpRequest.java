package com.globalrfq.identity.auth.dto.request;

import com.globalrfq.identity.user.entity.EmailType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotNull(message = "Select official or personal email") EmailType emailType,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank(message = "OTP is required") @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits") String otp
) {}