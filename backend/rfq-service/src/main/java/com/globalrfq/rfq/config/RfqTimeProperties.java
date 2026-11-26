package com.globalrfq.rfq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.rfq")
public class RfqTimeProperties {
    private String defaultTimeZone = "Asia/Kolkata";

    public String getDefaultTimeZone() { return defaultTimeZone; }

    public void setDefaultTimeZone(String defaultTimeZone) {
        ZoneId.of(defaultTimeZone);
        this.defaultTimeZone = defaultTimeZone;
    }
}
