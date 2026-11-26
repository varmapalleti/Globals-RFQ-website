package com.globalrfq.rfq.purchase.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.purchase.dto.CreatePurchaseOrderRequest;
import com.globalrfq.rfq.purchase.dto.PurchaseOrderResponse;
import com.globalrfq.rfq.purchase.dto.UpdatePurchaseOrderStatusRequest;
import com.globalrfq.rfq.purchase.entity.PurchaseOrder;
import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import com.globalrfq.rfq.purchase.repository.PurchaseOrderRepository;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {
    private static final Set<RfqStatus> PURCHASABLE = Set.of(
            RfqStatus.CUSTOMER_APPROVED, RfqStatus.READY_FOR_MARKETING,
            RfqStatus.ASSIGNED_TO_MARKETING, RfqStatus.CLOSED
    );

    private final PurchaseOrderRepository purchaseOrders;
    private final RfqRepository rfqs;
    private final RfqWorkflowEventRepository events;
    private final InAppNotificationService notifications;
    private final ObjectMapper mapper;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrders, RfqRepository rfqs,
                                RfqWorkflowEventRepository events,
                                InAppNotificationService notifications,
                                ObjectMapper mapper) {
        this.purchaseOrders = purchaseOrders;
        this.rfqs = rfqs;
        this.events = events;
        this.notifications = notifications;
        this.mapper = mapper;
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
        appendEvent(rfq, "PURCHASE_ORDER_CREATED", actor, request.notes(),
                Map.of("orderId", orderId, "supplierName", request.supplierName() == null ? "" : request.supplierName()));
        if (request.assignedTo() != null && !Objects.equals(request.assignedTo(), actor.userId())) {
            notifications.send(request.assignedTo(), NotificationType.GENERAL,
                    "Purchase Order " + orderId + " assigned",
                    "Purchase order " + orderId + " for RFQ-" + rfq.getSourceRfqId() + " was assigned to you.",
                    rfq.getPublicId());
        }
        return PurchaseOrderResponse.from(order, rfq.getSourceRfqId(), rfq.getCustomerName());
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(IdentityPrincipal actor, int page, int size,
                                                     PurchaseOrderStatus status) {
        requirePurchaseTeamOrAdmin(actor);
        var result = purchaseOrders.findFiltered(status, PageRequest.of(page, size));
        Map<Long, Rfq> rfqMap = rfqs.findAllById(
                result.getContent().stream().map(PurchaseOrder::getRfqId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Rfq::getId, Function.identity()));
        return new PageResponse<>(
                result.getContent().stream().map(order -> {
                    Rfq rfq = rfqMap.get(order.getRfqId());
                    return PurchaseOrderResponse.from(order,
                            rfq == null ? null : rfq.getSourceRfqId(),
                            rfq == null ? null : rfq.getCustomerName());
                }).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id, IdentityPrincipal actor) {
        requirePurchaseTeamOrAdmin(actor);
        PurchaseOrder order = findOrThrow(id);
        Rfq rfq = rfqs.findById(order.getRfqId()).orElse(null);
        return PurchaseOrderResponse.from(order,
                rfq == null ? null : rfq.getSourceRfqId(),
                rfq == null ? null : rfq.getCustomerName());
    }

    @Transactional
    public PurchaseOrderResponse updateStatus(Long id, UpdatePurchaseOrderStatusRequest request,
                                               IdentityPrincipal actor) {
        requirePurchaseTeamOrAdmin(actor);
        PurchaseOrder order = findOrThrow(id);
        PurchaseOrderStatus previous = order.getStatus();
        order.updateStatus(request.status());
        purchaseOrders.save(order);
        Rfq rfq = rfqs.findById(order.getRfqId()).orElse(null);
        if (rfq != null) {
            appendEvent(rfq, "PURCHASE_ORDER_STATUS_UPDATED", actor, null,
                    Map.of("orderId", order.getOrderId(), "from", previous.name(), "to", request.status().name()));
        }
        String rfqLabel = rfq == null ? order.getRfqPublicId() : "RFQ-" + rfq.getSourceRfqId();
        if (order.getCreatedBy() != null && !Objects.equals(order.getCreatedBy(), actor.userId())) {
            notifications.send(order.getCreatedBy(), NotificationType.GENERAL,
                    "Purchase Order " + order.getOrderId() + " updated",
                    "Status for " + order.getOrderId() + " (" + rfqLabel + ") changed to " + request.status().name() + ".",
                    order.getRfqPublicId());
        }
        if (order.getAssignedTo() != null
                && !Objects.equals(order.getAssignedTo(), actor.userId())
                && !Objects.equals(order.getAssignedTo(), order.getCreatedBy())) {
            notifications.send(order.getAssignedTo(), NotificationType.GENERAL,
                    "Purchase Order " + order.getOrderId() + " updated",
                    "Status for " + order.getOrderId() + " (" + rfqLabel + ") changed to " + request.status().name() + ".",
                    order.getRfqPublicId());
        }
        return PurchaseOrderResponse.from(order,
                rfq == null ? null : rfq.getSourceRfqId(),
                rfq == null ? null : rfq.getCustomerName());
    }

    private PurchaseOrder findOrThrow(Long id) {
        return purchaseOrders.findById(id)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Purchase order was not found"));
    }

    private void requirePurchaseTeamOrAdmin(IdentityPrincipal actor) {
        boolean allowed = actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN")
                || actor.roles().contains("PURCHASE_TEAM") || actor.roles().contains("MARKETING_EXECUTIVE");
        if (!allowed) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only Admin or Purchase/Marketing team can manage purchase orders");
        }
    }

    private void appendEvent(Rfq rfq, String eventType, IdentityPrincipal actor, String reason, Map<String, ?> payload) {
        try {
            events.save(new RfqWorkflowEvent(rfq, eventType, actor.userId(),
                    mapper.writeValueAsString(actor.roles().stream().sorted().toList()),
                    null, null, reason, mapper.writeValueAsString(payload), MDC.get("correlationId")));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "Purchase order audit event could not be recorded");
        }
    }
}
