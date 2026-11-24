package com.globalrfq.rfq.attachment.dto;

import com.globalrfq.rfq.attachment.entity.RfqAttachment;

import java.time.Instant;

public record RfqAttachmentResponse(String id, String fileName, String extension, String mediaType,
                                    long sizeBytes, String sha256, String category, String scanState,
                                    Integer lineNumber, Long uploadedByUserId, Instant createdAt) {
    public static RfqAttachmentResponse from(RfqAttachment attachment) {
        return new RfqAttachmentResponse(attachment.getPublicId(), attachment.getOriginalFileName(),
                attachment.getFileExtension(), attachment.getDetectedMediaType(), attachment.getByteSize(),
                attachment.getSha256(), attachment.getCategory().name(),
                attachment.getScanState().name(), attachment.getLineItem() == null ? null : attachment.getLineItem().getLineNumber(),
                attachment.getUploadedByUserId(), attachment.getCreatedAt());
    }
}
