package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqTimeProperties;
import com.globalrfq.rfq.intake.dto.AssignmentRequest;
import com.globalrfq.rfq.intake.dto.AdminWorkflowResolutionRequest;
import com.globalrfq.rfq.intake.dto.CreateRfqRequest;
import com.globalrfq.rfq.intake.dto.CustomerDecisionRequest;
import com.globalrfq.rfq.intake.dto.CustomerSubmissionRequest;
import com.globalrfq.rfq.intake.dto.MarketingActionRequest;
import com.globalrfq.rfq.intake.dto.RfqResponse;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.CustomerDecision;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqAssignment;
import com.globalrfq.rfq.intake.entity.RfqCustomerDecisionRecord;
import com.globalrfq.rfq.intake.entity.RfqCustomerSubmission;
import com.globalrfq.rfq.intake.entity.RfqLineItem;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqCustomerDecisionRepository;
import com.globalrfq.rfq.intake.repository.RfqCustomerSubmissionRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.repository.QuotationReviewDecisionRepository;
import com.globalrfq.rfq.quotation.repository.QuotationVersionRepository;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.ZoneId;

@Service
public class RfqCommandService {
    private final RfqRepository rfqs;
    private final RfqAssignmentRepository assignments;
    private final RfqCustomerSubmissionRepository submissions;
    private final RfqCustomerDecisionRepository decisions;
    private final RfqWorkflowEventRepository events;
    private final QuotationVersionRepository quotationVersions;
    private final QuotationReviewDecisionRepository quotationReviews;
    private final RfqTimeProperties timeProperties;
    private final InAppNotificationService notifications;
    private final ObjectMapper objectMapper;

    public RfqCommandService(RfqRepository rfqs, RfqAssignmentRepository assignments,
                             RfqCustomerSubmissionRepository submissions,
                             RfqCustomerDecisionRepository decisions,
                             RfqWorkflowEventRepository events, QuotationVersionRepository quotationVersions,
                             QuotationReviewDecisionRepository quotationReviews,
                             RfqTimeProperties timeProperties,
                             InAppNotificationService notifications,
                             ObjectMapper objectMapper) {
        this.rfqs = rfqs;
        this.assignments = assignments;
        this.submissions = submissions;
        this.decisions = decisions;
        this.events = events;
        this.quotationVersions = quotationVersions;
        this.quotationReviews = quotationReviews;
        this.timeProperties = timeProperties;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RfqResponse create(CreateRfqRequest request, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        if (rfqs.existsBySourceSystemAndSourceRfqId(request.sourceSystem().trim(), request.sourceRfqId().trim())) {
            throw new RfqException(RfqErrorCode.DUPLICATE_RESOURCE, "An RFQ with this source system and source RFQ ID already exists");
        }
        validateUniqueLines(request.lineItems());
        String timeZone = request.dueAtLocal() == null ? request.dueTimeZone()
                : (request.dueTimeZone() == null || request.dueTimeZone().isBlank()
                ? timeProperties.getDefaultTimeZone() : request.dueTimeZone().trim());
        if (timeZone != null && !timeZone.isBlank()) {
            try { ZoneId.of(timeZone); }
            catch (Exception invalidZone) { throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "dueTimeZone must be a valid IANA timezone, for example Asia/Kolkata"); }
        }
        Rfq rfq = new Rfq(request.sourceSystem().trim(), request.sourceRfqId().trim(), request.sourceReference(),
                request.sourceQuotationManagerName(),
                request.customerName().trim(), request.customerCompany(), request.buyerName(), request.buyerEmail(),
                request.buyerPhone(), request.requestTitle(), request.dueAtLocal(), timeZone,
                request.requestedDeliveryDate(), request.shipToText(), request.sourceUrl(), request.termsAndConditions(),
                json(request.sourceData()), actor.userId());
        for (CreateRfqRequest.CreateRfqLineItemRequest line : request.lineItems()) {
            rfq.addLineItem(new RfqLineItem(line.sourceLineId().trim(), line.lineNumber(), line.description().trim(),
                    line.requestedQuantity(), line.unitOfMeasure(), line.customerPartNumber(), line.requestedManufacturer(),
                    line.requestedPartNumber(), normalizeOptional(line.procurementCategory()), line.taaRequired(),
                    line.technicalSpecifications(), json(line.sourceData())));
        }
        rfqs.save(rfq);
        appendEvent(rfq, "RFQ_IMPORTED", actor, null, RfqStatus.IMPORTED, null,
                Map.of("sourceSystem", rfq.getSourceSystem(), "sourceRfqId", rfq.getSourceRfqId()));
        return response(rfq);
    }

