package com.globalrfq.rfq.quotation.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqAssignment;
import com.globalrfq.rfq.intake.entity.RfqLineItem;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqLineItemRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.quotation.dto.MandatoryFieldConfigRequest;
import com.globalrfq.rfq.quotation.dto.AdminAuditResponse;
import com.globalrfq.rfq.quotation.dto.QuotationDraftRequest;
import com.globalrfq.rfq.quotation.dto.QuotationDraftResponse;
import com.globalrfq.rfq.quotation.dto.QuotationSnapshot;
import com.globalrfq.rfq.quotation.dto.QuotationVersionResponse;
import com.globalrfq.rfq.quotation.dto.ReviewRequest;
import com.globalrfq.rfq.quotation.dto.WorkflowPolicyRequest;
import com.globalrfq.rfq.quotation.entity.MandatoryFieldConfig;
import com.globalrfq.rfq.quotation.entity.QuotationDraft;
import com.globalrfq.rfq.quotation.entity.QuotationReviewDecision;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.entity.RfqAdminAuditEvent;
import com.globalrfq.rfq.quotation.entity.WorkflowPolicy;
import com.globalrfq.rfq.quotation.repository.MandatoryFieldConfigRepository;
import com.globalrfq.rfq.quotation.repository.QuotationDraftRepository;
import com.globalrfq.rfq.quotation.repository.QuotationReviewDecisionRepository;
import com.globalrfq.rfq.quotation.repository.QuotationVersionRepository;
import com.globalrfq.rfq.quotation.repository.RfqAdminAuditEventRepository;
import com.globalrfq.rfq.quotation.repository.WorkflowPolicyRepository;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuotationWorkflowService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    private static final Set<String> SUPPORTED_REQUIRED_FIELDS = Set.of(
            "currency", "freight", "otherCharges", "discount", "taxAmount", "items",
            "deliveryTimeline", "warranty", "notes", "items.description", "items.quantity",
            "items.unitPrice", "items.unitOfMeasure", "items.manufacturer", "items.partNumber", "items.taaCompliant",
            "items.deliveryTimeline", "items.warranty", "items.note", "items.productUrl");

    private final RfqRepository rfqs;
    private final RfqLineItemRepository lineItems;
    private final RfqAssignmentRepository assignments;
    private final QuotationDraftRepository drafts;
    private final QuotationVersionRepository versions;
    private final QuotationReviewDecisionRepository reviews;
    private final MandatoryFieldConfigRepository mandatoryFields;
    private final WorkflowPolicyRepository policies;
    private final RfqWorkflowEventRepository events;
    private final RfqAdminAuditEventRepository adminAudit;
    private final InAppNotificationService notifications;
    private final ObjectMapper mapper;

    public QuotationWorkflowService(RfqRepository rfqs, RfqLineItemRepository lineItems,
                                   RfqAssignmentRepository assignments, QuotationDraftRepository drafts,
                                   QuotationVersionRepository versions, QuotationReviewDecisionRepository reviews,
                                   MandatoryFieldConfigRepository mandatoryFields, WorkflowPolicyRepository policies,
                                   RfqWorkflowEventRepository events, RfqAdminAuditEventRepository adminAudit,
                                   InAppNotificationService notifications,
                                   ObjectMapper mapper) {
        this.rfqs = rfqs;
        this.lineItems = lineItems;
        this.assignments = assignments;
        this.drafts = drafts;
        this.versions = versions;
        this.reviews = reviews;
        this.mandatoryFields = mandatoryFields;
        this.policies = policies;
        this.events = events;
        this.adminAudit = adminAudit;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Transactional
    public QuotationDraftResponse saveDraft(String publicId, QuotationDraftRequest request, IdentityPrincipal actor) {
        Rfq rfq = lockRfq(publicId);
        requireAssigned(rfq, AssignmentRole.QUOTATION_MANAGER, actor);
        ensureDraftable(rfq);
        validateLineReferences(rfq, request);
        String json = json(request);
        QuotationDraft draft = drafts.findByRfqId(rfq.getId()).orElse(null);
        String previousDraftJson = draft == null ? null : draft.getDraftJson();
        if (draft == null) {
            draft = new QuotationDraft(rfq, json, actor.userId());
        } else {
            draft.replace(json, actor.userId());
        }
        drafts.save(draft);
        RfqStatus from = rfq.getStatus();
        if (from == RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER || from == RfqStatus.REJECTED_BY_REVIEWER
                || from == RfqStatus.REVISION_IN_PROGRESS || from == RfqStatus.REOPENED_FOR_REVISION) {
            rfq.setStatus(RfqStatus.QUOTATION_IN_PROGRESS);
            rfqs.save(rfq);
        }
        Map<String, Object> auditSnapshot = new java.util.LinkedHashMap<>();
        auditSnapshot.put("draftRevision", draft.getVersion());
        auditSnapshot.put("previousDraftJson", previousDraftJson);
        auditSnapshot.put("savedDraft", request);
        appendEvent(rfq, "QUOTATION_DRAFT_SAVED", actor, from, rfq.getStatus(), null, auditSnapshot);
        return draftResponse(draft);
    }

    @Transactional(readOnly = true)
    public QuotationDraftResponse getDraft(String publicId, IdentityPrincipal actor) {
        Rfq rfq = readableRfq(publicId, actor);
        requireAssigned(rfq, AssignmentRole.QUOTATION_MANAGER, actor);
        QuotationDraft draft = drafts.findByRfqId(rfq.getId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Quotation draft was not found"));
        return draftResponse(draft);
    }

    @Transactional
    public QuotationVersionResponse submit(String publicId, String idempotencyKey,
                                          QuotationDraftRequest request, IdentityPrincipal actor) {
        Rfq rfq = lockRfq(publicId);
        requireAssigned(rfq, AssignmentRole.QUOTATION_MANAGER, actor);
        String requestHash = sha256(json(request));
        QuotationVersion prior = versions.findByRfqIdAndIdempotencyKey(rfq.getId(), idempotencyKey).orElse(null);
        if (prior != null) {
            if (!prior.getRequestSha256().equals(requestHash)) {
                throw new RfqException(RfqErrorCode.DUPLICATE_RESOURCE, "The idempotency key was already used with a different quotation");
            }
            return versionResponse(prior);
        }
        ensureDraftable(rfq);
        validateLineReferences(rfq, request);

        Calculation calculation = calculate(request);
        List<String> required = mandatoryFields.findAllByRequiredTrueOrderByFieldKeyAsc().stream()
                .map(MandatoryFieldConfig::getFieldKey).toList();
        List<String> missing = findMissing(required, request);
        List<QuotationSnapshot.QuotedLineSnapshot> itemSnapshots = snapshotItems(request, calculation.lineTotals());
        QuotationSnapshot snapshot = new QuotationSnapshot(normalizedCurrency(request.currency()), itemSnapshots,
                calculation.freight(), calculation.otherCharges(), calculation.discount(), calculation.taxAmount(),
                calculation.subtotal(), calculation.grandTotal(), trimToNull(request.deliveryTimeline()),
                trimToNull(request.warranty()), trimToNull(request.notes()));
        QuotationVersion previous = versions.findTopByRfqIdOrderByVersionNumberDesc(rfq.getId()).orElse(null);
        int number = previous == null ? 1 : previous.getVersionNumber() + 1;
        QuotationVersion version = versions.save(new QuotationVersion(rfq, number, idempotencyKey, requestHash,
                normalizedCurrency(request.currency()), calculation.subtotal(), calculation.freight(),
                calculation.otherCharges(), calculation.discount(), calculation.taxAmount(), calculation.grandTotal(),
                json(snapshot), json(required), json(missing), actor.userId()));
        RfqStatus from = rfq.getStatus();
        rfq.setStatus(RfqStatus.PENDING_REVIEW);
        rfqs.save(rfq);
        appendEvent(rfq, "QUOTATION_VERSION_SUBMITTED", actor, from, rfq.getStatus(), null,
                Map.of("quotationVersionId", version.getId(), "versionNumber", number,
                        "mandatoryFieldsSnapshot", required, "missingMandatoryFields", missing));
        var activeReviewer = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.REVIEWER).orElse(null);
        Long targetId = activeReviewer != null ? activeReviewer.getAssignedUserId() : rfq.getCreatedBy();
        if (targetId != null && !targetId.equals(actor.userId())) {
            notifications.send(targetId, NotificationType.QUOTATION_SUBMITTED,
                    "Quotation V" + number + " submitted for RFQ-" + rfq.getSourceRfqId(),
                    "Quotation version V" + number + " for RFQ-" + rfq.getSourceRfqId()
                            + " is ready for review.",
                    rfq.getPublicId());
        }
        return versionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<QuotationVersionResponse> listVersions(String publicId, IdentityPrincipal actor) {
        Rfq rfq = readableHistoricalRfq(publicId, actor);
        return versions.findByRfqIdOrderByVersionNumberDesc(rfq.getId()).stream().map(this::versionResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuotationVersionResponse getVersion(String publicId, Long versionId, IdentityPrincipal actor) {
        Rfq rfq = readableHistoricalRfq(publicId, actor);
        QuotationVersion version = versions.findByRfqIdAndId(rfq.getId(), versionId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Quotation version was not found"));
        return versionResponse(version);
    }

    @Transactional
    public QuotationVersionResponse review(String publicId, Long versionId, ReviewRequest request, IdentityPrincipal actor) {
        Rfq rfq = lockRfq(publicId);
        requireAssigned(rfq, AssignmentRole.REVIEWER, actor);
        if (!actor.roles().contains("REVIEWER") && !actor.roles().contains("ADMIN") && !actor.roles().contains("SUPER_ADMIN")) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an assigned reviewer can decide this quotation");
        }
        QuotationVersion version = versions.findByRfqIdAndId(rfq.getId(), versionId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Quotation version was not found"));
        if (rfq.getStatus() != RfqStatus.PENDING_REVIEW) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "The RFQ is not waiting for a review decision");
        }
        if (reviews.findByQuotationVersionId(versionId).isPresent()) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "A decision has already been recorded for this quotation version");
        }
        boolean approved = "APPROVED".equals(request.decision());
        if (approved && !readStringList(version.getMissingMandatoryFieldsJson()).isEmpty()) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION,
                    "This quotation has missing configured mandatory fields. Record a rejection with a reason.");
        }
        if (!approved && (request.reason() == null || request.reason().isBlank())) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "A reason is required when rejecting a quotation");
        }
        String reason = trimToNull(request.reason());
        reviews.save(new QuotationReviewDecision(version, request.decision(), actor.userId(), reason));
        RfqStatus from = rfq.getStatus();
        if (approved) {
            rfq.setStatus(RfqStatus.APPROVED_BY_REVIEWER);
        } else {
            long rejectionCount = reviews.countByQuotationVersionRfqIdAndDecision(rfq.getId(), "REJECTED");
            int limit = policies.findById((byte) 1).orElseThrow().getMaxFormalRejections();
            if (rejectionCount >= limit) {
                rfq.setStatus(RfqStatus.ESCALATED);
            } else {
                rfq.setStatus(RfqStatus.REJECTED_BY_REVIEWER);
            }
        }
        rfqs.save(rfq);
        appendEvent(rfq, approved ? "QUOTATION_APPROVED_BY_REVIEWER" : "QUOTATION_REJECTED_BY_REVIEWER",
                actor, from, rfq.getStatus(), reason,
                Map.of("quotationVersionId", versionId, "versionNumber", version.getVersionNumber(),
                        "rejectionCount", reviews.countByQuotationVersionRfqIdAndDecision(rfq.getId(), "REJECTED"),
                        "maxFormalRejections", policies.findById((byte) 1).orElseThrow().getMaxFormalRejections()));

        var activeManager = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), AssignmentRole.QUOTATION_MANAGER).orElse(null);
        if (approved) {
            if (activeManager != null && !activeManager.getAssignedUserId().equals(actor.userId())) {
                notifications.send(activeManager.getAssignedUserId(), NotificationType.QUOTATION_APPROVED,
                        "Quotation approved for RFQ-" + rfq.getSourceRfqId(),
                        "Reviewer approved quotation version V" + version.getVersionNumber() + " for RFQ-" + rfq.getSourceRfqId() + ".",
                        rfq.getPublicId());
            }
            if (rfq.getCreatedBy() != null && !rfq.getCreatedBy().equals(actor.userId())
                    && (activeManager == null || !rfq.getCreatedBy().equals(activeManager.getAssignedUserId()))) {
                notifications.send(rfq.getCreatedBy(), NotificationType.QUOTATION_APPROVED,
                        "RFQ-" + rfq.getSourceRfqId() + " ready to send to customer",
                        "Reviewer approved quotation V" + version.getVersionNumber() + " for RFQ-" + rfq.getSourceRfqId() + ".",
                        rfq.getPublicId());
            }
        } else {
            boolean escalated = rfq.getStatus() == RfqStatus.ESCALATED;
            if (activeManager != null && !activeManager.getAssignedUserId().equals(actor.userId())) {
                notifications.send(activeManager.getAssignedUserId(),
                        escalated ? NotificationType.QUOTATION_ESCALATED : NotificationType.QUOTATION_REJECTED,
                        (escalated ? "Quotation escalated on RFQ-" : "Quotation rejected for RFQ-") + rfq.getSourceRfqId(),
                        "V" + version.getVersionNumber() + " rejected: " + reason,
                        rfq.getPublicId());
            }
            if (escalated && rfq.getCreatedBy() != null && !rfq.getCreatedBy().equals(actor.userId())) {
                notifications.send(rfq.getCreatedBy(), NotificationType.QUOTATION_ESCALATED,
                        "RFQ-" + rfq.getSourceRfqId() + " escalated to Admin",
                        "Maximum reviewer rejections reached on RFQ-" + rfq.getSourceRfqId() + ". Admin resolution required.",
                        rfq.getPublicId());
            }
        }
        return versionResponse(version);
    }

    @Transactional
    public List<String> replaceMandatoryFields(MandatoryFieldConfigRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        Set<String> unique = new HashSet<>(request.requiredFields());
        if (unique.size() != request.requiredFields().size()) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Required fields must not contain duplicates");
        }
        if (!SUPPORTED_REQUIRED_FIELDS.containsAll(unique)) {
            Set<String> unsupported = new HashSet<>(unique);
            unsupported.removeAll(SUPPORTED_REQUIRED_FIELDS);
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Unsupported mandatory field keys: " + String.join(", ", unsupported));
        }
        List<String> before = mandatoryFields.findAllByRequiredTrueOrderByFieldKeyAsc().stream()
                .map(MandatoryFieldConfig::getFieldKey).toList();
        mandatoryFields.deleteAllInBatch();
        List<MandatoryFieldConfig> rows = SUPPORTED_REQUIRED_FIELDS.stream().sorted()
                .map(key -> new MandatoryFieldConfig(key, unique.contains(key), actor.userId(), request.reason())).toList();
        mandatoryFields.saveAll(rows);
        adminAudit.save(new RfqAdminAuditEvent("MANDATORY_FIELDS_CHANGED", actor.userId(), request.reason(),
                json(before), json(request.requiredFields()), MDC.get("correlationId")));
        return request.requiredFields().stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<String> getMandatoryFields(IdentityPrincipal actor) {
        requireAdmin(actor);
        return mandatoryFields.findAllByRequiredTrueOrderByFieldKeyAsc().stream().map(MandatoryFieldConfig::getFieldKey).toList();
    }

    @Transactional
    public int changeRejectionLimit(WorkflowPolicyRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        WorkflowPolicy policy = policies.findById((byte) 1)
                .orElseThrow(() -> new RfqException(RfqErrorCode.INTERNAL_ERROR, "RFQ workflow policy is not initialized"));
        int previous = policy.getMaxFormalRejections();
        policy.update(request.maxFormalRejections(), actor.userId(), request.reason());
        policies.save(policy);
        adminAudit.save(new RfqAdminAuditEvent("REJECTION_LIMIT_CHANGED", actor.userId(), request.reason(),
                json(Map.of("maxFormalRejections", previous)), json(Map.of("maxFormalRejections", request.maxFormalRejections())),
                MDC.get("correlationId")));
        return request.maxFormalRejections();
    }

    @Transactional(readOnly = true)
    public int getRejectionLimit(IdentityPrincipal actor) {
        requireAdmin(actor);
        return policies.findById((byte) 1).orElseThrow().getMaxFormalRejections();
    }

    @Transactional(readOnly = true)
    public List<AdminAuditResponse> adminAudit(IdentityPrincipal actor) {
        requireAdmin(actor);
        return adminAudit.findTop100ByOrderByOccurredAtDescIdDesc().stream().map(AdminAuditResponse::from).toList();
    }

    private Calculation calculate(QuotationDraftRequest request) {
        List<BigDecimal> lineTotals = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean complete = true;
        for (QuotationDraftRequest.QuotedLineRequest item : request.items()) {
            if (item.quantity() == null || item.unitPrice() == null) {
                lineTotals.add(null);
                complete = false;
            } else {
                BigDecimal total = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
                lineTotals.add(total);
                subtotal = subtotal.add(total);
            }
        }
        BigDecimal freight = amount(request.freight());
        BigDecimal other = amount(request.otherCharges());
        BigDecimal discount = amount(request.discount());
        BigDecimal tax = amount(request.taxAmount());
        subtotal = complete ? subtotal.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal grandTotal = null;
        if (complete) {
            grandTotal = subtotal.add(freight).add(other).add(tax).subtract(discount).setScale(2, RoundingMode.HALF_UP);
            if (grandTotal.signum() < 0) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Discount cannot make the quotation total negative");
            }
        }
        return new Calculation(lineTotals, subtotal, freight, other, discount, tax, grandTotal);
    }

    private List<QuotationSnapshot.QuotedLineSnapshot> snapshotItems(QuotationDraftRequest request, List<BigDecimal> totals) {
        List<QuotationSnapshot.QuotedLineSnapshot> result = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            QuotationDraftRequest.QuotedLineRequest item = request.items().get(i);
            result.add(new QuotationSnapshot.QuotedLineSnapshot(item.sourceLineId(), trimToNull(item.description()),
                    item.quantity(), item.unitPrice(), totals.get(i), trimToNull(item.unitOfMeasure()),
                    trimToNull(item.manufacturer()), trimToNull(item.partNumber()), item.taaCompliant(),
                    trimToNull(item.deliveryTimeline()),
                    trimToNull(item.warranty()), trimToNull(item.note()), trimToNull(item.productUrl())));
        }
        return result;
    }

    private List<String> findMissing(List<String> required, QuotationDraftRequest request) {
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            boolean absent = switch (key) {
                case "currency" -> blank(request.currency());
                case "freight" -> request.freight() == null;
                case "otherCharges" -> request.otherCharges() == null;
                case "discount" -> request.discount() == null;
                case "taxAmount" -> request.taxAmount() == null;
                case "items" -> request.items() == null || request.items().isEmpty();
                case "deliveryTimeline" -> blank(request.deliveryTimeline());
                case "warranty" -> blank(request.warranty());
                case "notes" -> blank(request.notes());
                default -> request.items() == null || request.items().isEmpty()
                        || request.items().stream().anyMatch(item -> itemMissing(key, item));
            };
            if (absent) missing.add(key);
        }
        return missing;
    }

    private boolean itemMissing(String key, QuotationDraftRequest.QuotedLineRequest item) {
        return switch (key) {
            case "items.description" -> blank(item.description());
            case "items.quantity" -> item.quantity() == null;
            case "items.unitPrice" -> item.unitPrice() == null;
            case "items.unitOfMeasure" -> blank(item.unitOfMeasure());
            case "items.manufacturer" -> blank(item.manufacturer());
            case "items.partNumber" -> blank(item.partNumber());
            case "items.taaCompliant" -> item.taaCompliant() == null;
            case "items.deliveryTimeline" -> blank(item.deliveryTimeline());
            case "items.warranty" -> blank(item.warranty());
            case "items.note" -> blank(item.note());
            case "items.productUrl" -> blank(item.productUrl());
            default -> false;
        };
    }

    private void validateLineReferences(Rfq rfq, QuotationDraftRequest request) {
        Set<String> sourceIds = lineItems.findByRfqIdOrderByLineNumberAsc(rfq.getId()).stream()
                .map(RfqLineItem::getSourceLineId).collect(java.util.stream.Collectors.toSet());
        for (QuotationDraftRequest.QuotedLineRequest item : request.items()) {
            if (item.sourceLineId() == null || !sourceIds.contains(item.sourceLineId())) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED,
                        "Each quotation line must reference a source line ID from this RFQ");
            }
        }
    }

    private QuotationVersionResponse versionResponse(QuotationVersion version) {
        QuotationReviewDecision review = reviews.findByQuotationVersionId(version.getId()).orElse(null);
        String status = review == null ? "PENDING_REVIEW" : review.getDecision();
        return new QuotationVersionResponse(version.getId(), version.getVersionNumber(), status,
                version.getCurrency(), version.getSubtotal(), version.getFreight(), version.getOtherCharges(),
                version.getDiscount(), version.getTaxAmount(), version.getGrandTotal(),
                read(version.getQuotationJson(), QuotationSnapshot.class),
                readStringList(version.getMandatoryFieldsSnapshotJson()), readStringList(version.getMissingMandatoryFieldsJson()),
                version.getSubmittedByUserId(), version.getSubmittedAt(), review == null ? null
                : new QuotationVersionResponse.ReviewResponse(review.getDecision(), review.getReviewerUserId(), review.getReason(), review.getDecidedAt()));
    }

    private QuotationDraftResponse draftResponse(QuotationDraft draft) {
        return new QuotationDraftResponse(read(draft.getDraftJson(), QuotationDraftRequest.class), draft.getVersion(), draft.getSavedAt());
    }

    private Rfq readableRfq(String publicId, IdentityPrincipal actor) {
        Rfq rfq = rfqs.findWithLineItemsByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        if (!isAdmin(actor) && !assignments.existsByRfqIdAndAssignedUserIdAndActiveTrue(rfq.getId(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have access to this RFQ");
        }
        return rfq;
    }

    private Rfq readableHistoricalRfq(String publicId, IdentityPrincipal actor) {
        Rfq rfq = rfqs.findWithLineItemsByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        if (!isAdmin(actor) && !assignments.existsByRfqIdAndAssignedUserId(rfq.getId(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have access to this RFQ's quotation history");
        }
        return rfq;
    }

    private Rfq lockRfq(String publicId) {
        return rfqs.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
    }

    private void requireAssigned(Rfq rfq, AssignmentRole role, IdentityPrincipal actor) {
        RfqAssignment assignment = assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), role).orElse(null);
        if (assignment == null || !assignment.getAssignedUserId().equals(actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You are not the currently assigned " + role.name().replace('_', ' ').toLowerCase());
        }
    }

    private void ensureDraftable(Rfq rfq) {
        if (!Set.of(RfqStatus.ASSIGNED_TO_QUOTATION_MANAGER, RfqStatus.QUOTATION_IN_PROGRESS,
                RfqStatus.REJECTED_BY_REVIEWER, RfqStatus.REVISION_IN_PROGRESS,
                RfqStatus.REOPENED_FOR_REVISION).contains(rfq.getStatus())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "A quotation can only be edited before reviewer approval or while a revision is allowed");
        }
    }

    private void requireAdmin(IdentityPrincipal actor) {
        if (!isAdmin(actor)) throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can change RFQ workflow configuration");
    }

    private boolean isAdmin(IdentityPrincipal actor) {
        return actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
    }

    private void appendEvent(Rfq rfq, String type, IdentityPrincipal actor, RfqStatus from, RfqStatus to,
                             String reason, Map<String, ?> payload) {
        events.save(new RfqWorkflowEvent(rfq, type, actor.userId(), json(actor.roles().stream().sorted().toList()),
                from, to, reason, json(payload), MDC.get("correlationId")));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The quotation snapshot could not be serialized");
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "A stored quotation snapshot could not be read");
        }
    }

    private List<String> readStringList(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "A stored quotation field snapshot could not be read");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The quotation request could not be fingerprinted");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizedCurrency(String currency) {
        return trimToNull(currency);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record Calculation(List<BigDecimal> lineTotals, BigDecimal subtotal, BigDecimal freight,
                               BigDecimal otherCharges, BigDecimal discount, BigDecimal taxAmount,
                               BigDecimal grandTotal) { }
}
