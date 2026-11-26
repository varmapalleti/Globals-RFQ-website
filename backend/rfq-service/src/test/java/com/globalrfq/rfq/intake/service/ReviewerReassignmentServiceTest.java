package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentCreateRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentResolutionRequest;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.ReviewerReassignmentRequest;
import com.globalrfq.rfq.intake.entity.ReviewerReassignmentStatus;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.repository.ReviewerReassignmentRequestRepository;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewerReassignmentServiceTest {
    @Mock private RfqRepository rfqs;
    @Mock private RfqAssignmentRepository assignments;
    @Mock private ReviewerReassignmentRequestRepository requests;
    @Mock private RfqWorkflowEventRepository events;
    @Mock private RfqAssignmentCoordinator assignmentCoordinator;
    @Mock private InAppNotificationService notifications;

    private ReviewerReassignmentService service;
    private final IdentityPrincipal reviewer = new IdentityPrincipal(5L, "reviewer@example.test", Set.of("REVIEWER"));
    private final IdentityPrincipal admin = new IdentityPrincipal(1L, "admin@example.test", Set.of("ADMIN"));

    @BeforeEach
    void setUp() {
        service = new ReviewerReassignmentService(rfqs, assignments, requests, events,
                assignmentCoordinator, notifications, new ObjectMapper());
    }

    @Test
    void createMovesRfqIntoAwaitingReassignmentAndStoresResumeState() {
        Rfq rfq = rfqWithStatus(RfqStatus.PENDING_REVIEW, reviewer.userId());
        var currentReviewer = mock(com.globalrfq.rfq.intake.entity.RfqAssignment.class);
        when(currentReviewer.getAssignedUserId()).thenReturn(reviewer.userId());
        when(rfqs.findByPublicIdForUpdate("rfq-public-id")).thenReturn(Optional.of(rfq));
        when(assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(10L, AssignmentRole.REVIEWER))
                .thenReturn(Optional.of(currentReviewer));
        when(requests.findByRfqIdAndIdempotencyKey(10L, "request-key")).thenReturn(Optional.empty());
        when(requests.existsByRfqIdAndStatus(10L, ReviewerReassignmentStatus.PENDING)).thenReturn(false);
        when(requests.save(any())).thenAnswer(invocation -> {
            ReviewerReassignmentRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        service.create("rfq-public-id", new ReviewerReassignmentCreateRequest("Need a domain reviewer"),
                "request-key", reviewer);

        assertEquals(RfqStatus.AWAITING_REASSIGNMENT, rfq.getStatus());
        ArgumentCaptor<ReviewerReassignmentRequest> requestCaptor = ArgumentCaptor.forClass(ReviewerReassignmentRequest.class);
        verify(requests).save(requestCaptor.capture());
        assertEquals(RfqStatus.PENDING_REVIEW, requestCaptor.getValue().getRfqStatusBeforeRequest());
        verify(events).save(any());
    }

    @Test
    void decliningRequestRestoresPreviousRfqStatus() {
        Rfq rfq = rfqWithStatus(RfqStatus.AWAITING_REASSIGNMENT, 1L);
        ReviewerReassignmentRequest request = request(rfq, RfqStatus.PENDING_REVIEW);
        when(rfqs.findByPublicIdForUpdate("rfq-public-id")).thenReturn(Optional.of(rfq));
        when(requests.findByIdAndRfqIdForUpdate(22L, 10L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resolve("rfq-public-id", 22L,
                new ReviewerReassignmentResolutionRequest("DECLINE", null, "Current reviewer remains assigned"), admin);

        assertEquals(RfqStatus.PENDING_REVIEW, rfq.getStatus());
        assertEquals("DECLINED", response.status());
        verify(assignmentCoordinator, never()).assign(any(), any(), any());
        verify(events).save(any());
    }

    @Test
    void approvingRequestRestoresPreviousStatusBeforeAssigningReplacementReviewer() {
        Rfq rfq = rfqWithStatus(RfqStatus.AWAITING_REASSIGNMENT, 1L);
        ReviewerReassignmentRequest request = request(rfq, RfqStatus.REJECTED_BY_REVIEWER);
        when(rfqs.findByPublicIdForUpdate("rfq-public-id")).thenReturn(Optional.of(rfq));
        when(requests.findByIdAndRfqIdForUpdate(22L, 10L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertEquals(RfqStatus.REJECTED_BY_REVIEWER, rfq.getStatus());
            return null;
        }).when(assignmentCoordinator).assign(eq("rfq-public-id"), any(), eq(admin));

        var response = service.resolve("rfq-public-id", 22L,
                new ReviewerReassignmentResolutionRequest("APPROVE", 8L, "Assign specialist reviewer"), admin);

        assertEquals(RfqStatus.REJECTED_BY_REVIEWER, rfq.getStatus());
        assertEquals("APPROVED", response.status());
        assertEquals(8L, response.replacementReviewerId());
        verify(assignmentCoordinator).assign(eq("rfq-public-id"), any(), eq(admin));
    }

    private Rfq rfqWithStatus(RfqStatus initialStatus, Long creatorId) {
        AtomicReference<RfqStatus> status = new AtomicReference<>(initialStatus);
        Rfq rfq = mock(Rfq.class);
        when(rfq.getId()).thenReturn(10L);
        when(rfq.getPublicId()).thenReturn("rfq-public-id");
        lenient().when(rfq.getSourceRfqId()).thenReturn("RFQ-100");
        lenient().when(rfq.getCreatedBy()).thenReturn(creatorId);
        when(rfq.getStatus()).thenAnswer(invocation -> status.get());
        doAnswer(invocation -> {
            status.set(invocation.getArgument(0));
            return null;
        }).when(rfq).setStatus(any());
        return rfq;
    }

    private ReviewerReassignmentRequest request(Rfq rfq, RfqStatus previousStatus) {
        ReviewerReassignmentRequest request = new ReviewerReassignmentRequest(
                rfq, reviewer.userId(), "Need a different reviewer", "request-key", "hash", previousStatus);
        ReflectionTestUtils.setField(request, "id", 22L);
        return request;
    }
}
