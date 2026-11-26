package com.globalrfq.rfq.export.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqLineItem;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.service.RfqQueryService;
import com.globalrfq.rfq.quotation.dto.QuotationSnapshot;
import com.globalrfq.rfq.quotation.entity.QuotationReviewDecision;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.repository.QuotationReviewDecisionRepository;
import com.globalrfq.rfq.quotation.repository.QuotationVersionRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfqExportServiceTest {

    @Mock private RfqRepository rfqs;
    @Mock private RfqQueryService queries;
    @Mock private QuotationVersionRepository versions;
    @Mock private QuotationReviewDecisionRepository reviews;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsCleanClientSheetWithV2xPartAndSeparatesInternalAuditSheet() throws Exception {
        RfqExportService service = new RfqExportService(rfqs, queries, versions, reviews, mapper);
        IdentityPrincipal admin = new IdentityPrincipal(1L, "admin@globalrfq.test", Set.of("ADMIN"));

        Rfq rfq = new Rfq(
                "CUSTOMER_SOFTWARE", "110627", "REF-110627", "VANESSA K BENTON",
                "V2X Systems Corporation", "V2X Systems Corporation",
                "Marty Barclay", "v2x.rfqadmin@procureline.com", "+1-719-555-0100",
                "Mosquito Fogger Parts",
                LocalDateTime.of(2026, 8, 6, 9, 0), "Asia/Kolkata",
                LocalDate.of(2026, 11, 20),
                "OAKLAND CROSS-DOCK\n8757 SAN LEANDRO ST\nOAKLAND, CA 94603",
                "https://v2x.procuredinc.com/60277",
                "TERMS OF PAYMENT: NET 60",
                mapper.writeValueAsString(java.util.Map.of("customerAddress", "PO Box 39710, Colorado Springs, CO 80949")),
                1L);
        rfq.addLineItem(new RfqLineItem(
                "1", 1, "BELT, V, DUST BIN, RUBBER", new BigDecimal("4"), "EA",
                "3030-09-V00-0119", "BUFFALO TURBINE", "1291",
                "Spare Parts", true, "High temp rubber", "{}"));

        String pubId = rfq.getPublicId();
        QuotationSnapshot snapshot = new QuotationSnapshot(
                "USD",
                List.of(new QuotationSnapshot.QuotedLineSnapshot(
                        "1", "BELT, V, DUST BIN, RUBBER", new BigDecimal("4"),
                        new BigDecimal("25.00"), new BigDecimal("100.00"), "EA",
                        "BUFFALO TURBINE", "1291", true, "14 days",
                        "1 Year OEM", "OEM direct", "https://internal-vendor.example/item/1291")),
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("120.00"), "14 days", "1 Year OEM", "FOB Origin");

        QuotationVersion version = mock(QuotationVersion.class, withSettings().lenient());
        when(version.getId()).thenReturn(99L);
        when(version.getVersionNumber()).thenReturn(1);
        when(version.getSubmittedByUserId()).thenReturn(2L);
        when(version.getSubmittedAt()).thenReturn(java.time.Instant.now());
        when(version.getQuotationJson()).thenReturn(mapper.writeValueAsString(snapshot));

        QuotationReviewDecision review = new QuotationReviewDecision(version, "APPROVED", 3L, "Verified");

        when(rfqs.findWithLineItemsByPublicId(pubId)).thenReturn(Optional.of(rfq));
        when(versions.findTopByRfqIdOrderByVersionNumberDesc(rfq.getId())).thenReturn(Optional.of(version));
        when(reviews.findByQuotationVersionId(99L)).thenReturn(Optional.of(review));

        var xlsxExport = service.export(pubId, null, "xlsx", admin);
        assertEquals("RFQ-110627.xlsx", xlsxExport.fileName());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxExport.bytes()))) {
            assertEquals("Client Quotation", workbook.getSheetAt(0).getSheetName());
            assertEquals("Internal Audit", workbook.getSheetAt(1).getSheetName());

            String clientText = sheetToText(workbook.getSheetAt(0));
            assertTrue(clientText.contains("V2X PART #"));
            assertTrue(clientText.contains("3030-09-V00-0119"));
            assertTrue(clientText.contains("PO Box 39710, Colorado Springs, CO 80949"));
            assertTrue(clientText.contains("TERMS OF PAYMENT: NET 60"));
            assertTrue(clientText.contains("https://v2x.procuredinc.com/60277"));
            assertTrue(clientText.contains("Grand Total (USD)"));
            assertFalse(clientText.contains(pubId), "Client sheet must not expose internal RFQ UUID");
            assertFalse(clientText.contains("https://internal-vendor.example"), "Client sheet must not expose internal vendor URL");
            assertFalse(clientText.contains("Submitted By User ID"), "Client sheet must not expose internal user IDs");

            String auditText = sheetToText(workbook.getSheetAt(1));
            assertTrue(auditText.contains(pubId));
            assertTrue(auditText.contains("Submitted By User ID"));
            assertTrue(auditText.contains("https://internal-vendor.example/item/1291"));
        }

        var pdfExport = service.export(pubId, null, "pdf", admin);
        String pdfHeader = new String(pdfExport.bytes(), 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", pdfHeader);
    }

    private String sheetToText(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (var row : sheet) {
            for (var cell : row) {
                sb.append(cell.toString()).append('\t');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
