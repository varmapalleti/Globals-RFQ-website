package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminWorkflowResolutionRequest(
        @NotBlank @Pattern(regexp = "AUTHORIZE_REVISION|CLOSE") String resolution,
        @NotBlank @Size(max = 1000) String reason
) { }
