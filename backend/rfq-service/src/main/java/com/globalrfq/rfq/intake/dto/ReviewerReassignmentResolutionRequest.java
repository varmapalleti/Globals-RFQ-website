package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewerReassignmentResolutionRequest(
        @NotBlank @Pattern(regexp = "APPROVE|DECLINE") String decision,
        @Positive Long replacementReviewerId,
        @NotBlank @Size(max = 1000) String reason
) { }
