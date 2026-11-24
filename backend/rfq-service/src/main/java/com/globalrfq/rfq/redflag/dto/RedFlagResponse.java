package com.globalrfq.rfq.redflag.dto;

import com.globalrfq.rfq.redflag.entity.RedFlag;

import java.time.Instant;

public record RedFlagResponse(
        Long id,
        Long rfqId,
        String rfqPublicId,
        String severity,
        String reason,
        String status,
        Long raisedBy,
        Long assignedTo,
        String resolutionNote,
        Long resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
    public static RedFlagResponse from(RedFlag flag) {
        return new RedFlagResponse(
                flag.getId(), flag.getRfqId(), flag.getRfqPublicId(),
                flag.getSeverity().name(), flag.getReason(), flag.getStatus().name(),
                flag.getRaisedBy(), flag.getAssignedTo(), flag.getResolutionNote(),
                flag.getResolvedBy(), flag.getResolvedAt(), flag.getCreatedAt()
        );
    }
}
