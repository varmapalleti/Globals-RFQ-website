package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.CustomerDecision;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RfqSummaryResponse(
        String id,
        String sourceRfqId,
        String customerName,
        String requestTitle,
        RfqStatus status,
        CustomerDecision customerDecision,
        LocalDate requestedDeliveryDate,
        Instant createdAt,
        String sourceReference,
        LocalDateTime dueAtLocal,
        long lineItemCount,
        Long assigneeUserId,
        String assigneeRole
) {
    public static RfqSummaryResponse from(Rfq rfq) {
        return from(rfq, 0L, null, null);
    }

    public static RfqSummaryResponse from(Rfq rfq, long lineItemCount, Long assigneeUserId, String assigneeRole) {
        return new RfqSummaryResponse(
                rfq.getPublicId(),
                rfq.getSourceRfqId(),
                rfq.getCustomerName(),
                rfq.getRequestTitle(),
                rfq.getStatus(),
                rfq.getCustomerDecision(),
                rfq.getRequestedDeliveryDate(),
                rfq.getCreatedAt(),
                rfq.getSourceReference(),
                rfq.getDueAtLocal(),
                lineItemCount,
                assigneeUserId,
                assigneeRole
        );
    }
}
