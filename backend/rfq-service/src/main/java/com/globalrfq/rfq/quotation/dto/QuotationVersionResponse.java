package com.globalrfq.rfq.quotation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuotationVersionResponse(
        Long id,
        int versionNumber,
        String status,
        String currency,
        BigDecimal subtotal,
        BigDecimal freight,
        BigDecimal otherCharges,
        BigDecimal discount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        QuotationSnapshot quotation,
        List<String> mandatoryFieldsSnapshot,
        List<String> missingMandatoryFields,
        Long submittedByUserId,
        Instant submittedAt,
        ReviewResponse review
) {
    public record ReviewResponse(String decision, Long reviewerUserId, String reason, Instant decidedAt) { }
}
