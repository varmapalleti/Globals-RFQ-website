package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MarketingAssignmentRequest(
        @NotNull @Positive Long assignedUserId,
        @Size(max = 1000) String reason
) { }
