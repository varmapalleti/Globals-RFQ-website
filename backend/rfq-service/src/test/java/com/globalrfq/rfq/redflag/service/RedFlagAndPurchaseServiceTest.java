package com.globalrfq.rfq.redflag.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.purchase.dto.CreatePurchaseOrderRequest;
import com.globalrfq.rfq.purchase.dto.UpdatePurchaseOrderStatusRequest;
import com.globalrfq.rfq.purchase.entity.PurchaseOrder;
import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import com.globalrfq.rfq.purchase.repository.PurchaseOrderRepository;
import com.globalrfq.rfq.purchase.service.PurchaseOrderService;
import com.globalrfq.rfq.redflag.dto.CreateRedFlagRequest;
import com.globalrfq.rfq.redflag.dto.ResolveRedFlagRequest;
import com.globalrfq.rfq.redflag.entity.RedFlag;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import com.globalrfq.rfq.redflag.repository.RedFlagRepository;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedFlagAndPurchaseServiceTest {

    @Mock private RedFlagRepository redFlags;
    @Mock private PurchaseOrderRepository purchaseOrders;
    @Mock private RfqRepository rfqs;
    @Mock private RfqAssignmentRepository assignments;
    @Mock private RfqWorkflowEventRepository events;
    @Mock private InAppNotificationService notifications;

    private RedFlagService redFlagService;
    private PurchaseOrderService purchaseOrderService;

    private final IdentityPrincipal admin = new IdentityPrincipal(1L, "admin@globalrfq.test", Set.of("ADMIN"));
    private final IdentityPrincipal manager = new IdentityPrincipal(2L, "qm@globalrfq.test", Set.of("QUOTATION_MANAGER"));
    private final IdentityPrincipal viewer = new IdentityPrincipal(3L, "viewer@globalrfq.test", Set.of("VIEWER"));

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        redFlagService = new RedFlagService(redFlags, rfqs, assignments, events, notifications, mapper);
        purchaseOrderService = new PurchaseOrderService(purchaseOrders, rfqs, events, notifications, mapper);
    }

    private Rfq buildRfq(Long id, String publicId, RfqStatus status, Long createdBy) {
        Rfq rfq = mock(Rfq.class, withSettings().lenient());
        when(rfq.getId()).thenReturn(id);
        when(rfq.getPublicId()).thenReturn(publicId);
        when(rfq.getSourceRfqId()).thenReturn("PR-9001");
        when(rfq.getCustomerName()).thenReturn("V2X Systems");
        when(rfq.getStatus()).thenReturn(status);
        when(rfq.getCreatedBy()).thenReturn(createdBy);
        return rfq;
    }

    @Test
    void createRedFlagRejectsUnassignedNonAdmin() {
        Rfq rfq = buildRfq(10L, "pub-10", RfqStatus.QUOTATION_IN_PROGRESS, 1L);
        when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(rfq));
        when(assignments.existsByRfqIdAndAssignedUserIdAndActiveTrue(10L, 2L)).thenReturn(false);

        var req = new CreateRedFlagRequest("pub-10", RedFlagSeverity.HIGH, "Missing specs", null);
        RfqException ex = assertThrows(RfqException.class, () -> redFlagService.create(req, manager));
        assertEquals(RfqErrorCode.ACCESS_DENIED, ex.code());
    }

    @Test
    void createAndResolveRedFlagEmitsNotificationsAndAuditEvent() {
        Rfq rfq = buildRfq(10L, "pub-10", RfqStatus.QUOTATION_IN_PROGRESS, 1L);
        when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(rfq));
        when(rfqs.findById(10L)).thenReturn(Optional.of(rfq));
        when(assignments.existsByRfqIdAndAssignedUserIdAndActiveTrue(10L, 2L)).thenReturn(true);
        when(redFlags.save(any())).thenAnswer(inv -> {
            RedFlag saved = inv.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", 77L);
            return saved;
        });

        var created = redFlagService.create(
                new CreateRedFlagRequest("pub-10", RedFlagSeverity.CRITICAL, "Part discontinued", 5L),
                manager
        );
        assertEquals("OPEN", created.status());
        assertEquals("PR-9001", created.sourceRfqId());
        verify(events, times(1)).save(any());
        verify(notifications).send(eq(5L), eq(NotificationType.RED_FLAG_RAISED), anyString(), anyString(), eq("pub-10"));
        verify(notifications).send(eq(1L), eq(NotificationType.RED_FLAG_RAISED), anyString(), anyString(), eq("pub-10"));

        RedFlag storedFlag = new RedFlag(10L, "pub-10", RedFlagSeverity.CRITICAL, "Part discontinued", 2L, 5L);
        ReflectionTestUtils.setField(storedFlag, "id", 77L);
        when(redFlags.findById(77L)).thenReturn(Optional.of(storedFlag));

        var resolved = redFlagService.resolve(77L, new ResolveRedFlagRequest("RESOLVE", "Replaced with SKU-2"), admin);
        assertEquals("RESOLVED", resolved.status());
        verify(notifications).send(eq(2L), eq(NotificationType.RED_FLAG_RESOLVED), anyString(), eq("Replaced with SKU-2"), eq("pub-10"));
    }

    @Test
    void purchaseOrderRequiresApprovedRfqAndAuthorizedRole() {
        var req = new CreatePurchaseOrderRequest("pub-10", 1L, "DigiKey", 6L, LocalDate.now(), LocalDate.now().plusDays(5), "Rush");

        RfqException denied = assertThrows(RfqException.class, () -> purchaseOrderService.create(req, viewer));
        assertEquals(RfqErrorCode.ACCESS_DENIED, denied.code());

        Rfq nonApproved = buildRfq(10L, "pub-10", RfqStatus.PENDING_REVIEW, 1L);
        when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(nonApproved));
        RfqException invalidState = assertThrows(RfqException.class, () -> purchaseOrderService.create(req, admin));
        assertEquals(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, invalidState.code());
    }

    @Test
    void purchaseOrderCreateAndStatusUpdateSuccess() {
        Rfq approved = buildRfq(10L, "pub-10", RfqStatus.CUSTOMER_APPROVED, 1L);
        when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(approved));
        when(rfqs.findById(10L)).thenReturn(Optional.of(approved));
        when(purchaseOrders.save(any())).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            if (po.getId() == null) ReflectionTestUtils.setField(po, "id", 88L);
            return po;
        });

        var created = purchaseOrderService.create(
                new CreatePurchaseOrderRequest("pub-10", 3L, "Mouser", 9L, LocalDate.now(), LocalDate.now().plusDays(10), "PO note"),
                admin
        );
        assertTrue(created.orderId().startsWith("PO-"));
        assertEquals("PENDING", created.status());
        verify(notifications).send(eq(9L), eq(NotificationType.GENERAL), contains("assigned"), anyString(), eq("pub-10"));

        PurchaseOrder stored = new PurchaseOrder(created.orderId(), 10L, "pub-10", 3L, "Mouser", 9L,
                LocalDate.now(), LocalDate.now().plusDays(10), "PO note", 1L);
        ReflectionTestUtils.setField(stored, "id", 88L);
        when(purchaseOrders.findById(88L)).thenReturn(Optional.of(stored));

        var updated = purchaseOrderService.updateStatus(88L, new UpdatePurchaseOrderStatusRequest(PurchaseOrderStatus.ORDERED), admin);
        assertEquals("ORDERED", updated.status());
        verify(events, times(2)).save(any());
    }
}