    @Transactional
    public RfqResponse assign(String publicId, AssignmentRequest request, IdentityPrincipal actor) {
        assertAssignmentAuthority(publicId, request.assignmentRole(), actor);
        Rfq rfq = lockRfq(publicId);
        ensureNotTerminal(rfq);
        AssignmentRole role = request.assignmentRole();
        validateAssignmentStage(rfq, role);

        var previous = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), role).orElse(null);
        if (previous != null && previous.getAssignedUserId().equals(request.assignedUserId())) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "This user is already assigned to the RFQ for that role");
        }
        if (previous != null && (request.reason() == null || request.reason().isBlank())) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "A reason is required when reassigning an RFQ");
        }

        Long previousUserId = previous == null ? null : previous.getAssignedUserId();
        RfqStatus from = rfq.getStatus();
        if (previous != null) {
            previous.deactivate();
            assignments.saveAndFlush(previous);
        }

        RfqAssignment assignment = assignments.save(new RfqAssignment(rfq, role, request.assignedUserId(), actor.userId(), request.reason()));
        if (role == AssignmentRole.QUOTATION_MANAGER && rfq.getStatus() == RfqStatus.IMPORTED) {
            rfq.setStatus(RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER);
        } else if (role == AssignmentRole.MARKETING_EXECUTIVE) {
            rfq.setStatus(RfqStatus.ASSIGNED_TO_MARKETING);
        }
        appendEvent(rfq, previous == null ? "ASSIGNED" : "REASSIGNED", actor, from, rfq.getStatus(), request.reason(),
                Map.of("role", role.name(), "previousUserId", previousUserId == null ? "" : previousUserId,
                        "assignedUserId", request.assignedUserId()));
        rfqs.save(rfq);

        String roleLabel = role.name().replace('_', ' ').toLowerCase();
        NotificationType type = role == AssignmentRole.MARKETING_EXECUTIVE
                ? NotificationType.MARKETING_ASSIGNED
                : (previous == null ? NotificationType.RFQ_ASSIGNED : NotificationType.RFQ_REASSIGNED);
        notifyUser(request.assignedUserId(), actor.userId(), type,
                "Assigned to RFQ-" + rfq.getSourceRfqId(),
                "You have been assigned as " + roleLabel + " on RFQ-" + rfq.getSourceRfqId()
                        + " (" + rfq.getCustomerName() + ").",
                rfq.getPublicId());
        if (previousUserId != null) {
            notifyUser(previousUserId, actor.userId(), NotificationType.RFQ_REASSIGNED,
                    "Reassigned from RFQ-" + rfq.getSourceRfqId(),
                    "Your " + roleLabel + " assignment on RFQ-" + rfq.getSourceRfqId()
                            + " has been transferred to another user.",
                    rfq.getPublicId());
        }
        return response(rfq);
    }

    @Transactional(readOnly = true)
    public void assertAssignmentAuthority(String publicId, AssignmentRole role, IdentityPrincipal actor) {
        boolean admin = actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
        if (admin) return;
        if (role != AssignmentRole.REVIEWER || !actor.roles().contains("QUOTATION_MANAGER")) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You are not authorized to assign this RFQ role");
        }
        Rfq rfq = rfqs.findByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        if (!rfq.isQuotationManagerCanAssignReviewer()) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "An Admin has not delegated reviewer assignment on this RFQ");
        }
        RfqAssignment current = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.QUOTATION_MANAGER).orElse(null);
        if (current == null || !current.getAssignedUserId().equals(actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only the assigned quotation manager can assign a reviewer");
        }
    }

    @Transactional(readOnly = true)
    public boolean reviewerAssignmentDelegation(String publicId, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        return rfqs.findByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"))
                .isQuotationManagerCanAssignReviewer();
    }

    @Transactional
    public boolean updateReviewerAssignmentDelegation(String publicId, boolean allowed, String reason, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(publicId);
        boolean previous = rfq.isQuotationManagerCanAssignReviewer();
        rfq.setQuotationManagerCanAssignReviewer(allowed);
        rfqs.save(rfq);
        appendEvent(rfq, "RFQ_REVIEWER_ASSIGNMENT_DELEGATION_CHANGED", actor, rfq.getStatus(), rfq.getStatus(), reason,
                Map.of("quotationManagerCanAssignReviewerBefore", previous,
                        "quotationManagerCanAssignReviewerAfter", allowed));
        return allowed;
    }

    @Transactional
    public RfqResponse recordCustomerSubmission(String publicId, CustomerSubmissionRequest request,
                                                String idempotencyKey, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(publicId);
        RfqCustomerSubmission prior = submissions.findByRfqIdAndIdempotencyKey(rfq.getId(), idempotencyKey).orElse(null);
        if (prior != null) {
            if (prior.getQuotationVersionId().equals(request.quotationVersionId())
                    && java.util.Objects.equals(prior.getExternalReference(), request.externalReference())) {
                return response(rfq);
            }
            throw new RfqException(RfqErrorCode.DUPLICATE_RESOURCE, "The idempotency key was already used for a different submission");
        }
        if (rfq.getStatus() != RfqStatus.APPROVED_BY_REVIEWER) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Only an internally approved quotation can be recorded as sent to the customer");
        }
        QuotationVersion quote = quotationVersions.findById(request.quotationVersionId())
                .filter(version -> version.getRfq().getId().equals(rfq.getId()))
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Quotation version was not found for this RFQ"));
        boolean approvedVersion = quotationReviews.findByQuotationVersionId(quote.getId())
                .map(review -> "APPROVED".equals(review.getDecision())).orElse(false);
        if (!approvedVersion) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Only a reviewer-approved quotation version can be sent to the customer");
        }
        RfqStatus from = rfq.getStatus();
        submissions.save(new RfqCustomerSubmission(rfq, request.quotationVersionId(), actor.userId(),
                request.externalReference(), idempotencyKey, request.notes()));
        rfq.setStatus(RfqStatus.SENT_TO_CUSTOMER);
        rfqs.save(rfq);
        appendEvent(rfq, "CUSTOMER_SUBMISSION_RECORDED", actor, from, rfq.getStatus(), request.notes(),
                Map.of("quotationVersionId", request.quotationVersionId(),
                        "externalReference", request.externalReference() == null ? "" : request.externalReference()));
        notifyActiveAssignee(rfq, AssignmentRole.QUOTATION_MANAGER, actor.userId(),
                NotificationType.CUSTOMER_SUBMISSION_RECORDED,
                "Quotation sent for RFQ-" + rfq.getSourceRfqId(),
                "Admin recorded that the approved quotation for RFQ-" + rfq.getSourceRfqId() + " was sent to the customer.");
        return response(rfq);
    }

    @Transactional
    public RfqResponse recordCustomerDecision(String publicId, CustomerDecisionRequest request,
                                              String idempotencyKey, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        if (request.decision() == CustomerDecision.PENDING) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "PENDING is not a decision to record");
        }
        Rfq rfq = lockRfq(publicId);
        RfqCustomerDecisionRecord prior = decisions.findByRfqIdAndIdempotencyKey(rfq.getId(), idempotencyKey).orElse(null);
        if (prior != null) {
            if (prior.getDecision() == request.decision()
                    && java.util.Objects.equals(prior.getQuotationVersionId(), request.quotationVersionId())) {
                return response(rfq);
            }
            throw new RfqException(RfqErrorCode.DUPLICATE_RESOURCE, "The idempotency key was already used for a different customer decision");
        }
        if (rfq.getStatus() != RfqStatus.SENT_TO_CUSTOMER) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Customer decisions can only be recorded after the quotation is sent");
        }
        RfqCustomerSubmission latestSubmission = submissions.findByRfqIdOrderBySentAtDesc(rfq.getId()).stream()
                .findFirst().orElseThrow(() -> new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "No customer submission is recorded"));
        if (!latestSubmission.getQuotationVersionId().equals(request.quotationVersionId())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Customer decision must refer to the latest submitted quotation version");
        }

        RfqStatus from = rfq.getStatus();
        decisions.save(new RfqCustomerDecisionRecord(rfq, request.decision(), request.quotationVersionId(),
                actor.userId(), request.notes(), request.sourceReference(), idempotencyKey));
        rfq.setCustomerDecision(request.decision());
        if (request.decision() == CustomerDecision.APPROVED) {
            rfq.setStatus(RfqStatus.CUSTOMER_APPROVED);
        } else if (request.decision() == CustomerDecision.REJECTED) {
            rfq.setStatus(RfqStatus.CUSTOMER_REJECTED);
        }
        rfqs.save(rfq);
        appendEvent(rfq, "CUSTOMER_DECISION_RECORDED", actor, from, rfq.getStatus(), request.notes(),
                Map.of("decision", request.decision().name(), "quotationVersionId", request.quotationVersionId(),
                        "sourceReference", request.sourceReference() == null ? "" : request.sourceReference()));
        String decisionText = request.decision().name().replace('_', ' ').toLowerCase();
        notifyActiveAssignee(rfq, AssignmentRole.QUOTATION_MANAGER, actor.userId(),
                NotificationType.CUSTOMER_DECISION_RECORDED,
                "Customer decision on RFQ-" + rfq.getSourceRfqId(),
                "Customer decision recorded as " + decisionText + " for RFQ-" + rfq.getSourceRfqId() + ".");
        notifyActiveAssignee(rfq, AssignmentRole.REVIEWER, actor.userId(),
                NotificationType.CUSTOMER_DECISION_RECORDED,
                "Customer decision on RFQ-" + rfq.getSourceRfqId(),
                "Customer decision recorded as " + decisionText + " for RFQ-" + rfq.getSourceRfqId() + ".");
        return response(rfq);
    }

    @Transactional
    public RfqResponse resolveEscalation(String publicId, AdminWorkflowResolutionRequest request, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(publicId);
        if (rfq.getStatus() != RfqStatus.ESCALATED) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "This RFQ is not escalated for Admin decision");
        }
        RfqStatus from = rfq.getStatus();
        if ("AUTHORIZE_REVISION".equals(request.resolution())) {
            if (assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.QUOTATION_MANAGER).isEmpty()) {
                throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Assign an active quotation manager before authorizing another revision");
            }
            rfq.setStatus(RfqStatus.REVISION_IN_PROGRESS);
        } else {
            rfq.setStatus(RfqStatus.CLOSED);
        }
        rfqs.save(rfq);
        appendEvent(rfq, "ESCALATION_RESOLVED", actor, from, rfq.getStatus(), request.reason(),
                Map.of("resolution", request.resolution()));
        notifyActiveAssignee(rfq, AssignmentRole.QUOTATION_MANAGER, actor.userId(),
                NotificationType.GENERAL,
                "Escalation resolved on RFQ-" + rfq.getSourceRfqId(),
                "Admin resolved the escalation on RFQ-" + rfq.getSourceRfqId() + " (" + request.resolution() + ").");
        return response(rfq);
    }

    @Transactional
    public RfqResponse resolveCustomerRejection(String publicId, AdminWorkflowResolutionRequest request, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(publicId);
        if (rfq.getStatus() != RfqStatus.CUSTOMER_REJECTED) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "This RFQ does not have a customer rejection awaiting Admin action");
        }
        RfqStatus from = rfq.getStatus();
        if ("AUTHORIZE_REVISION".equals(request.resolution())) {
            if (assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.QUOTATION_MANAGER).isEmpty()) {
                throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Assign an active quotation manager before reopening this RFQ");
            }
            rfq.setStatus(RfqStatus.REOPENED_FOR_REVISION);
        } else {
            rfq.setStatus(RfqStatus.CLOSED);
        }
        rfqs.save(rfq);
        appendEvent(rfq, "CUSTOMER_REJECTION_RESOLVED", actor, from, rfq.getStatus(), request.reason(),
                Map.of("resolution", request.resolution()));
        notifyActiveAssignee(rfq, AssignmentRole.QUOTATION_MANAGER, actor.userId(),
                NotificationType.GENERAL,
                "Customer rejection resolved on RFQ-" + rfq.getSourceRfqId(),
                "Admin resolved the customer rejection on RFQ-" + rfq.getSourceRfqId() + " (" + request.resolution() + ").");
        return response(rfq);
    }

    @Transactional
    public RfqResponse markReadyForMarketing(String publicId, MarketingActionRequest request, IdentityPrincipal actor) {
        requireRole(actor, "ADMIN", "SUPER_ADMIN");
        Rfq rfq = lockRfq(publicId);
        if (rfq.getStatus() != RfqStatus.CUSTOMER_APPROVED) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "Only a customer-approved RFQ can be marked ready for marketing");
        }
        RfqStatus from = rfq.getStatus();
        rfq.setStatus(RfqStatus.READY_FOR_MARKETING);
        rfqs.save(rfq);
        String notes = request == null ? null : normalizeOptional(request.notes());
        appendEvent(rfq, "RFQ_READY_FOR_MARKETING", actor, from, rfq.getStatus(), notes,
                Map.of("notes", notes == null ? "" : notes));
        return response(rfq);
    }

    @Transactional
    public RfqResponse completeMarketing(String publicId, MarketingActionRequest request, IdentityPrincipal actor) {
        Rfq rfq = lockRfq(publicId);
        boolean isAdmin = actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
        if (!isAdmin) {
            if (!actor.roles().contains("MARKETING_EXECUTIVE")) {
                throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an assigned Marketing Executive or Admin can complete marketing");
            }
            var assignedMarketing = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.MARKETING_EXECUTIVE)
                    .orElse(null);
            if (assignedMarketing == null || !assignedMarketing.getAssignedUserId().equals(actor.userId())) {
                throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You are not the assigned marketing executive for this RFQ");
            }
        }
        if (rfq.getStatus() != RfqStatus.ASSIGNED_TO_MARKETING) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "Marketing completion is only permitted while assigned to marketing");
        }
        RfqStatus from = rfq.getStatus();
        rfq.setStatus(RfqStatus.CLOSED);
        rfqs.save(rfq);
        String outcome = request == null ? null : normalizeOptional(request.outcome());
        String notes = request == null ? null : normalizeOptional(request.notes());
        String detail = notes != null ? notes : (outcome != null ? outcome : "Marketing workflow completed");
        appendEvent(rfq, "MARKETING_ACTIVITY_COMPLETED", actor, from, rfq.getStatus(), detail,
                Map.of("outcome", outcome == null ? "" : outcome, "notes", notes == null ? "" : notes));
        notifyUser(rfq.getCreatedBy(), actor.userId(), NotificationType.GENERAL,
                "Marketing completed for RFQ-" + rfq.getSourceRfqId(),
                "Marketing activities were completed and RFQ-" + rfq.getSourceRfqId() + " is now closed.",
                rfq.getPublicId());
        return response(rfq);
    }

    private Rfq lockRfq(String publicId) {
        return rfqs.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
    }

    private void validateAssignmentStage(Rfq rfq, AssignmentRole role) {
        if (role == AssignmentRole.MARKETING_EXECUTIVE) {
            if (!Set.of(RfqStatus.CUSTOMER_APPROVED, RfqStatus.READY_FOR_MARKETING, RfqStatus.ASSIGNED_TO_MARKETING)
                    .contains(rfq.getStatus())) {
                throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Marketing assignment is allowed only after customer approval");
            }
            return;
        }
        Set<RfqStatus> assignableStatuses = Set.of(RfqStatus.IMPORTED, RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER,
                RfqStatus.QUOTATION_IN_PROGRESS, RfqStatus.PENDING_REVIEW, RfqStatus.REJECTED_BY_REVIEWER,
                RfqStatus.REVISION_IN_PROGRESS,
                RfqStatus.REOPENED_FOR_REVISION, RfqStatus.ESCALATED);
        if (!assignableStatuses.contains(rfq.getStatus())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "This RFQ is past its quotation and reviewer assignment stage");
        }
    }

    private void ensureNotTerminal(Rfq rfq) {
        if (rfq.getStatus() == RfqStatus.CLOSED || rfq.getStatus() == RfqStatus.CANCELLED) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Closed or cancelled RFQs cannot be changed");
        }
    }

    private void validateUniqueLines(List<CreateRfqRequest.CreateRfqLineItemRequest> lineItems) {
        Set<String> lineIds = new HashSet<>();
        Set<Integer> lineNumbers = new HashSet<>();
        for (var line : lineItems) {
            if (!lineIds.add(line.sourceLineId().trim()) || !lineNumbers.add(line.lineNumber())) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Source line IDs and line numbers must be unique within an RFQ");
            }
        }
    }

    private void requireRole(IdentityPrincipal actor, String... requiredRoles) {
        for (String role : requiredRoles) {
            if (actor.roles().contains(role)) return;
        }
        throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
    }

    private void appendEvent(Rfq rfq, String type, IdentityPrincipal actor, RfqStatus from, RfqStatus to,
                             String reason, Map<String, ?> payload) {
        events.save(new RfqWorkflowEvent(rfq, type, actor.userId(), json(actor.roles().stream().sorted().toList()),
                from, to, reason, json(payload), MDC.get("correlationId")));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "Source data could not be encoded as JSON");
        }
    }

    private RfqResponse response(Rfq rfq) {
        List<RfqAssignment> currentAssignments = assignments.findByRfqIdAndActiveTrueOrderByAssignedAtAsc(rfq.getId());
        return RfqResponse.from(rfq, currentAssignments);
    }

    private void notifyActiveAssignee(Rfq rfq, AssignmentRole role, Long actorUserId,
                                      NotificationType type, String title, String message) {
        assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), role)
                .ifPresent(assignment -> notifyUser(assignment.getAssignedUserId(), actorUserId,
                        type, title, message, rfq.getPublicId()));
    }

    private void notifyUser(Long targetUserId, Long actorUserId, NotificationType type,
                            String title, String message, String rfqPublicId) {
        if (targetUserId == null || targetUserId.equals(actorUserId)) {
            return;
        }
        notifications.send(targetUserId, type, title, message, rfqPublicId);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
