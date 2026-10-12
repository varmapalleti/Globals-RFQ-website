package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.common.config.NotificationProperties;
import com.globalrfq.identity.common.util.MaskingUtil;
import com.globalrfq.identity.notification.model.NotificationChannel;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationStatus;
import com.globalrfq.identity.notification.model.RenderedMessage;
import com.globalrfq.identity.notification.provider.EmailMessage;
import com.globalrfq.identity.notification.provider.EmailSender;
import com.globalrfq.identity.notification.provider.SmsMessage;
import com.globalrfq.identity.notification.provider.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationProperties props;
    private final TemplateRenderer renderer;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationOutboxStore store;

    public NotificationDispatcher(NotificationProperties props, TemplateRenderer renderer,
                                  EmailSender emailSender, SmsSender smsSender,
                                  NotificationOutboxStore store) {
        this.props = props;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.store = store;
    }

    @Async("notificationExecutor")
    public void dispatch(Long outboxId, NotificationRequest request) {
        try {
            RenderedMessage rendered = renderer.render(request.template(), request.variables());
            boolean mock;
            if (request.channel() == NotificationChannel.EMAIL) {
                String from = props.fromName() + " <" + props.fromEmail() + ">";
                emailSender.send(new EmailMessage(from, request.recipient(),
                        rendered.subject(), rendered.htmlBody(), rendered.textBody()));
                mock = emailSender.isMock();
            } else {
                smsSender.send(new SmsMessage(request.recipient(), rendered.textBody()));
                mock = smsSender.isMock();
            }
            store.markResult(outboxId, mock ? NotificationStatus.MOCK_SENT : NotificationStatus.SENT, null);
        } catch (Exception ex) {
            log.error("Notification {} to {} failed: {}", request.template(),
                    MaskingUtil.maskEmail(request.recipient()), ex.getClass().getSimpleName());
            store.markResult(outboxId, NotificationStatus.FAILED, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}