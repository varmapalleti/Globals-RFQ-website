package com.globalrfq.rfq.redflag.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import com.globalrfq.rfq.redflag.dto.CreateRedFlagRequest;
import com.globalrfq.rfq.redflag.dto.RedFlagResponse;
import com.globalrfq.rfq.redflag.dto.ResolveRedFlagRequest;
import com.globalrfq.rfq.redflag.entity.RedFlag;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import com.globalrfq.rfq.redflag.repository.RedFlagRepository;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RedFlagService {
    private final RedFlagRepository redFlags;
    private final RfqRepository rfqs;
    private final RfqAssignmentRepository assignments;
    private final RfqWorkflowEventRepository events;
    private final InAppNotificationService notifications;
    private final ObjectMapper mapper;

    public RedFlagService(RedFlagRepository redFlags, RfqRepository rfqs,
                          RfqAssignmentRepository assignments,
                          RfqWorkflowEventRepository events,
                          InAppNotificationService notifications,
                          ObjectMapper mapper) {
        this.redFlags = redFlags;
        this.rfqs = rfqs;
        this.assignments = assignments;
        this.events = events;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Transactional
    public RedFlagResponse create(CreateRedFlagRequest request, IdentityPrincipal actor) {
        Rfq rfq = rfqs.findByPublicId(request.rfqPublicId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        boolean isAdmin = isAdmin(actor);
        if (!isAdmin && !assignments.existsByRfqIdAndAssignedUserIdAndActiveTrue(rfq.getId(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin or actively assigned RFQ participant can raise a red flag");
        }
        RedFlag flag = new RedFlag(rfq.getId(), rfq.getPublicId(), request.severity(),
                request.reason().trim(), actor.userId(), request.assignedTo());
        redFlags.save(flag);
        appendEvent(rfq, "RED_FLAG_RAISED", actor, request.reason().trim(),
                Map.of("redFlagId", flag.getId(), "severity", flag.getSeverity().name(),
                        "assignedTo", request.assignedTo() == null ? "" : request.assignedTo()));

        if (request.assignedTo() != null && !Objects.equals(request.assignedTo(), actor.userId())) {
            notifications.send(request.assignedTo(), NotificationType.RED_FLAG_RAISED,
                    "Red flag raised on RFQ-" + rfq.getSourceRfqId(),
                    "[" + flag.getSeverity().name() + "] " + flag.getReason(),
                    rfq.getPublicId());
        }
        if (rfq.getCreatedBy() != null
                && !Objects.equals(rfq.getCreatedBy(), actor.userId())
                && !Objects.equals(rfq.getCreatedBy(), request.assignedTo())) {
            notifications.send(rfq.getCreatedBy(), NotificationType.RED_FLAG_RAISED,
                    "Red flag raised on RFQ-" + rfq.getSourceRfqId(),
                    "[" + flag.getSeverity().name() + "] " + flag.getReason(),
                    rfq.getPublicId());
        }
        return RedFlagResponse.from(flag, rfq.getSourceRfqId(), rfq.getCustomerName());
    }

    @Transactional(readOnly = true)
    public PageResponse<RedFlagResponse> list(IdentityPrincipal actor, int page, int size,
                                              RedFlagStatus status, RedFlagSeverity severity) {
        Page<RedFlag> result = isAdmin(actor)
                ? redFlags.findFiltered(status, severity, PageRequest.of(page, size))
                : redFlags.findFilteredForUser(actor.userId(), status, severity, PageRequest.of(page, size));
        Map<Long, Rfq> rfqMap = rfqs.findAllById(
                result.getContent().stream().map(RedFlag::getRfqId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Rfq::getId, Function.identity()));
        return new PageResponse<>(
                result.getContent().stream().map(flag -> {
                    Rfq rfq = rfqMap.get(flag.getRfqId());
                    return RedFlagResponse.from(flag,
                            rfq == null ? null : rfq.getSourceRfqId(),
                            rfq == null ? null : rfq.getCustomerName());
                }).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public RedFlagResponse get(Long id, IdentityPrincipal actor) {
        RedFlag flag = findOrThrow(id);
        if (!isAdmin(actor)
                && !Objects.equals(flag.getRaisedBy(), actor.userId())
                && !Objects.equals(flag.getAssignedTo(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have access to this red flag");
        }
        Rfq rfq = rfqs.findById(flag.getRfqId()).orElse(null);
        return RedFlagResponse.from(flag,
                rfq == null ? null : rfq.getSourceRfqId(),
                rfq == null ? null : rfq.getCustomerName());
    }

    @Transactional
    public RedFlagResponse resolve(Long id, ResolveRedFlagRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        RedFlag flag = findOrThrow(id);
        if (flag.getStatus() != RedFlagStatus.OPEN) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Only open red flags can be resolved");
        }
        if ("RESOLVE".equals(request.resolution())) {
            flag.resolve(request.note().trim(), actor.userId());
        } else if ("DISMISS".equals(request.resolution())) {
            flag.dismiss(request.note().trim(), actor.userId());
        } else {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Resolution must be RESOLVE or DISMISS");
        }
        redFlags.save(flag);
        Rfq rfq = rfqs.findById(flag.getRfqId()).orElse(null);
        if (rfq != null) {
            appendEvent(rfq, "RED_FLAG_RESOLVED", actor, request.note().trim(),
                    Map.of("redFlagId", flag.getId(), "status", flag.getStatus().name()));
        }
        String rfqLabel = rfq == null ? flag.getRfqPublicId() : "RFQ-" + rfq.getSourceRfqId();
        if (flag.getRaisedBy() != null && !Objects.equals(flag.getRaisedBy(), actor.userId())) {
            notifications.send(flag.getRaisedBy(), NotificationType.RED_FLAG_RESOLVED,
                    "Red flag " + flag.getStatus().name().toLowerCase() + " on " + rfqLabel,
                    flag.getResolutionNote(), flag.getRfqPublicId());
        }
        if (flag.getAssignedTo() != null
                && !Objects.equals(flag.getAssignedTo(), actor.userId())
                && !Objects.equals(flag.getAssignedTo(), flag.getRaisedBy())) {
            notifications.send(flag.getAssignedTo(), NotificationType.RED_FLAG_RESOLVED,
                    "Red flag " + flag.getStatus().name().toLowerCase() + " on " + rfqLabel,
                    flag.getResolutionNote(), flag.getRfqPublicId());
        }
        return RedFlagResponse.from(flag,
                rfq == null ? null : rfq.getSourceRfqId(),
                rfq == null ? null : rfq.getCustomerName());
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return redFlags.countByStatus(RedFlagStatus.OPEN);
    }

    private RedFlag findOrThrow(Long id) {
        return redFlags.findById(id)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Red flag was not found"));
    }

    private boolean isAdmin(IdentityPrincipal actor) {
        return actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
    }

    private void requireAdmin(IdentityPrincipal actor) {
        if (!isAdmin(actor)) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can resolve red flags");
        }
    }

    private void appendEvent(Rfq rfq, String eventType, IdentityPrincipal actor, String reason, Map<String, ?> payload) {
        try {
            events.save(new RfqWorkflowEvent(rfq, eventType, actor.userId(),
                    mapper.writeValueAsString(actor.roles().stream().sorted().toList()),
                    null, null, reason, mapper.writeValueAsString(payload), MDC.get("correlationId")));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "Red flag audit event could not be recorded");
        }
    }
}
