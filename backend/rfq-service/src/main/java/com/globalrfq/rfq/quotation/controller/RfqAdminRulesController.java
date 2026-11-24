package com.globalrfq.rfq.quotation.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.quotation.dto.AdminAuditResponse;
import com.globalrfq.rfq.quotation.dto.MandatoryFieldConfigRequest;
import com.globalrfq.rfq.quotation.dto.WorkflowPolicyRequest;
import com.globalrfq.rfq.quotation.service.QuotationWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rfq-rules")
public class RfqAdminRulesController {
    private final QuotationWorkflowService workflow;

    public RfqAdminRulesController(QuotationWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/mandatory-fields")
    public ApiResponse<List<String>> getMandatoryFields() {
        return ApiResponse.success("Mandatory quotation fields", workflow.getMandatoryFields(CurrentActor.require()), correlationId());
    }

    @PutMapping("/mandatory-fields")
    public ApiResponse<List<String>> setMandatoryFields(@Valid @RequestBody MandatoryFieldConfigRequest request) {
        return ApiResponse.success("Mandatory quotation fields updated",
                workflow.replaceMandatoryFields(request, CurrentActor.require()), correlationId());
    }

    @GetMapping("/rejection-limit")
    public ApiResponse<Map<String, Integer>> getRejectionLimit() {
        return ApiResponse.success("Reviewer rejection policy",
                Map.of("maxFormalRejections", workflow.getRejectionLimit(CurrentActor.require())), correlationId());
    }

    @PatchMapping("/rejection-limit")
    public ApiResponse<Map<String, Integer>> setRejectionLimit(@Valid @RequestBody WorkflowPolicyRequest request) {
        return ApiResponse.success("Reviewer rejection policy updated",
                Map.of("maxFormalRejections", workflow.changeRejectionLimit(request, CurrentActor.require())), correlationId());
    }

    @GetMapping("/audit")
    public ApiResponse<List<AdminAuditResponse>> audit() {
        return ApiResponse.success("RFQ configuration audit history", workflow.adminAudit(CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
