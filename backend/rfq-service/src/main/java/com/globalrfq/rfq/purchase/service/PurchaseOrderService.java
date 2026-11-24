package com.globalrfq.rfq.purchase.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.purchase.dto.CreatePurchaseOrderRequest;
import com.globalrfq.rfq.purchase.dto.PurchaseOrderResponse;
import com.globalrfq.rfq.purchase.dto.UpdatePurchaseOrderStatusRequest;
import com.globalrfq.rfq.purchase.entity.PurchaseOrder;
import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import com.globalrfq.rfq.purchase.repository.PurchaseOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class PurchaseOrderService {
    private static final Set<RfqStatus> PURCHASABLE = Set.of(
            RfqStatus.CUSTOMER_APPROVED, RfqStatus.READY_FOR_MARKETING,
            RfqStatus.ASSIGNED_TO_MARKETING
    );

    private final PurchaseOrderRepository purchaseOrders;
    private final RfqRepository rfqs;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrders, RfqRepository rfqs) {
        this.purchaseOrders = purchaseOrders;
        this.rfqs = rfqs;
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request, IdentityPrincipal actor) {
        requirePurchaseTeamOrAdmin(actor);
        Rfq rfq = rfqs.findByPublicId(request.rfqPublicId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        if (!PURCHASABLE.contains(rfq.getStatus())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "Purchase orders can only be created for customer-approved RFQs");
        }
        String orderId = "PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PurchaseOrder order = new PurchaseOrder(
                orderId, rfq.getId(), rfq.getPublicId(), request.quotationVersionId(),
                request.supplierName(), request.assignedTo(), request.orderDate(),
                request.expectedDelivery(), request.notes(), actor.userId()
        );
        purchaseOrders.save(order);
        return PurchaseOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(IdentityPrincipal actor, int page, int size,
                                                     PurchaseOrderStatus status) {
        requirePurchaseTeamOrAdmin(actor);
        var result = purchaseOrders.findFiltered(status, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(PurchaseOrderResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id, IdentityPrincipal actor) {
        requirePurchaseTeamOrAdmin(actor);
        return PurchaseOrderResponse.from(findOrThrow(id));
    }

    @Transactional
    public PurchaseOrderResponse updateStatus(Long id, UpdatePurchaseOrderStatusRequest request,
                                               IdentityPrincipal actor) {
        requirePurchaseTeamOrAdmin(actor);
        PurchaseOrder order = findOrThrow(id);
        order.updateStatus(request.status());
        purchaseOrders.save(order);
        return PurchaseOrderResponse.from(order);
    }

    private PurchaseOrder findOrThrow(Long id) {
        return purchaseOrders.findById(id)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Purchase order was not found"));
    }

    private void requirePurchaseTeamOrAdmin(IdentityPrincipal actor) {
        boolean allowed = actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN")
                || actor.roles().contains("PURCHASE_TEAM") || actor.roles().contains("MARKETING_EXECUTIVE");
        if (!allowed) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only Admin or Purchase team can manage purchase orders");
        }
    }
}
