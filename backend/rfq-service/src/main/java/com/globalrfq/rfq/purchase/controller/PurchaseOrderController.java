package com.globalrfq.rfq.purchase.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.purchase.dto.CreatePurchaseOrderRequest;
import com.globalrfq.rfq.purchase.dto.PurchaseOrderResponse;
import com.globalrfq.rfq.purchase.dto.UpdatePurchaseOrderStatusRequest;
import com.globalrfq.rfq.purchase.entity.PurchaseOrderStatus;
import com.globalrfq.rfq.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
@Validated
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ApiResponse.success("Purchase order created", service.create(request, CurrentActor.require()), correlationId());
    }

    @GetMapping
    public ApiResponse<PageResponse<PurchaseOrderResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PurchaseOrderStatus status) {
        return ApiResponse.success("Purchase orders", service.list(CurrentActor.require(), page, size, status), correlationId());
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Purchase order", service.get(id, CurrentActor.require()), correlationId());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PurchaseOrderResponse> updateStatus(@PathVariable Long id,
                                                            @Valid @RequestBody UpdatePurchaseOrderStatusRequest request) {
        return ApiResponse.success("Purchase order status updated", service.updateStatus(id, request, CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
