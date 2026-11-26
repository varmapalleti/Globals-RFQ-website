package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.attachment.dto.RfqAttachmentResponse;

public record RfqImportResultResponse(RfqResponse rfq, RfqAttachmentResponse originalAttachment) { }
