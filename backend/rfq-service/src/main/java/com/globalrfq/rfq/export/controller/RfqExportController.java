package com.globalrfq.rfq.export.controller;

import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.export.service.RfqExportService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/rfqs/{publicId}/export")
@Validated
public class RfqExportController {
    private final RfqExportService exports;

    public RfqExportController(RfqExportService exports) { this.exports = exports; }

    @GetMapping
    public ResponseEntity<byte[]> export(@PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String publicId,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) @Positive Long quotationVersionId) {
        var file = exports.export(publicId, quotationVersionId, format, CurrentActor.require());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
