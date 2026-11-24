package com.globalrfq.rfq.quotation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowPolicyRequest(
        @Min(1) @Max(20) int maxFormalRejections,
        @NotBlank @Size(max = 1000) String reason
) { }
