package com.globalrfq.identity.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 150) String fullName,
        @Size(max = 25) String phone,
        @Email @Size(max = 190) String personalEmail
) {
    public boolean hasChanges() {
        return fullName != null || phone != null || personalEmail != null;
    }
}
