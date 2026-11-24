package com.globalrfq.rfq.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuotationDraftRequest(
        @Pattern(regexp = "^$|[A-Z]{3}", message = "Use an uppercase ISO 4217 currency code") String currency,
        @NotEmpty @Size(max = 500) List<@Valid QuotedLineRequest> items,
        @DecimalMin("0.00") BigDecimal freight,
        @DecimalMin("0.00") BigDecimal otherCharges,
        @DecimalMin("0.00") BigDecimal discount,
        @DecimalMin("0.00") BigDecimal taxAmount,
        @Size(max = 500) String deliveryTimeline,
        @Size(max = 500) String warranty,
        @Size(max = 10000) String notes
) {
    public record QuotedLineRequest(
            @NotBlank @Size(max = 120) String sourceLineId,
            @Size(max = 20000) String description,
            @DecimalMin(value = "0.0001") BigDecimal quantity,
            @DecimalMin(value = "0.00") BigDecimal unitPrice,
            @Size(max = 40) String unitOfMeasure,
            @Size(max = 255) String manufacturer,
            @Size(max = 255) String partNumber,
            Boolean taaCompliant,
            @Size(max = 500) String deliveryTimeline,
            @Size(max = 500) String warranty,
            @Size(max = 4000) String note,
            @Size(max = 1000) String productUrl
    ) { }
}
