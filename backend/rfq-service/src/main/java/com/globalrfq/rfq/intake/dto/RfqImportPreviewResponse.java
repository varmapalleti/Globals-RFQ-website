package com.globalrfq.rfq.intake.dto;

import java.util.List;
import java.util.Map;
import java.time.Instant;

public record RfqImportPreviewResponse(String fileName, String detectedFormat,
                                        CreateRfqRequest normalizedRfq,
                                        Map<String, String> fieldMappings,
                                        List<String> warnings,
                                        String previewToken,
                                        Instant expiresAt) {
    public RfqImportPreviewResponse withStaging(String token, Instant expiry) {
        return new RfqImportPreviewResponse(fileName, detectedFormat, normalizedRfq, fieldMappings, warnings, token, expiry);
    }
}
