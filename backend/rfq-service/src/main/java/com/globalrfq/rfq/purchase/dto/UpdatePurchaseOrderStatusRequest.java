package com.globalrfq.rfq.purchase.dto;

import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePurchaseOrderStatusRequest(
        @NotNull PurchaseOrderStatus status
) { }
