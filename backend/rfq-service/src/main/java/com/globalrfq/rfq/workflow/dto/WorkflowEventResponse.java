package com.globalrfq.rfq.workflow.dto;

import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.intake.entity.RfqStatus;

import java.time.Instant;

public record WorkflowEventResponse(Long id, String eventType, Long actorUserId, String actorRolesJson,
                                    RfqStatus fromStatus, RfqStatus toStatus, String reason,
                                    String payloadJson, String correlationId, Instant occurredAt) {
    public static WorkflowEventResponse from(RfqWorkflowEvent event) {
        return new WorkflowEventResponse(event.getId(), event.getEventType(), event.getActorUserId(), event.getActorRolesJson(),
                event.getFromStatus(), event.getToStatus(), event.getReason(), event.getPayloadJson(),
                event.getCorrelationId(), event.getOccurredAt());
    }
}
