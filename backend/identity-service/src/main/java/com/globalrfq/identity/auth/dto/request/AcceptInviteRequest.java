package com.globalrfq.identity.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.globalrfq.identity.common.validation.StrongPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @StrongPassword String newPassword,
        @NotBlank String confirmPassword
) {
    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isConfirmPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @Override
    public String toString() {
        return "AcceptInviteRequest[redacted]";
    }
}
