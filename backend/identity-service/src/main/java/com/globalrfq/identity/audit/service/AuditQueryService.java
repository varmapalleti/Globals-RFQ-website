package com.globalrfq.identity.audit.service;

import com.globalrfq.identity.audit.dto.AuditLogResponse;
import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.repository.SecurityAuditLogRepository;
import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.user.dto.PageResponse;
import com.globalrfq.identity.user.service.UserAccessPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditQueryService {
    private final SecurityAuditLogRepository repository;
    private final UserAccessPolicy policy;
    private final AuditService audit;

    public AuditQueryService(SecurityAuditLogRepository repository, UserAccessPolicy policy, AuditService audit) {
        this.repository = repository;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuthenticatedUser principal, Instant from, Instant to,
                                                  AuditEventType eventType, Long userId, int page, int size) {
        policy.assertSuperAdmin(policy.actor(principal));
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The from date must be before the to date");
        }
        Specification<com.globalrfq.identity.audit.entity.SecurityAuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        if (eventType != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), eventType));
        if (userId != null) spec = spec.and((root, query, cb) -> cb.or(
                cb.equal(root.get("actorUserId"), userId), cb.equal(root.get("targetUserId"), userId)));
        Page<com.globalrfq.identity.audit.entity.SecurityAuditLog> rows = repository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Page<AuditLogResponse> response = rows.map(AuditLogResponse::from);
        audit.record(AuditEvent.of(com.globalrfq.identity.audit.entity.AuditEventType.AUDIT_LOGS_VIEWED,
                com.globalrfq.identity.audit.entity.AuditOutcome.SUCCESS, "SEARCH_AUDIT_LOGS",
                principal.id(), null, null));
        return PageResponse.from(response);
    }
}
