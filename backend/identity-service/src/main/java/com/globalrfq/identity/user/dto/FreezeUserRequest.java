package com.globalrfq.identity.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FreezeUserRequest(@NotBlank @Size(max = 500) String reason) {}
