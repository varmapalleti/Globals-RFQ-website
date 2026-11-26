package com.globalrfq.rfq.redflag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveRedFlagRequest(
        @NotBlank String resolution,
        @NotBlank @Size(max = 2000) String note
) { }
