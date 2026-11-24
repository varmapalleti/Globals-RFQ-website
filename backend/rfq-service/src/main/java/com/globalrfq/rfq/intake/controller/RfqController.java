package com.globalrfq.rfq.intake.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.intake.dto.AssignmentRequest;
import com.globalrfq.rfq.intake.dto.AdminWorkflowResolutionRequest;
import com.globalrfq.rfq.intake.dto.CreateRfqRequest;
import com.globalrfq.rfq.intake.dto.CustomerDecisionRequest;
import com.globalrfq.rfq.intake.dto.CustomerSubmissionRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentCreateRequest;
import com.globalrfq.rfq.intake.dto.ReviewerReassignmentResolutionRequest;
import com.globalrfq.rfq.quotation.dto.ReviewerDelegationRequest;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.intake.dto.RfqResponse;
import com.globalrfq.rfq.intake.dto.RfqSummaryResponse;
import com.globalrfq.rfq.intake.dto.RfqDashboardSummaryResponse;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.service.RfqAssignmentCoordinator;
import com.globalrfq.rfq.intake.service.RfqCommandService;
import com.globalrfq.rfq.intake.service.RfqQueryService;
import com.globalrfq.rfq.intake.service.ReviewerReassignmentService;
import com.globalrfq.rfq.workflow.dto.WorkflowEventResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rfqs")
@Validated
public class RfqController {
    private final RfqCommandService commands;
    private final RfqQueryService queries;
    private final RfqAssignmentCoordinator assignmentCoordinator;
    private final ReviewerReassignmentService reviewerReassignments;

    public RfqController(RfqCommandService commands, RfqQueryService queries,
                         RfqAssignmentCoordinator assignmentCoordinator,
                         ReviewerReassignmentService reviewerReassignments) {
        this.commands = commands;
        this.queries = queries;
        this.assignmentCoordinator = assignmentCoordinator;
        this.reviewerReassignments = reviewerReassignments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RfqResponse> create(@Valid @RequestBody CreateRfqRequest request) {
        return ApiResponse.success("RFQ imported", commands.create(request, CurrentActor.require()), correlationId());
    }

    @GetMapping
    public ApiResponse<PageResponse<RfqSummaryResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 120) String sourceRfqId,
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(required = false) RfqStatus status,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return ApiResponse.success("RFQs",
                queries.list(CurrentActor.require(), page, size, sourceRfqId, search, status, sort), correlationId());
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<RfqDashboardSummaryResponse> dashboardSummary() {
        return ApiResponse.success("Role-scoped RFQ dashboard summary",
                queries.dashboardSummary(CurrentActor.require()), correlationId());
    }

    @GetMapping("/{publicId}")
    public ApiResponse<RfqResponse> get(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("RFQ", queries.get(publicId, CurrentActor.require()), correlationId());
    }

    @GetMapping("/{publicId}/history")
    public ApiResponse<List<WorkflowEventResponse>> history(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("RFQ workflow history", queries.history(publicId, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/assignments")
    public ApiResponse<RfqResponse> assign(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                           @Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.success("RFQ assigned", assignmentCoordinator.assign(publicId, request, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/reviewer-reassignment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<com.globalrfq.rfq.intake.dto.ReviewerReassignmentResponse> requestReviewerReassignment(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}") String idempotencyKey,
            @Valid @RequestBody ReviewerReassignmentCreateRequest request) {
        return ApiResponse.success("Reviewer reassignment request recorded",
                reviewerReassignments.create(publicId, request, idempotencyKey, CurrentActor.require()), correlationId());
    }

    @GetMapping("/{publicId}/reviewer-reassignment-requests")
    public ApiResponse<List<com.globalrfq.rfq.intake.dto.ReviewerReassignmentResponse>> reviewerReassignmentRequests(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("Reviewer reassignment requests",
                reviewerReassignments.list(publicId, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/reviewer-reassignment-requests/{requestId}/resolve")
    public ApiResponse<com.globalrfq.rfq.intake.dto.ReviewerReassignmentResponse> resolveReviewerReassignment(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @PathVariable @Positive Long requestId,
            @Valid @RequestBody ReviewerReassignmentResolutionRequest request) {
        return ApiResponse.success("Reviewer reassignment request resolved",
                reviewerReassignments.resolve(publicId, requestId, request, CurrentActor.require()), correlationId());
    }

    @GetMapping("/{publicId}/reviewer-assignment-delegation")
    public ApiResponse<Map<String, Boolean>> reviewerAssignmentDelegation(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("RFQ reviewer assignment delegation",
                Map.of("quotationManagerCanAssignReviewer", commands.reviewerAssignmentDelegation(publicId, CurrentActor.require())),
                correlationId());
    }

    @PatchMapping("/{publicId}/reviewer-assignment-delegation")
    public ApiResponse<Map<String, Boolean>> setReviewerAssignmentDelegation(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @Valid @RequestBody ReviewerDelegationRequest request) {
        return ApiResponse.success("RFQ reviewer assignment delegation updated",
                Map.of("quotationManagerCanAssignReviewer", commands.updateReviewerAssignmentDelegation(publicId,
                        request.quotationManagerCanAssignReviewer(), request.reason(), CurrentActor.require())),
                correlationId());
    }

    @PostMapping("/{publicId}/customer-submissions")
    public ApiResponse<RfqResponse> recordCustomerSubmission(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                                              @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}") String idempotencyKey,
                                                              @Valid @RequestBody CustomerSubmissionRequest request) {
        return ApiResponse.success("Customer submission recorded", commands.recordCustomerSubmission(
                publicId, request, idempotencyKey, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/customer-decision")
    public ApiResponse<RfqResponse> recordCustomerDecision(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                                            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}") String idempotencyKey,
                                                            @Valid @RequestBody CustomerDecisionRequest request) {
        return ApiResponse.success("Customer decision recorded", commands.recordCustomerDecision(
                publicId, request, idempotencyKey, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/escalation-resolution")
    public ApiResponse<RfqResponse> resolveEscalation(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                                      @Valid @RequestBody AdminWorkflowResolutionRequest request) {
        return ApiResponse.success("RFQ escalation resolved", commands.resolveEscalation(publicId, request, CurrentActor.require()), correlationId());
    }

    @PostMapping("/{publicId}/customer-rejection-resolution")
    public ApiResponse<RfqResponse> resolveCustomerRejection(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                                              @Valid @RequestBody AdminWorkflowResolutionRequest request) {
        return ApiResponse.success("Customer rejection resolved", commands.resolveCustomerRejection(publicId, request, CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
