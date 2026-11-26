package com.globalrfq.rfq.quotation.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.*;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqLineItemRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.quotation.dto.QuotationDraftRequest;
import com.globalrfq.rfq.quotation.dto.ReviewRequest;
import com.globalrfq.rfq.quotation.entity.MandatoryFieldConfig;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.entity.WorkflowPolicy;
import com.globalrfq.rfq.quotation.repository.*;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationWorkflowServiceTest {

    @Mock private RfqRepository rfqs;
    @Mock private RfqLineItemRepository lineItems;
    @Mock private RfqAssignmentRepository assignments;
    @Mock private QuotationDraftRepository drafts;
    @Mock private QuotationVersionRepository versions;
    @Mock private QuotationReviewDecisionRepository reviews;
    @Mock private MandatoryFieldConfigRepository mandatoryFields;
    @Mock private WorkflowPolicyRepository policies;
    @Mock private RfqWorkflowEventRepository events;
    @Mock private RfqAdminAuditEventRepository adminAudit;
    @Mock private InAppNotificationService notifications;

    private QuotationWorkflowService service;

    private final IdentityPrincipal manager = new IdentityPrincipal(2L, "qm@globalrfq.test", Set.of("QUOTATION_MANAGER"));
    private final IdentityPrincipal otherManager = new IdentityPrincipal(22L, "other@globalrfq.test", Set.of("QUOTATION_MANAGER"));
    private final IdentityPrincipal reviewer = new IdentityPrincipal(3L, "rev@globalrfq.test", Set.of("REVIEWER"));

    @BeforeEach
    void setUp() {
        service = new QuotationWorkflowService(
                rfqs, lineItems, assignments, drafts, versions, reviews,
                mandatoryFields, policies, events, adminAudit, notifications, new ObjectMapper()
        );
    }

    private Rfq buildRfq(RfqStatus initialStatus) {
        AtomicReference<RfqStatus> status = new AtomicReference<>(initialStatus);
        Rfq rfq = mock(Rfq.class, withSettings().lenient());
        when(rfq.getId()).thenReturn(10L);
        when(rfq.getPublicId()).thenReturn("pub-10");
        when(rfq.getSourceRfqId()).thenReturn("PR-7701");
        when(rfq.getCreatedBy()).thenReturn(1L);
        when(rfq.getStatus()).thenAnswer(inv -> status.get());
        doAnswer(inv -> {
            status.set(inv.getArgument(0));
            return null;
        }).when(rfq).setStatus(any());

        RfqLineItem item = mock(RfqLineItem.class, withSettings().lenient());
        when(item.getSourceLineId()).thenReturn("LINE-1");
        when(item.getDescription()).thenReturn("Optical Transceiver");
        when(item.getRequestedQuantity()).thenReturn(new BigDecimal("5"));
        when(item.getUnitOfMeasure()).thenReturn("EA");
        when(rfq.getLineItems()).thenReturn(List.of(item));

        lenient().when(lineItems.findByRfqIdOrderByLineNumberAsc(10L)).thenReturn(List.of(item));
        lenient().when(rfqs.findByPublicIdForUpdate("pub-10")).thenReturn(Optional.of(rfq));
        lenient().when(rfqs.findByPublicId("pub-10")).thenReturn(Optional.of(rfq));
        return rfq;
    }

    private QuotationDraftRequest buildDraftRequest(BigDecimal unitPrice, String partNumber) {
        return new QuotationDraftRequest(
                "USD",
                List.of(new QuotationDraftRequest.QuotedLineRequest(
                        "LINE-1", "Optical Transceiver", new BigDecimal("5"), unitPrice,
                        "EA", "Cisco", partNumber, true, "14 days", "1 year", "In stock", "https://example.com"
                )),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "14 days", "1 year", "Draft note"
        );
    }

    @Test
    void saveDraftDeniesUnassignedManagerAndAllowsAssignedManager() {
        Rfq rfq = buildRfq(RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER);
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.QUOTATION_MANAGER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.QUOTATION_MANAGER, 2L, 1L, "Assigned")));

        var req = buildDraftRequest(new BigDecimal("120.00"), "SFP-10G");

        RfqException denied = assertThrows(RfqException.class, () -> service.saveDraft("pub-10", req, otherManager));
        assertEquals(RfqErrorCode.ACCESS_DENIED, denied.code());

        var draftRes = service.saveDraft("pub-10", req, manager);
        assertEquals(RfqStatus.QUOTATION_IN_PROGRESS, rfq.getStatus());
        assertNotNull(draftRes);
        verify(drafts).save(any());
    }

    @Test
    void submitAllowsMissingMandatoryFieldsButReviewerCannotApproveIncompleteVersion() {
        Rfq rfq = buildRfq(RfqStatus.QUOTATION_IN_PROGRESS);
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.QUOTATION_MANAGER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.QUOTATION_MANAGER, 2L, 1L, "Assigned")));
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.REVIEWER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.REVIEWER, 3L, 1L, "Reviewer")));
        when(mandatoryFields.findAllByRequiredTrueOrderByFieldKeyAsc()).thenReturn(List.of(
                new MandatoryFieldConfig("items.partNumber", true, 1L, "Required"),
                new MandatoryFieldConfig("items.unitPrice", true, 1L, "Required")
        ));
        when(versions.findByRfqIdAndIdempotencyKey(10L, "sub-1")).thenReturn(Optional.empty());
        when(versions.findTopByRfqIdOrderByVersionNumberDesc(10L)).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(inv -> {
            QuotationVersion v = inv.getArgument(0);
            if (v.getId() == null) ReflectionTestUtils.setField(v, "id", 55L);
            return v;
        });

        // Submit with missing partNumber (blank) - system accepts submission per business rules
        var submitted = service.submit("pub-10", "sub-1", buildDraftRequest(new BigDecimal("120.00"), ""), manager);
        assertEquals(1, submitted.versionNumber());
        assertFalse(submitted.missingMandatoryFields().isEmpty());
        assertEquals(RfqStatus.PENDING_REVIEW, rfq.getStatus());
        verify(notifications).send(eq(3L), eq(NotificationType.QUOTATION_SUBMITTED), anyString(), anyString(), eq("pub-10"));

        // Now reviewer attempts to APPROVE version that has missing mandatory fields
        QuotationVersion incomplete = new QuotationVersion(
                rfq, 1, "sub-1", "sha", "USD", new BigDecimal("600.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600.00"),
                "{}", "[\"partNumber\"]", "[\"partNumber\"]", 2L
        );
        ReflectionTestUtils.setField(incomplete, "id", 55L);
        when(versions.findByRfqIdAndId(10L, 55L)).thenReturn(Optional.of(incomplete));
        when(reviews.findByQuotationVersionId(55L)).thenReturn(Optional.empty());

        RfqException blocked = assertThrows(RfqException.class,
                () -> service.review("pub-10", 55L, new ReviewRequest("APPROVED", null), reviewer));
        assertEquals(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, blocked.code());
    }

    @Test
    void reviewRejectTransitionsToRejectedThenEscalatesAtPolicyThreshold() {
        Rfq rfq = buildRfq(RfqStatus.PENDING_REVIEW);
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.REVIEWER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.REVIEWER, 3L, 1L, "Reviewer")));
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.QUOTATION_MANAGER))
                .thenReturn(Optional.of(new RfqAssignment(rfq, AssignmentRole.QUOTATION_MANAGER, 2L, 1L, "Manager")));
        QuotationVersion v1 = new QuotationVersion(
                rfq, 1, "sub-1", "sha", "USD", BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                "{}", "[]", "[]", 2L
        );
        ReflectionTestUtils.setField(v1, "id", 11L);
        when(versions.findByRfqIdAndId(10L, 11L)).thenReturn(Optional.of(v1));
        when(reviews.findByQuotationVersionId(11L)).thenReturn(Optional.empty());

        WorkflowPolicy policy = mock(WorkflowPolicy.class);
        when(policy.getMaxFormalRejections()).thenReturn(2);
        when(policies.findById((byte) 1)).thenReturn(Optional.of(policy));

        // 1st rejection -> REJECTED_BY_REVIEWER
        when(reviews.countByQuotationVersionRfqIdAndDecision(10L, "REJECTED")).thenReturn(1L);
        service.review("pub-10", 11L, new ReviewRequest("REJECTED", "Unit price missing freight check"), reviewer);
        assertEquals(RfqStatus.REJECTED_BY_REVIEWER, rfq.getStatus());
        verify(notifications).send(eq(2L), eq(NotificationType.QUOTATION_REJECTED), anyString(), anyString(), eq("pub-10"));
        verify(notifications).send(eq(1L), eq(NotificationType.QUOTATION_REJECTED), anyString(),
                org.mockito.ArgumentMatchers.contains("Unit price missing freight check"), eq("pub-10"));

        // 2nd rejection hitting maxFormalRejections = 2 -> ESCALATED
        rfq.setStatus(RfqStatus.PENDING_REVIEW);
        QuotationVersion v2 = new QuotationVersion(
                rfq, 2, "sub-2", "sha", "USD", BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                "{}", "[]", "[]", 2L
        );
        ReflectionTestUtils.setField(v2, "id", 12L);
        when(versions.findByRfqIdAndId(10L, 12L)).thenReturn(Optional.of(v2));
        when(reviews.findByQuotationVersionId(12L)).thenReturn(Optional.empty());
        when(reviews.countByQuotationVersionRfqIdAndDecision(10L, "REJECTED")).thenReturn(2L);

        service.review("pub-10", 12L, new ReviewRequest("REJECTED", "Still wrong margin"), reviewer);
        assertEquals(RfqStatus.ESCALATED, rfq.getStatus());
        verify(notifications).send(eq(1L), eq(NotificationType.QUOTATION_ESCALATED), anyString(), anyString(), eq("pub-10"));
    }
}
