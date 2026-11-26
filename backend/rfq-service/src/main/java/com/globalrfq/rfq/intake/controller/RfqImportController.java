package com.globalrfq.rfq.intake.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.intake.dto.RfqImportPreviewResponse;
import com.globalrfq.rfq.intake.dto.RfqImportConfirmRequest;
import com.globalrfq.rfq.intake.dto.RfqImportResultResponse;
import com.globalrfq.rfq.intake.service.RfqImportCoordinator;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rfqs/imports")
@Validated
public class RfqImportController {
    private final RfqImportCoordinator imports;

    public RfqImportController(RfqImportCoordinator imports) {
        this.imports = imports;
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RfqImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "CUSTOMER_SOFTWARE") @Size(max = 80) String sourceSystem,
            @RequestParam(required = false) @Size(max = 120) String sourceRfqId) {
        return ApiResponse.success("RFQ import preview. Review the field mappings and warnings before confirming.",
                imports.preview(file, sourceSystem, sourceRfqId, CurrentActor.require()), correlationId());
    }

    @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<RfqImportResultResponse> importAndStore(
            @Valid @RequestBody RfqImportConfirmRequest request) {
        return ApiResponse.success("RFQ imported with its original source attachment",
                imports.importAndStore(request, CurrentActor.require()), correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
