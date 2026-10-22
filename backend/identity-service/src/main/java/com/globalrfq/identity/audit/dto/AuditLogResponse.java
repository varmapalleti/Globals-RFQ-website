package com.globalrfq.identity.audit.dto;

import com.globalrfq.identity.audit.entity.SecurityAuditLog;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(Long id, String eventType, Long actorUserId, Long targetUserId,
                               String action, String outcome, String ipAddress, String userAgent,
                               String correlationId, Map<String, Object> metadata, Instant createdAt) {
    public static AuditLogResponse from(SecurityAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEventType().name(), log.getActorUserId(),
                log.getTargetUserId(), log.getAction(), log.getOutcome().name(), log.getIpAddress(),
                log.getUserAgent(), log.getCorrelationId(), log.getMetadata(), log.getCreatedAt());
    }
}
