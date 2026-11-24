package com.globalrfq.rfq.quotation.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.quotation.dto.QuotationDraftRequest;
import com.globalrfq.rfq.quotation.dto.QuotationDraftResponse;
import com.globalrfq.rfq.quotation.dto.QuotationVersionResponse;
import com.globalrfq.rfq.quotation.dto.ReviewRequest;
import com.globalrfq.rfq.quotation.service.QuotationWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rfqs/{publicId}")
@Validated
public class QuotationController {
    private final QuotationWorkflowService workflow;

    public QuotationController(QuotationWorkflowService workflow) {
        this.workflow = workflow;
    }

    @PutMapping("/quotation-draft")
    public ApiResponse<QuotationDraftResponse> saveDraft(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @Valid @RequestBody QuotationDraftRequest request) {
        return ApiResponse.success("Quotation draft saved", workflow.saveDraft(publicId, request, CurrentActor.require()), correlationId());
    }

    @GetMapping("/quotation-draft")
    public ApiResponse<QuotationDraftResponse> getDraft(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("Quotation draft", workflow.getDraft(publicId, CurrentActor.require()), correlationId());
    }

    @PostMapping("/quotation-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuotationVersionResponse> submit(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}") String idempotencyKey,
            @Valid @RequestBody QuotationDraftRequest request) {
        return ApiResponse.success("Quotation submitted for review",
                workflow.submit(publicId, idempotencyKey, request, CurrentActor.require()), correlationId());
    }

    @GetMapping("/quotation-versions")
    public ApiResponse<List<QuotationVersionResponse>> listVersions(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("Quotation versions", workflow.listVersions(publicId, CurrentActor.require()), correlationId());
    }

    @GetMapping("/quotation-versions/{versionId}")
    public ApiResponse<QuotationVersionResponse> getVersion(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @PathVariable @Positive Long versionId) {
        return ApiResponse.success("Quotation version", workflow.getVersion(publicId, versionId, CurrentActor.require()), correlationId());
    }

    @PostMapping("/quotation-versions/{versionId}/review")
    public ApiResponse<QuotationVersionResponse> review(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @PathVariable @Positive Long versionId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success("Reviewer decision recorded",
                workflow.review(publicId, versionId, request, CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
