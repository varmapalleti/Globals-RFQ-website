package com.globalrfq.rfq.quotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
        @Size(max = 2000) String reason
) { }
