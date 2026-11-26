package com.globalrfq.rfq.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePurchaseOrderRequest(
        @NotBlank @Size(max = 36) String rfqPublicId,
        Long quotationVersionId,
        @Size(max = 190) String supplierName,
        Long assignedTo,
        LocalDate orderDate,
        LocalDate expectedDelivery,
        @Size(max = 2000) String notes
) { }
