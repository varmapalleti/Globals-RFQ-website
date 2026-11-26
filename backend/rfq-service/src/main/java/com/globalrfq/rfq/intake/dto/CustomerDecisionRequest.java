package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.CustomerDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerDecisionRequest(
        @NotNull CustomerDecision decision,
        @NotNull @Positive Long quotationVersionId,
        @Size(max = 2000) String notes,
        @Size(max = 255) String sourceReference
) { }
