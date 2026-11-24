package com.globalrfq.rfq.attachment.controller;

import com.globalrfq.rfq.attachment.dto.RfqAttachmentResponse;
import com.globalrfq.rfq.attachment.entity.AttachmentCategory;
import com.globalrfq.rfq.attachment.service.RfqAttachmentService;
import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/rfqs/{publicId}/attachments")
@Validated
public class RfqAttachmentController {
    private final RfqAttachmentService attachments;

    public RfqAttachmentController(RfqAttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RfqAttachmentResponse> upload(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestParam AttachmentCategory category,
            @RequestParam(required = false) @Min(1) Integer lineNumber) {
        return ApiResponse.success("Attachment uploaded and queued for malware scanning",
                attachments.upload(publicId, file, category, lineNumber, CurrentActor.require()), correlationId());
    }

    @GetMapping
    public ApiResponse<List<RfqAttachmentResponse>> list(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId) {
        return ApiResponse.success("RFQ attachments", attachments.list(publicId, CurrentActor.require()), correlationId());
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String attachmentId) {
        var file = attachments.download(publicId, attachmentId, CurrentActor.require());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }

    private String correlationId() {
        return org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
