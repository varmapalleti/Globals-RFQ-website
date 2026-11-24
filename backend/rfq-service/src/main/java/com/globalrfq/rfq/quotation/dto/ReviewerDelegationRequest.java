package com.globalrfq.rfq.quotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewerDelegationRequest(
        boolean quotationManagerCanAssignReviewer,
        @NotBlank @Size(max = 1000) String reason
) { }
