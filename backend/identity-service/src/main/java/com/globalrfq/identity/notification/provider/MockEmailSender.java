package com.globalrfq.identity.notification.provider;

import com.globalrfq.identity.common.config.NotificationProperties;
import com.globalrfq.identity.common.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.email-provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    private final NotificationProperties props;

    public MockEmailSender(NotificationProperties props) {
        this.props = props;
    }

    @Override
    public String providerName() { return "MOCK"; }

    @Override
    public boolean isMock() { return true; }

    @Override
    public void send(EmailMessage message) {
        log.info("MOCK EMAIL to={} subject=\"{}\"", MaskingUtil.maskEmail(message.to()), message.subject());
        if (props.logSensitiveInMock()) {
            log.info("MOCK EMAIL BODY (dev only):\n{}", message.textBody());
        }
    }
}
