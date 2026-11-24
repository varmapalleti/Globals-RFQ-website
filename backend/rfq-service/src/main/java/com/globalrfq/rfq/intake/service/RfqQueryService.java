package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.intake.dto.RfqDashboardSummaryResponse;
import com.globalrfq.rfq.intake.dto.RfqResponse;
import com.globalrfq.rfq.intake.dto.RfqSummaryResponse;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqAssignment;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqLineItemRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.workflow.dto.WorkflowEventResponse;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RfqQueryService {
    private final RfqRepository rfqs;
    private final RfqAssignmentRepository assignments;
    private final RfqLineItemRepository lineItems;
    private final RfqWorkflowEventRepository events;

    public RfqQueryService(RfqRepository rfqs, RfqAssignmentRepository assignments,
                           RfqLineItemRepository lineItems, RfqWorkflowEventRepository events) {
        this.rfqs = rfqs;
        this.assignments = assignments;
        this.lineItems = lineItems;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<RfqSummaryResponse> list(IdentityPrincipal actor, int page, int size,
                                                 String sourceRfqId, String search,
                                                 RfqStatus status, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
        }
        Sort primarySort = "requestedDeliveryDate".equals(sort)
                ? Sort.by(Sort.Direction.ASC, "requestedDeliveryDate")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, primarySort.and(Sort.by(Sort.Direction.DESC, "id")));
        String lookup = sourceRfqId == null ? null : sourceRfqId.trim();
        if (lookup != null && lookup.isEmpty()) lookup = null;
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<Rfq> result;
        if (lookup != null) {
            result = isAdmin(actor)
                    ? rfqs.findBySourceRfqId(lookup, pageable)
                    : rfqs.findAssignedToUserBySourceRfqId(actor.userId(), lookup, pageable);
        } else {
            result = isAdmin(actor)
                    ? rfqs.searchAll(normalizedSearch, status, pageable)
                    : rfqs.searchAssignedToUser(actor.userId(), normalizedSearch, status, pageable);
        }

        List<Long> rfqIds = result.getContent().stream().map(Rfq::getId).toList();
        Map<Long, Long> lineCounts = new HashMap<>();
        Map<Long, Map<AssignmentRole, RfqAssignment>> activeByRfq = new HashMap<>();
        if (!rfqIds.isEmpty()) {
            lineItems.countByRfqIdIn(rfqIds).forEach(row -> lineCounts.put(row.getRfqId(), row.getLineCount()));
            assignments.findByRfqIdInAndActiveTrueOrderByAssignedAtAsc(rfqIds).forEach(a ->
                    activeByRfq.computeIfAbsent(a.getRfq().getId(), k -> new EnumMap<>(AssignmentRole.class))
                            .put(a.getAssignmentRole(), a));
        }

        return PageResponse.from(result.map(rfq -> {
            long count = lineCounts.getOrDefault(rfq.getId(), 0L);
            RfqAssignment primary = pickPrimaryAssignment(rfq.getStatus(), activeByRfq.get(rfq.getId()));
            return RfqSummaryResponse.from(rfq, count,
                    primary == null ? null : primary.getAssignedUserId(),
                    primary == null ? null : primary.getAssignmentRole().name());
        }));
    }

    private RfqAssignment pickPrimaryAssignment(RfqStatus status, Map<AssignmentRole, RfqAssignment> active) {
        if (active == null || active.isEmpty()) return null;
        if (status == RfqStatus.ASSIGNED_TO_MARKETING || status == RfqStatus.READY_FOR_MARKETING) {
            RfqAssignment m = active.get(AssignmentRole.MARKETING_EXECUTIVE);
            if (m != null) return m;
        }
        if (status == RfqStatus.PENDING_REVIEW) {
            RfqAssignment r = active.get(AssignmentRole.REVIEWER);
            if (r != null) return r;
        }
        RfqAssignment qm = active.get(AssignmentRole.QUOTATION_MANAGER);
        if (qm != null) return qm;
        return active.values().iterator().next();
    }

    @Transactional(readOnly = true)
    public RfqDashboardSummaryResponse dashboardSummary(IdentityPrincipal actor) {
        List<RfqRepository.RfqStatusCount> counts = isAdmin(actor)
                ? rfqs.countStatusesForReport(null, null, null)
                : rfqs.countStatusesForAssignedUser(actor.userId());
        var statusCounts = new java.util.LinkedHashMap<String, Long>();
        counts.forEach(row -> statusCounts.put(row.getStatus().name(), row.getAmount()));
        long total = isAdmin(actor)
                ? statusCounts.values().stream().mapToLong(Long::longValue).sum()
                : rfqs.countAssignedToUser(actor.userId());
        return new RfqDashboardSummaryResponse(total, java.util.Map.copyOf(statusCounts));
    }

    @Transactional(readOnly = true)
    public RfqResponse get(String publicId, IdentityPrincipal actor) {
        Rfq rfq = getEntity(publicId);
        assertCanRead(rfq, actor);
        return RfqResponse.from(rfq, assignments.findByRfqIdAndActiveTrueOrderByAssignedAtAsc(rfq.getId()));
    }

    @Transactional(readOnly = true)
    public List<WorkflowEventResponse> history(String publicId, IdentityPrincipal actor) {
        Rfq rfq = getEntity(publicId);
        assertCanRead(rfq, actor);
        return events.findByRfqIdOrderByOccurredAtAscIdAsc(rfq.getId()).stream()
                .map(WorkflowEventResponse::from).toList();
    }

    private Rfq getEntity(String publicId) {
        return rfqs.findWithLineItemsByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
    }

    private void assertCanRead(Rfq rfq, IdentityPrincipal actor) {
        if (!isAdmin(actor) && !assignments.existsByRfqIdAndAssignedUserId(rfq.getId(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have access to this RFQ");
        }
    }

    private boolean isAdmin(IdentityPrincipal actor) {
        return actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
    }
}
