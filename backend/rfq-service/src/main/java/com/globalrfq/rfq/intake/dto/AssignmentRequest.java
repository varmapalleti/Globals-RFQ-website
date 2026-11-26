package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignmentRequest(
        @NotNull AssignmentRole assignmentRole,
        @NotNull @Positive Long assignedUserId,
        @Size(max = 1000) String reason
) { }
