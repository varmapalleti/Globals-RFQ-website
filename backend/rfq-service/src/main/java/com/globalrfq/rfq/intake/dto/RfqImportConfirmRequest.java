package com.globalrfq.rfq.intake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RfqImportConfirmRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String previewToken,
        @Valid CreateRfqRequest normalizedRfq
) { }
