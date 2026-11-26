package com.globalrfq.rfq.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "rfq_id", nullable = false)
    private Long rfqId;

    @Column(name = "rfq_public_id", nullable = false, length = 36)
    private String rfqPublicId;

    @Column(name = "quotation_version_id")
    private Long quotationVersionId;

    @Column(name = "supplier_name", length = 190)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseOrderStatus status = PurchaseOrderStatus.PENDING;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_delivery")
    private LocalDate expectedDelivery;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PurchaseOrder() { }

    public PurchaseOrder(String orderId, Long rfqId, String rfqPublicId, Long quotationVersionId,
                         String supplierName, Long assignedTo, LocalDate orderDate,
                         LocalDate expectedDelivery, String notes, Long createdBy) {
        this.orderId = orderId;
        this.rfqId = rfqId;
        this.rfqPublicId = rfqPublicId;
        this.quotationVersionId = quotationVersionId;
        this.supplierName = supplierName;
        this.assignedTo = assignedTo;
        this.orderDate = orderDate;
        this.expectedDelivery = expectedDelivery;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void updateStatus(PurchaseOrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public Long getRfqId() { return rfqId; }
    public String getRfqPublicId() { return rfqPublicId; }
    public Long getQuotationVersionId() { return quotationVersionId; }
    public String getSupplierName() { return supplierName; }
    public PurchaseOrderStatus getStatus() { return status; }
    public Long getAssignedTo() { return assignedTo; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getExpectedDelivery() { return expectedDelivery; }
    public String getNotes() { return notes; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
