package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.AssignmentRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentCreateRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentResolutionRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentResponse;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.ReviewerReassignmentRequest;
import com.globalrfq.rfq.intake.entity.ReviewerReassignmentStatus;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.repository.ReviewerReassignmentRequestRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ReviewerReassignmentService {
    private final RfqRepository rfqs;
    private final RfqAssignmentRepository assignments;
    private final ReviewerReassignmentRequestRepository requests;
    private final RfqWorkflowEventRepository events;
    private final RfqAssignmentCoordinator assignmentCoordinator;
    private final InAppNotificationService notifications;
    private final ObjectMapper objectMapper;

    public ReviewerReassignmentService(RfqRepository rfqs, RfqAssignmentRepository assignments,
                                       ReviewerReassignmentRequestRepository requests,
                                       RfqWorkflowEventRepository events,
                                       RfqAssignmentCoordinator assignmentCoordinator,
                                       InAppNotificationService notifications,
                                       ObjectMapper objectMapper) {
        this.rfqs = rfqs;
        this.assignments = assignments;
        this.requests = requests;
        this.events = events;
        this.assignmentCoordinator = assignmentCoordinator;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewerReassignmentResponse create(String rfqPublicId, ReviewerReassignmentCreateRequest request,
                                               String idempotencyKey, IdentityPrincipal actor) {
        requireRole(actor, "REVIEWER");
        Rfq rfq = lockRfq(rfqPublicId);
        String reason = request.reason().trim();
        String requestHash = sha256(reason);
        var previousRequest = requests.findByRfqIdAndIdempotencyKey(rfq.getId(), idempotencyKey);
        if (previousRequest.isPresent()) {
            if (!previousRequest.get().getRequestedByReviewerId().equals(actor.userId())) {
                throw new RfqException(RfqErrorCode.ACCESS_DENIED, "This idempotent request belongs to a different Reviewer");
            }
            if (!previousRequest.get().getRequestSha256().equals(requestHash)) {
                throw new RfqException(RfqErrorCode.DUPLICATE_RESOURCE, "Idempotency-Key was already used for a different reviewer reassignment request");
            }
            return ReviewerReassignmentResponse.from(rfq.getPublicId(), previousRequest.get());
        }
        boolean assignedReviewer = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.REVIEWER)
                .map(assignment -> assignment.getAssignedUserId().equals(actor.userId())).orElse(false);
        if (!assignedReviewer) throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only the currently assigned Reviewer can request reassignment");
        if (!Set.of(RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER, RfqStatus.QUOTATION_IN_PROGRESS, RfqStatus.PENDING_REVIEW,
                RfqStatus.REJECTED_BY_REVIEWER, RfqStatus.REVISION_IN_PROGRESS, RfqStatus.REOPENED_FOR_REVISION, RfqStatus.ESCALATED).contains(rfq.getStatus())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Reviewer reassignment can only be requested during the quotation and review workflow");
        }
        if (requests.existsByRfqIdAndStatus(rfq.getId(), ReviewerReassignmentStatus.PENDING)) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "A reviewer reassignment request is already awaiting Admin action");
        }
        RfqStatus previousStatus = rfq.getStatus();
        ReviewerReassignmentRequest saved = requests.save(new ReviewerReassignmentRequest(rfq, actor.userId(), reason,
                idempotencyKey, requestHash, previousStatus));
        rfq.setStatus(RfqStatus.AWAITING_REASSIGNMENT);
        appendEvent(rfq, "REVIEWER_REASSIGNMENT_REQUESTED", actor, previousStatus,
                RfqStatus.AWAITING_REASSIGNMENT, reason,
                Map.of("requestId", saved.getId(), "reviewerUserId", actor.userId()));
        if (rfq.getCreatedBy() != null && !rfq.getCreatedBy().equals(actor.userId())) {
            notifications.send(rfq.getCreatedBy(), NotificationType.REVIEWER_REASSIGNMENT_REQUESTED,
                    "Reviewer reassignment requested on RFQ-" + rfq.getSourceRfqId(),
                    reason, rfq.getPublicId());
        }
        return ReviewerReassignmentResponse.from(rfq.getPublicId(), saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewerReassignmentResponse> list(String rfqPublicId, IdentityPrincipal actor) {
        Rfq rfq = rfqs.findByPublicId(rfqPublicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        boolean admin = hasAnyRole(actor, "ADMIN", "SUPER_ADMIN");
        List<ReviewerReassignmentRequest> result;
        if (admin) {
            result = requests.findByRfqIdOrderByRequestedAtAsc(rfq.getId());
        } else if (hasAnyRole(actor, "REVIEWER")
                && requests.existsByRfqIdAndRequestedByReviewerId(rfq.getId(), actor.userId())) {
            result = requests.findByRfqIdAndRequestedByReviewerIdOrderByRequestedAtAsc(rfq.getId(), actor.userId());
        } else {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin or the requesting Reviewer can view reassignment requests");
        }
        return result.stream().map(request -> ReviewerReassignmentResponse.from(rfq.getPublicId(), request)).toList();
    }

    @Transactional
    public ReviewerReassignmentResponse resolve(String rfqPublicId, Long requestId,
                                                ReviewerReassignmentResolutionRequest resolution,
                                                IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(rfqPublicId);
        ReviewerReassignmentRequest request = requests.findByIdAndRfqIdForUpdate(requestId, rfq.getId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Reviewer reassignment request was not found"));
        if (request.getStatus() != ReviewerReassignmentStatus.PENDING) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "This reviewer reassignment request has already been resolved");
        }

        Long replacementReviewerId = null;
        ReviewerReassignmentStatus status;
        RfqStatus previousStatus = request.getRfqStatusBeforeRequest();
        if (previousStatus == null && rfq.getStatus() != RfqStatus.AWAITING_REASSIGNMENT) {
            // Compatibility for pending requests created before V16 captured the prior state.
            previousStatus = rfq.getStatus();
        }
        if (previousStatus == null || previousStatus == RfqStatus.AWAITING_REASSIGNMENT) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "The RFQ workflow state before the reassignment request is unavailable");
        }
        RfqStatus statusBeforeResolution = rfq.getStatus();
        rfq.setStatus(previousStatus);
        if ("APPROVE".equals(resolution.decision())) {
            if (resolution.replacementReviewerId() == null) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "replacementReviewerId is required when approving a reassignment request");
            }
            if (resolution.replacementReviewerId().equals(request.getRequestedByReviewerId())) {
                throw new RfqException(RfqErrorCode.BAD_REQUEST, "The replacement Reviewer must be a different user");
            }
            assignmentCoordinator.assign(rfqPublicId, new AssignmentRequest(AssignmentRole.REVIEWER,
                    resolution.replacementReviewerId(), resolution.reason()), actor);
            replacementReviewerId = resolution.replacementReviewerId();
            status = ReviewerReassignmentStatus.APPROVED;
        } else {
            if (resolution.replacementReviewerId() != null) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "replacementReviewerId must be omitted when declining a reassignment request");
            }
            status = ReviewerReassignmentStatus.DECLINED;
        }
        request.resolve(status, actor.userId(), resolution.reason().trim(), replacementReviewerId);
        requests.save(request);
        appendEvent(rfq, "REVIEWER_REASSIGNMENT_RESOLVED", actor, statusBeforeResolution, rfq.getStatus(),
                resolution.reason().trim(),
                Map.of("requestId", request.getId(), "decision", resolution.decision(),
                        "replacementReviewerId", replacementReviewerId == null ? "" : replacementReviewerId));
        if (!request.getRequestedByReviewerId().equals(actor.userId())) {
            notifications.send(request.getRequestedByReviewerId(), NotificationType.REVIEWER_REASSIGNMENT_RESOLVED,
                    "Reviewer reassignment request " + status.name().toLowerCase() + " for RFQ-" + rfq.getSourceRfqId(),
                    resolution.reason().trim(), rfq.getPublicId());
        }
        return ReviewerReassignmentResponse.from(rfq.getPublicId(), request);
    }

    private Rfq lockRfq(String publicId) {
        return rfqs.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
    }

    private void appendEvent(Rfq rfq, String eventType, IdentityPrincipal actor, RfqStatus fromStatus,
                             RfqStatus toStatus, String reason, Map<String, ?> payload) {
        try {
            events.save(new RfqWorkflowEvent(rfq, eventType, actor.userId(), objectMapper.writeValueAsString(actor.roles()),
                    fromStatus, toStatus, reason, objectMapper.writeValueAsString(payload), MDC.get("correlationId")));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "Reviewer reassignment history could not be recorded");
        }
    }

    private void requireRole(IdentityPrincipal actor, String... roles) {
        if (!hasAnyRole(actor, roles)) throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
    }

    private boolean hasAnyRole(IdentityPrincipal actor, String... roles) {
        for (String role : roles) if (actor.roles().contains(role)) return true;
        return false;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "Reviewer reassignment request could not be fingerprinted");
        }
    }
}
