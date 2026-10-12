package com.globalrfq.identity.notification.provider;

import com.globalrfq.identity.common.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.sms-provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public String providerName() { return "MOCK"; }

    @Override
    public boolean isMock() { return true; }

    @Override
    public void send(SmsMessage message) {
        log.info("MOCK SMS to={}", MaskingUtil.maskPhone(message.to()));
    }
}