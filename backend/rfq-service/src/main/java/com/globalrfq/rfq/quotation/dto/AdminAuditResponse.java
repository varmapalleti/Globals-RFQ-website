package com.globalrfq.rfq.quotation.dto;

import com.globalrfq.rfq.quotation.entity.RfqAdminAuditEvent;

import java.time.Instant;

public record AdminAuditResponse(Long id, String actionType, Long actorUserId, String reason,
                                 String beforeJson, String afterJson, String correlationId, Instant occurredAt) {
    public static AdminAuditResponse from(RfqAdminAuditEvent event) {
        return new AdminAuditResponse(event.getId(), event.getActionType(), event.getActorUserId(), event.getReason(),
                event.getBeforeJson(), event.getAfterJson(), event.getCorrelationId(), event.getOccurredAt());
    }
}
