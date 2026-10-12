package com.globalrfq.identity.audit.service;

import com.globalrfq.identity.audit.entity.SecurityAuditLog;
import com.globalrfq.identity.audit.repository.SecurityAuditLogRepository;
import com.globalrfq.identity.common.logging.CorrelationIdFilter;
import com.globalrfq.identity.common.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class DbAuditService implements AuditService {

    private final SecurityAuditLogRepository repository;

    public DbAuditService(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setEventType(event.eventType());
        log.setOutcome(event.outcome());
        log.setAction(event.action());
        log.setActorUserId(event.actorUserId());
        log.setTargetUserId(event.targetUserId());
        log.setEntityType("USER");
        log.setEntityId(event.targetUserId() == null ? null : String.valueOf(event.targetUserId()));
        log.setMetadata(event.metadata());
        log.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ClientInfo client = ClientInfo.from(request);
            log.setIpAddress(client.ipAddress());
            log.setUserAgent(client.userAgent());
        }
        repository.save(log);
    }
}
