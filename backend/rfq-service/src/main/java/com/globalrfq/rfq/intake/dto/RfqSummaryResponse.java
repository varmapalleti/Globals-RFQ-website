package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.CustomerDecision;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;

public record RfqSummaryResponse(String id, String sourceRfqId, String customerName, String requestTitle,
                                 RfqStatus status, CustomerDecision customerDecision,
                                 LocalDate requestedDeliveryDate, Instant createdAt) {
    public static RfqSummaryResponse from(Rfq rfq) {
        return new RfqSummaryResponse(rfq.getPublicId(), rfq.getSourceRfqId(), rfq.getCustomerName(),
                rfq.getRequestTitle(), rfq.getStatus(), rfq.getCustomerDecision(),
                rfq.getRequestedDeliveryDate(), rfq.getCreatedAt());
    }
}
