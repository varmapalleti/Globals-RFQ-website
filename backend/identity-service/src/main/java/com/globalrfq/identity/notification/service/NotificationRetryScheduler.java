package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.notification.model.NotificationStatus;
import com.globalrfq.identity.notification.repository.NotificationOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class NotificationRetryScheduler {
    private final NotificationOutboxRepository repository;
    private final NotificationDispatcher dispatcher;
    private final NotificationOutboxStore store;

    public NotificationRetryScheduler(NotificationOutboxRepository repository, NotificationDispatcher dispatcher,
                                       NotificationOutboxStore store) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${app.notification.retry-scan-delay-ms:30000}")
    public void dispatchDueNotifications() {
        Instant now = Instant.now();
        List<Long> ids = new ArrayList<>();
        ids.addAll(repository.findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtIsNullOrderByCreatedAtAsc(NotificationStatus.PENDING)
                .stream().map(row -> row.getId()).toList());
        ids.addAll(repository.findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtLessThanEqualAndAttemptCountLessThanOrderByScheduledAtAsc(
                        NotificationStatus.FAILED, now, 5).stream().map(row -> row.getId()).toList());
        repository.findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        NotificationStatus.PROCESSING, now).forEach(row -> {
                    if (row.getAttemptCount() >= 5) store.failExhausted(row.getId());
                    else dispatcher.dispatch(row.getId());
                });
        ids.stream().distinct().forEach(dispatcher::dispatch);
    }
}
