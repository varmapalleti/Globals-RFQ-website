package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqTimeProperties;
import com.globalrfq.rfq.intake.dto.AssignmentRequest;
import com.globalrfq.rfq.intake.dto.CustomerDecisionRequest;
import com.globalrfq.rfq.intake.dto.CustomerSubmissionRequest;
import com.globalrfq.rfq.intake.dto.MarketingActionRequest;
import com.globalrfq.rfq.intake.entity.*;
import com.globalrfq.rfq.intake.repository.*;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.quotation.entity.QuotationReviewDecision;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.repository.QuotationReviewDecisionRepository;
import com.globalrfq.rfq.quotation.repository.QuotationVersionRepository;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfqCommandServiceTest {

    @Mock private RfqRepository rfqs;
    @Mock private RfqAssignmentRepository assignments;
    @Mock private RfqCustomerSubmissionRepository submissions;
    @Mock private RfqCustomerDecisionRepository decisions;
    @Mock private RfqWorkflowEventRepository events;
    @Mock private QuotationVersionRepository quotationVersions;
    @Mock private QuotationReviewDecisionRepository quotationReviews;
    @Mock private InAppNotificationService notifications;

    private RfqCommandService service;

    private final IdentityPrincipal admin = new IdentityPrincipal(1L, "admin@globalrfq.test", Set.of("ADMIN"));
    private final IdentityPrincipal manager = new IdentityPrincipal(2L, "qm@globalrfq.test", Set.of("QUOTATION_MANAGER"));
    private final IdentityPrincipal marketing = new IdentityPrincipal(4L, "mkt@globalrfq.test", Set.of("MARKETING_EXECUTIVE"));

    @BeforeEach
    void setUp() {
        service = new RfqCommandService(
                rfqs, assignments, submissions, decisions, events,
                quotationVersions, quotationReviews, new RfqTimeProperties(),
                notifications, new ObjectMapper()
        );
    }

    private Rfq buildStatefulRfq(RfqStatus initialStatus, CustomerDecision initialDecision) {
        AtomicReference<RfqStatus> status = new AtomicReference<>(initialStatus);
        AtomicReference<CustomerDecision> decision = new AtomicReference<>(initialDecision);
        Rfq rfq = mock(Rfq.class, withSettings().lenient());
        when(rfq.getId()).thenReturn(10L);
        when(rfq.getPublicId()).thenReturn("pub-10");
        when(rfq.getSourceSystem()).thenReturn("SAP");
        when(rfq.getSourceRfqId()).thenReturn("PR-5001");
        when(rfq.getCustomerName()).thenReturn("Acme Defense");
        when(rfq.getRequestTitle()).thenReturn("V2X Hardware");
        when(rfq.getCreatedBy()).thenReturn(1L);
        when(rfq.getCreatedAt()).thenReturn(Instant.now());
        when(rfq.getLineItems()).thenReturn(List.of());
        when(rfq.getStatus()).thenAnswer(inv -> status.get());
        when(rfq.getCustomerDecision()).thenAnswer(inv -> decision.get());
        doAnswer(inv -> {
            status.set(inv.getArgument(0));
            return null;
        }).when(rfq).setStatus(any());
        doAnswer(inv -> {
            decision.set(inv.getArgument(0));
            return null;
        }).when(rfq).setCustomerDecision(any());
        lenient().when(rfqs.findByPublicIdForUpdate("pub-10")).thenReturn(Optional.of(rfq));
        lenient().when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(rfq));
        return rfq;
    }

    @Test
    void assignQuotationManagerTransitionsImportedRfqAndNotifiesAssignee() {
        Rfq rfq = buildStatefulRfq(RfqStatus.IMPORTED, CustomerDecision.PENDING);
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.QUOTATION_MANAGER))
                .thenReturn(Optional.empty());
        when(assignments.findByRfqIdAndActiveTrueOrderByAssignedAtAsc(10L)).thenReturn(List.of());
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = service.assign(
                "pub-10",
                new AssignmentRequest(AssignmentRole.QUOTATION_MANAGER, 2L, "Initial workload routing"),
                admin
        );

        assertEquals(RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER, res.status());
        verify(events).save(any());
        verify(notifications).send(eq(2L), eq(NotificationType.RFQ_ASSIGNED), anyString(), anyString(), eq("pub-10"));
    }

    @Test
    void managerCannotReassignAnotherQuotationManager() {
        Rfq rfq = buildStatefulRfq(RfqStatus.QUOTATION_IN_PROGRESS, CustomerDecision.PENDING);
        assertNotNull(rfq);
        RfqException ex = assertThrows(RfqException.class, () -> service.assign(
                "pub-10",
                new AssignmentRequest(AssignmentRole.QUOTATION_MANAGER, 9L, "Try self reassign"),
                manager
        ));
        assertEquals(RfqErrorCode.ACCESS_DENIED, ex.code());
    }

    @Test
    void directAssignmentIsBlockedWhileReviewerReassignmentAwaitsAdmin() {
        Rfq rfq = buildStatefulRfq(RfqStatus.AWAITING_REASSIGNMENT, CustomerDecision.PENDING);

        RfqException ex = assertThrows(RfqException.class, () -> service.assign(
                "pub-10",
                new AssignmentRequest(AssignmentRole.REVIEWER, 3L, "Bypass pending request"),
                admin
        ));

        assertEquals(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, ex.code());
        assertEquals(RfqStatus.AWAITING_REASSIGNMENT, rfq.getStatus());
        verify(assignments, never()).save(any());
    }

    @Test
    void customerDecisionAndMarketingLifecycleCompletesToClosed() {
        Rfq rfq = buildStatefulRfq(RfqStatus.APPROVED_BY_REVIEWER, CustomerDecision.PENDING);

        QuotationVersion version = new QuotationVersion(
                rfq, 1, "idem-1", "sha", "USD",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, "{}", "[]", "[]", 2L
        );
        ReflectionTestUtils.setField(version, "id", 100L);
        lenient().when(quotationVersions.findById(100L)).thenReturn(Optional.of(version));
        lenient().when(quotationVersions.findByRfqIdAndId(10L, 100L)).thenReturn(Optional.of(version));
        lenient().when(quotationVersions.findTopByRfqIdOrderByVersionNumberDesc(10L)).thenReturn(Optional.of(version));
        when(quotationReviews.findByQuotationVersionId(100L))
                .thenReturn(Optional.of(new QuotationReviewDecision(version, "APPROVED", 3L, "Looks good")));
        AtomicReference<RfqCustomerSubmission> savedSubmission = new AtomicReference<>();
        lenient().when(submissions.findByRfqIdAndIdempotencyKey(10L, "sub-1")).thenReturn(Optional.empty());
        lenient().when(submissions.save(any())).thenAnswer(inv -> {
            RfqCustomerSubmission sub = inv.getArgument(0);
            savedSubmission.set(sub);
            return sub;
        });
        lenient().when(submissions.findByRfqIdOrderBySentAtDesc(10L))
                .thenAnswer(inv -> savedSubmission.get() == null ? List.of() : List.of(savedSubmission.get()));
        lenient().when(decisions.findByRfqIdAndIdempotencyKey(10L, "dec-1")).thenReturn(Optional.empty());
        AtomicReference<RfqAssignment> savedMarketing = new AtomicReference<>();
        lenient().when(assignments.save(any())).thenAnswer(inv -> {
            RfqAssignment a = inv.getArgument(0);
            if (a.getAssignmentRole() == AssignmentRole.MARKETING_EXECUTIVE && a.isActive()) {
                savedMarketing.set(a);
            }
            return a;
        });
        lenient().when(assignments.findByRfqIdAndActiveTrueOrderByAssignedAtAsc(10L))
                .thenAnswer(inv -> savedMarketing.get() == null ? List.of() : List.of(savedMarketing.get()));
        lenient().when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.MARKETING_EXECUTIVE))
                .thenAnswer(inv -> Optional.ofNullable(savedMarketing.get()));
        lenient().when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.QUOTATION_MANAGER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.QUOTATION_MANAGER, 2L, 1L, "Manager")));
        lenient().when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.REVIEWER))
                .thenReturn(Optional.empty());

        var sent = service.recordCustomerSubmission(
                "pub-10",
                new CustomerSubmissionRequest(100L, "SAP-QT-10", "Sent via ERP"),
                "sub-1",
                admin
        );
        assertEquals(RfqStatus.SENT_TO_CUSTOMER, sent.status());

        var approved = service.recordCustomerDecision(
                "pub-10",
                new CustomerDecisionRequest(CustomerDecision.APPROVED, 100L, "PO issued", "CUST-OK"),
                "dec-1",
                admin
        );
        assertEquals(RfqStatus.CUSTOMER_APPROVED, approved.status());
        assertEquals(CustomerDecision.APPROVED, approved.customerDecision());

        var ready = service.markReadyForMarketing(
                "pub-10",
                new MarketingActionRequest("Ready for onboarding", null),
                admin
        );
        assertEquals(RfqStatus.READY_FOR_MARKETING, ready.status());

        var assignedMkt = service.assign(
                "pub-10",
                new AssignmentRequest(AssignmentRole.MARKETING_EXECUTIVE, 4L, "Assign marketing owner"),
                admin
        );
        assertEquals(RfqStatus.ASSIGNED_TO_MARKETING, assignedMkt.status());

        var closed = service.completeMarketing(
                "pub-10",
                new MarketingActionRequest("Handed off", "CAMPAIGN_LAUNCHED"),
                marketing
        );
        assertEquals(RfqStatus.CLOSED, closed.status());
    }
}
