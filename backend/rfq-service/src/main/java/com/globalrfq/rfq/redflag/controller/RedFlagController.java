package com.globalrfq.rfq.redflag.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.redflag.dto.CreateRedFlagRequest;
import com.globalrfq.rfq.redflag.dto.RedFlagResponse;
import com.globalrfq.rfq.redflag.dto.ResolveRedFlagRequest;
import com.globalrfq.rfq.redflag.entity.RedFlagSeverity;
import com.globalrfq.rfq.redflag.entity.RedFlagStatus;
import com.globalrfq.rfq.redflag.service.RedFlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/red-flags")
@Validated
public class RedFlagController {
    private final RedFlagService service;

    public RedFlagController(RedFlagService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RedFlagResponse> create(@Valid @RequestBody CreateRedFlagRequest request) {
        return ApiResponse.success("Red flag created", service.create(request, CurrentActor.require()), correlationId());
    }

    @GetMapping
    public ApiResponse<PageResponse<RedFlagResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) RedFlagStatus status,
            @RequestParam(required = false) RedFlagSeverity severity) {
        return ApiResponse.success("Red flags", service.list(CurrentActor.require(), page, size, status, severity), correlationId());
    }

    @GetMapping("/{id}")
    public ApiResponse<RedFlagResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Red flag", service.get(id, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<RedFlagResponse> resolve(@PathVariable Long id,
                                                 @Valid @RequestBody ResolveRedFlagRequest request) {
        return ApiResponse.success("Red flag resolved", service.resolve(id, request, CurrentActor.require()), correlationId());
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() {
        return ApiResponse.success("Red flags summary", Map.of("openCount", service.countOpen()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
