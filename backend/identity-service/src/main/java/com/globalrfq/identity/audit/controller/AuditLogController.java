package com.globalrfq.identity.audit.controller;

import com.globalrfq.identity.audit.entity.AuditEventType;
import com.globalrfq.identity.audit.dto.AuditLogResponse;
import com.globalrfq.identity.audit.service.AuditQueryService;
import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.user.dto.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-logs")
@Validated
public class AuditLogController {
    private final AuditQueryService service;

    public AuditLogController(AuditQueryService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Audit logs", service.search(principal, from, to, eventType, userId, page, size));
    }
}
