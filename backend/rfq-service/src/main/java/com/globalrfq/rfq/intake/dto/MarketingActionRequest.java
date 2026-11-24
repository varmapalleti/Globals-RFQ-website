package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.Size;

public record MarketingActionRequest(
        @Size(max = 2000) String notes,
        @Size(max = 2000) String outcome
) { }
