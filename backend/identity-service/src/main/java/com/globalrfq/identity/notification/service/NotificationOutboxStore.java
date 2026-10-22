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

    private final NotificationOutboxRepository repository;
    private final OutboxPayloadCipher cipher;

    public NotificationOutboxStore(NotificationOutboxRepository repository, OutboxPayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional
    public Long create(NotificationRequest request, String providerName) {
        Map<String, Object> encryptedVariables = new LinkedHashMap<>();
        request.variables().forEach((key, value) -> encryptedVariables.put(key, cipher.encrypt(value)));

        NotificationOutbox row = new NotificationOutbox();
        row.setChannel(request.channel());
        row.setTemplateCode(request.template().name());
        row.setRecipient(cipher.encrypt(request.recipient()));
        row.setVariables(encryptedVariables);
        row.setPayloadEncrypted(true);
        row.setProvider(providerName);
        row.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        return repository.save(row).getId();
    }

    @Transactional(readOnly = true)
    public NotificationRequest loadRequest(Long id) {
        NotificationOutbox row = repository.findById(id).orElseThrow();
        if (!row.isPayloadEncrypted()) throw new IllegalStateException("Notification payload was retired");
        Map<String, String> variables = new LinkedHashMap<>();
        row.getVariables().forEach((key, value) -> variables.put(key, cipher.decrypt(String.valueOf(value))));
        return new NotificationRequest(row.getChannel(),
                com.globalrfq.identity.notification.model.NotificationTemplate.valueOf(row.getTemplateCode()),
                cipher.decrypt(row.getRecipient()), variables, java.util.Set.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long id) {
        Instant now = Instant.now();
        return repository.claim(id, now, now.plusSeconds(600)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExhausted(Long id) {
        repository.failExhausted(id, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResult(Long id, NotificationStatus status, String failureReason) {
        repository.findById(id).ifPresent(row -> {
            row.setStatus(status);
            if (status == NotificationStatus.SENT || status == NotificationStatus.MOCK_SENT) {
                row.setSentAt(Instant.now());
            }
            if (failureReason != null) {
                row.setFailureReason(failureReason.length() > 500 ? failureReason.substring(0, 500) : failureReason);
                if (row.getAttemptCount() >= 5) {
                    row.setScheduledAt(null);
                } else {
                    long delaySeconds = Math.min(1800, 30L << Math.min(row.getAttemptCount(), 6));
                    row.setScheduledAt(Instant.now().plusSeconds(delaySeconds));
                }
            } else {
                row.setScheduledAt(null);
                row.setFailureReason(null);
            }
        });
    }
}
