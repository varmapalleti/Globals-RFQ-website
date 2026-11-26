package com.globalrfq.identity.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.globalrfq.identity.common.validation.StrongPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required") String resetToken,
        @NotBlank(message = "New password is required") @StrongPassword String newPassword,
        @NotBlank(message = "Confirm password is required") String confirmPassword
) {
    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isConfirmPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[redacted]";
    }
}