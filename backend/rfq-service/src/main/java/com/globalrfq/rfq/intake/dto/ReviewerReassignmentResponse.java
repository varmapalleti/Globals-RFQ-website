package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.ReviewerReassignmentRequest;

import java.time.Instant;

public record ReviewerReassignmentResponse(Long id, String rfqId, Long requestedByReviewerId,
                                           String requestReason, String status, Long resolvedByAdminId,
                                           String resolutionReason, Long replacementReviewerId,
                                           Instant requestedAt, Instant resolvedAt) {
    public static ReviewerReassignmentResponse from(String rfqPublicId, ReviewerReassignmentRequest request) {
        return new ReviewerReassignmentResponse(request.getId(), rfqPublicId, request.getRequestedByReviewerId(),
                request.getReason(), request.getStatus().name(), request.getResolvedByAdminId(),
                request.getResolutionReason(), request.getReplacementReviewerId(), request.getRequestedAt(), request.getResolvedAt());
    }
}
