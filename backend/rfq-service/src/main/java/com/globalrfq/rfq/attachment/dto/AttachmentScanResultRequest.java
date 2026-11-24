package com.globalrfq.rfq.attachment.dto;

import com.globalrfq.rfq.attachment.entity.AttachmentScanState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttachmentScanResultRequest(
        @NotNull AttachmentScanState state,
        @NotBlank @Size(max = 120) String scannerName,
        @Pattern(regexp = "^$|[A-Za-z0-9._:-]{1,255}") String scanReference
) { }
