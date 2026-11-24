package com.globalrfq.rfq.report.dto;

import com.globalrfq.rfq.intake.entity.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RfqOperationalReportResponse(
        Instant generatedAt,
        LocalDate createdFrom,
        LocalDate createdThrough,
        RfqStatus statusFilter,
        long totalRfqs,
        Map<String, Long> statusCounts,
        Map<String, Long> customerDecisionCounts,
        List<AssignmentWorkload> activeAssignmentWorkload
) {
    public record AssignmentWorkload(String role, Long identityUserId, Long activeRfqs) { }
}
