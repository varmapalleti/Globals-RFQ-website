package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerSubmissionRequest(
        @NotNull @Positive Long quotationVersionId,
        @Size(max = 255) String externalReference,
        @Size(max = 2000) String notes
) { }
