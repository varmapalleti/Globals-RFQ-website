package com.globalrfq.rfq.intake.dto;

import java.util.Map;

public record RfqDashboardSummaryResponse(long totalRfqs, Map<String, Long> statusCounts) { }
