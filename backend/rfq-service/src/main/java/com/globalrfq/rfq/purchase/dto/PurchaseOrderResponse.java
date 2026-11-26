package com.globalrfq.rfq.purchase.dto;

import com.globalrfq.rfq.purchase.entity.PurchaseOrder;

import java.time.Instant;
import java.time.LocalDate;

public record PurchaseOrderResponse(
        Long id,
        String orderId,
        Long rfqId,
        String rfqPublicId,
        String sourceRfqId,
        String customerName,
        Long quotationVersionId,
        String supplierName,
        String status,
        Long assignedTo,
        LocalDate orderDate,
        LocalDate expectedDelivery,
        String notes,
        Long createdBy,
        Instant createdAt
) {
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        return from(order, null, null);
    }

    public static PurchaseOrderResponse from(PurchaseOrder order, String sourceRfqId, String customerName) {
        return new PurchaseOrderResponse(
                order.getId(), order.getOrderId(), order.getRfqId(), order.getRfqPublicId(),
                sourceRfqId, customerName,
                order.getQuotationVersionId(), order.getSupplierName(), order.getStatus().name(),
                order.getAssignedTo(), order.getOrderDate(), order.getExpectedDelivery(),
                order.getNotes(), order.getCreatedBy(), order.getCreatedAt()
        );
    }
}
