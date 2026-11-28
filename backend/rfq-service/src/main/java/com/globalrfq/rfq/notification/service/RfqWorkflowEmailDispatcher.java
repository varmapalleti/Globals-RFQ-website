package com.globalrfq.rfq.notification.service;

import com.globalrfq.rfq.common.auth.IdentityAuthorizationClient;
import com.globalrfq.rfq.notification.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RfqWorkflowEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RfqWorkflowEmailDispatcher.class);

    private final IdentityAuthorizationClient identityClient;

    public RfqWorkflowEmailDispatcher(IdentityAuthorizationClient identityClient) {
        this.identityClient = identityClient;
    }

    @Async("rfqEmailExecutor")
    public void dispatch(
            Long recipientUserId,
            NotificationType type,
            String title,
            String message,
            String rfqPublicId) {
        if (recipientUserId == null || type == null) {
            return;
        }
        try {
            identityClient.sendWorkflowNotificationEmail(
                    recipientUserId,
                    type.name(),
                    title,
                    message,
                    rfqPublicId);
        } catch (Exception ex) {
            log.warn("Could not enqueue RFQ workflow email recipientUserId={} type={} reason={}",
                    recipientUserId, type, ex.getClass().getSimpleName());
        }
    }
}
