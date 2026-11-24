package com.globalrfq.rfq.attachment.dto;

import com.globalrfq.rfq.attachment.entity.RfqAttachment;

public record PendingAttachmentScanResponse(String id, String sha256, long sizeBytes, String mediaType,
                                           String downloadPath) {
    public static PendingAttachmentScanResponse from(RfqAttachment attachment) {
        return new PendingAttachmentScanResponse(attachment.getPublicId(), attachment.getSha256(),
                attachment.getByteSize(), attachment.getDetectedMediaType(),
                "/internal/attachment-scanner/attachments/" + attachment.getPublicId() + "/content");
    }
}
