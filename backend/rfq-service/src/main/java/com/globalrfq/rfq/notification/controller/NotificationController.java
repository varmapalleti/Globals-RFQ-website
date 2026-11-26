package com.globalrfq.rfq.notification.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.notification.dto.NotificationResponse;
import com.globalrfq.rfq.notification.service.InAppNotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {
    private final InAppNotificationService service;

    public NotificationController(InAppNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Notifications",
                service.listForCurrentUser(CurrentActor.require(), page, size), correlationId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success("Unread count",
                service.unreadCount(CurrentActor.require()), correlationId());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.success("Notification marked as read",
                service.markRead(id, CurrentActor.require()), correlationId());
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.success("All notifications marked as read",
                service.markAllRead(CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
