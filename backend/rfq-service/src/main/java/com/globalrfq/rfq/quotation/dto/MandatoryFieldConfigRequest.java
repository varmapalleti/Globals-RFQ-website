package com.globalrfq.rfq.quotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MandatoryFieldConfigRequest(
        @NotNull @Size(max = 40) List<@NotBlank @Size(max = 80) String> requiredFields,
        @NotBlank @Size(max = 1000) String reason
) { }
