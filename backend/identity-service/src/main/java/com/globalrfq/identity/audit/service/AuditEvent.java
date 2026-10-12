package com.globalrfq.identity.audit.service;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.entity.AuditOutcome;

import java.util.Map;

public record AuditEvent(
        AuditEventType eventType,
        AuditOutcome outcome,
        String action,
        Long actorUserId,
        Long targetUserId,
        Map<String, Object> metadata
) {
    public static AuditEvent of(AuditEventType type, AuditOutcome outcome, String action,
                                Long actorUserId, Long targetUserId, Map<String, Object> metadata) {
        return new AuditEvent(type, outcome, action, actorUserId, targetUserId, metadata);
    }
}