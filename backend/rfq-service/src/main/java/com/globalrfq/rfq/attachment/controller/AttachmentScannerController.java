package com.globalrfq.rfq.attachment.controller;

import com.globalrfq.rfq.attachment.dto.AttachmentScanResultRequest;
import com.globalrfq.rfq.attachment.dto.PendingAttachmentScanResponse;
import com.globalrfq.rfq.attachment.service.RfqAttachmentService;
import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/attachment-scanner")
@Validated
public class AttachmentScannerController {
    private final RfqAttachmentService attachments;

    public AttachmentScannerController(RfqAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/pending")
    public ApiResponse<List<PendingAttachmentScanResponse>> pending() {
        return ApiResponse.success("Attachments awaiting malware scanning", attachments.pendingScans(), correlationId());
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> content(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String attachmentId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(attachments.scannerContent(attachmentId));
    }

    @PostMapping("/attachments/{attachmentId}/result")
    public ApiResponse<Void> result(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String attachmentId,
                                    @Valid @RequestBody AttachmentScanResultRequest request) {
        attachments.recordScan(attachmentId, request);
        return ApiResponse.success("Attachment scan result recorded", null, correlationId());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
