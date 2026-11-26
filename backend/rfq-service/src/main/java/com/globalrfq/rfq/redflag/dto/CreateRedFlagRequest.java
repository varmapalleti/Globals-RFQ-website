package com.globalrfq.rfq.redflag.dto;

import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRedFlagRequest(
        @NotBlank @Size(max = 36) String rfqPublicId,
        @NotNull RedFlagSeverity severity,
        @NotBlank @Size(max = 2000) String reason,
        Long assignedTo
) { }
