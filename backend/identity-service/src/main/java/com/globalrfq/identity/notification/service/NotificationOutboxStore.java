package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.common.logging.CorrelationIdFilter;
import com.globalrfq.identity.notification.entity.NotificationOutbox;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationStatus;
import com.globalrfq.identity.notification.repository.NotificationOutboxRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationOutboxStore {

    private static final String MASK = "***";

    private final NotificationOutboxRepository repository;

    public NotificationOutboxStore(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(NotificationRequest request, String providerName) {
        Map<String, Object> safeVariables = new LinkedHashMap<>();
        request.variables().forEach((key, value) ->
                safeVariables.put(key, request.sensitiveKeys().contains(key) ? MASK : value));

        NotificationOutbox row = new NotificationOutbox();
        row.setChannel(request.channel());
        row.setTemplateCode(request.template().name());
        row.setRecipient(request.recipient());
        row.setVariables(safeVariables);
        row.setProvider(providerName);
        row.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        return repository.save(row).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResult(Long id, NotificationStatus status, String failureReason) {
        repository.findById(id).ifPresent(row -> {
            row.setStatus(status);
            row.setAttemptCount(row.getAttemptCount() + 1);
            if (status == NotificationStatus.SENT || status == NotificationStatus.MOCK_SENT) {
                row.setSentAt(Instant.now());
            }
            if (failureReason != null) {
                row.setFailureReason(failureReason.length() > 500 ? failureReason.substring(0, 500) : failureReason);
            }
        });
    }
}