package com.globalrfq.rfq.intake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CreateRfqRequest(
        @NotBlank @Size(max = 80) String sourceSystem,
        @NotBlank @Size(max = 120) String sourceRfqId,
        @Size(max = 255) String sourceReference,
        @Size(max = 190) String sourceQuotationManagerName,
        @NotBlank @Size(max = 190) String customerName,
        @Size(max = 190) String customerCompany,
        @Size(max = 150) String buyerName,
        @Email @Size(max = 190) String buyerEmail,
        @Size(max = 40) String buyerPhone,
        @Size(max = 255) String requestTitle,
        LocalDateTime dueAtLocal,
        @Size(max = 80) String dueTimeZone,
        LocalDate requestedDeliveryDate,
        @Size(max = 20000) String shipToText,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 100000) String termsAndConditions,
        Map<String, Object> sourceData,
        @NotEmpty @Size(max = 500) List<@Valid CreateRfqLineItemRequest> lineItems
) {
    public record CreateRfqLineItemRequest(
            @NotBlank @Size(max = 120) String sourceLineId,
            @Positive int lineNumber,
            @NotBlank @Size(max = 20000) String description,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal requestedQuantity,
            @Size(max = 40) String unitOfMeasure,
            @Size(max = 255) String customerPartNumber,
            @Size(max = 255) String requestedManufacturer,
            @Size(max = 255) String requestedPartNumber,
            @Size(max = 120) String procurementCategory,
            Boolean taaRequired,
            @Size(max = 100000) String technicalSpecifications,
            Map<String, Object> sourceData
    ) { }
}
