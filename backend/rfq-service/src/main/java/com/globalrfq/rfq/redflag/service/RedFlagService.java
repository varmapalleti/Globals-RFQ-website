package com.globalrfq.rfq.redflag.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.redflag.dto.CreateRedFlagRequest;
import com.globalrfq.rfq.redflag.dto.RedFlagResponse;
import com.globalrfq.rfq.redflag.dto.ResolveRedFlagRequest;
import com.globalrfq.rfq.redflag.entity.RedFlag;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import com.globalrfq.rfq.redflag.repository.RedFlagRepository;
import com.globalrfq.rfq.intake.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedFlagService {
    private final RedFlagRepository redFlags;
    private final RfqRepository rfqs;

    public RedFlagService(RedFlagRepository redFlags, RfqRepository rfqs) {
        this.redFlags = redFlags;
        this.rfqs = rfqs;
    }

    @Transactional
    public RedFlagResponse create(CreateRedFlagRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        Rfq rfq = rfqs.findByPublicId(request.rfqPublicId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        RedFlag flag = new RedFlag(rfq.getId(), rfq.getPublicId(), request.severity(),
                request.reason(), actor.userId(), request.assignedTo());
        redFlags.save(flag);
        return RedFlagResponse.from(flag);
    }

    @Transactional(readOnly = true)
    public PageResponse<RedFlagResponse> list(IdentityPrincipal actor, int page, int size,
                                              RedFlagStatus status, RedFlagSeverity severity) {
        requireAdmin(actor);
        var result = redFlags.findFiltered(status, severity, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(RedFlagResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public RedFlagResponse get(Long id, IdentityPrincipal actor) {
        requireAdmin(actor);
        return RedFlagResponse.from(findOrThrow(id));
    }

    @Transactional
    public RedFlagResponse resolve(Long id, ResolveRedFlagRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        RedFlag flag = findOrThrow(id);
        if (flag.getStatus() != RedFlagStatus.OPEN) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Only open red flags can be resolved");
        }
        if ("RESOLVE".equals(request.resolution())) {
            flag.resolve(request.note(), actor.userId());
        } else if ("DISMISS".equals(request.resolution())) {
            flag.dismiss(request.note(), actor.userId());
        } else {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Resolution must be RESOLVE or DISMISS");
        }
        redFlags.save(flag);
        return RedFlagResponse.from(flag);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return redFlags.countByStatus(RedFlagStatus.OPEN);
    }

    private RedFlag findOrThrow(Long id) {
        return redFlags.findById(id)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Red flag was not found"));
    }

    private void requireAdmin(IdentityPrincipal actor) {
        if (!actor.roles().contains("ADMIN") && !actor.roles().contains("SUPER_ADMIN")) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can manage red flags");
        }
    }
}
