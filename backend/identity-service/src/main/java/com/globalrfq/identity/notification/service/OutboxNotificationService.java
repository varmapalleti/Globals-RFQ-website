package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.notification.model.NotificationChannel;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.provider.EmailSender;
import com.globalrfq.identity.notification.provider.SmsSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OutboxNotificationService implements NotificationService {

    private final TemplateRenderer renderer;
    private final NotificationOutboxStore store;
    private final NotificationDispatcher dispatcher;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public OutboxNotificationService(TemplateRenderer renderer, NotificationOutboxStore store,
                                     NotificationDispatcher dispatcher,
                                     EmailSender emailSender, SmsSender smsSender) {
        this.renderer = renderer;
        this.store = store;
        this.dispatcher = dispatcher;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    @Override
    public void send(NotificationRequest request) {
        if (request.recipient() == null || request.recipient().isBlank()) {
            throw new IllegalArgumentException("Notification recipient is required");
        }
        if (request.template().getChannel() != request.channel()) {
            throw new IllegalArgumentException("Template does not support channel " + request.channel());
        }
        renderer.render(request.template(), request.variables());

        String provider = request.channel() == NotificationChannel.EMAIL
                ? emailSender.providerName() : smsSender.providerName();
        Long outboxId = store.create(request, provider);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatch(outboxId, request);
                }
            });
        } else {
            dispatcher.dispatch(outboxId, request);
        }
    }
}