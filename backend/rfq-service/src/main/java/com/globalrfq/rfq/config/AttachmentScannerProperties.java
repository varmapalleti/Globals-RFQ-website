package com.globalrfq.rfq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.attachment-scanner")
public record AttachmentScannerProperties(String sharedToken) { }
