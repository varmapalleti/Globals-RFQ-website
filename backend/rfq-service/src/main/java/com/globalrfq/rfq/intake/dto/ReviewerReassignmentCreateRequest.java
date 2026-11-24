package com.globalrfq.rfq.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewerReassignmentCreateRequest(@NotBlank @Size(max = 1000) String reason) { }
