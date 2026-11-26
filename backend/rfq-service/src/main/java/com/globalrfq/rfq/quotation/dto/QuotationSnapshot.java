package com.globalrfq.rfq.quotation.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuotationSnapshot(
        String currency,
        List<QuotedLineSnapshot> items,
        BigDecimal freight,
        BigDecimal otherCharges,
        BigDecimal discount,
        BigDecimal taxAmount,
        BigDecimal subtotal,
        BigDecimal grandTotal,
        String deliveryTimeline,
        String warranty,
        String notes
) {
    public record QuotedLineSnapshot(
            String sourceLineId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String unitOfMeasure,
            String manufacturer,
            String partNumber,
            Boolean taaCompliant,
            String deliveryTimeline,
            String warranty,
            String note,
            String productUrl
    ) { }
}
