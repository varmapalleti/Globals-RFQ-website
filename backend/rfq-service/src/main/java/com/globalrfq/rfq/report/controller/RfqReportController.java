package com.globalrfq.rfq.report.controller;

import com.globalrfq.rfq.common.api.ApiResponse;
import com.globalrfq.rfq.common.api.CorrelationIdFilter;
import com.globalrfq.rfq.common.auth.CurrentActor;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.report.dto.RfqOperationalReportResponse;
import com.globalrfq.rfq.report.service.RfqReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/rfqs")
public class RfqReportController {
    private final RfqReportService reports;
    public RfqReportController(RfqReportService reports) { this.reports = reports; }

    @GetMapping("/summary")
    public ApiResponse<RfqOperationalReportResponse> summary(@RequestParam(required = false) RfqStatus status,
                                                              @RequestParam(required = false) LocalDate from,
                                                              @RequestParam(required = false) LocalDate through) {
        return ApiResponse.success("RFQ operational report summary", reports.summary(CurrentActor.require(), status, from, through),
                org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) RfqStatus status,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate through) {
        var file = reports.export(CurrentActor.require(), status, from, through, format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
