package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.config.RfqTimeProperties;
import com.globalrfq.rfq.intake.dto.RfqImportPreviewResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RfqDocumentParserTest {

    private final RfqDocumentParser parser = new RfqDocumentParser(new RfqTimeProperties());

    private byte[] createV2xWorkbook(boolean includePrNumber) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("REQUEST FOR QUOTE");
            sheet.createRow(1).createCell(0).setCellValue("V2X Systems Corporation");
            sheet.createRow(2).createCell(0).setCellValue("PO Box 39710");
            sheet.createRow(3).createCell(0).setCellValue("Accounts Payable Box 4");
            sheet.createRow(4).createCell(0).setCellValue("Colorado Springs, CO 80949");

            var prRow = sheet.createRow(6);
            prRow.createCell(0).setCellValue("PR #");
            if (includePrNumber) {
                prRow.createCell(1).setCellValue("110627");
            }
            prRow.createCell(3).setCellValue("VANESSA K BENTON");

            var dueRow = sheet.createRow(7);
            dueRow.createCell(0).setCellValue("Due Date & Time");
            dueRow.createCell(1).setCellValue("06-08-2026 09:00");

            var delRow = sheet.createRow(8);
            delRow.createCell(0).setCellValue("Requested Delivery Date");
            delRow.createCell(1).setCellValue("20-11-2026");

            var buyerRow = sheet.createRow(9);
            buyerRow.createCell(0).setCellValue("Buyer Name");
            buyerRow.createCell(1).setCellValue("Marty Barclay");

            var emailRow = sheet.createRow(10);
            emailRow.createCell(0).setCellValue("Buyer Email");
            emailRow.createCell(1).setCellValue("v2x.rfqadmin@procureline.com");

            var shipRow1 = sheet.createRow(12);
            shipRow1.createCell(0).setCellValue("Ship To");
            shipRow1.createCell(1).setCellValue("OAKLAND CROSS-DOCK");
            sheet.createRow(13).createCell(1).setCellValue("8757 SAN LEANDRO ST");
            sheet.createRow(14).createCell(1).setCellValue("OAKLAND, CA, 94603");
            sheet.createRow(15).createCell(1).setCellValue("USA");

            var header = sheet.createRow(17);
            String[] cols = {"ITEM #", "QTY", "UI", "V2X PART #", "DESCRIPTION", "MFG", "PN", "UP", "TOTAL"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            var item1 = sheet.createRow(18);
            item1.createCell(0).setCellValue("1");
            item1.createCell(1).setCellValue(4);
            item1.createCell(2).setCellValue("EA");
            item1.createCell(3).setCellValue("3030-09-V00-0119");
            item1.createCell(4).setCellValue("BELT, V, DUST BIN, RUBBER");
            item1.createCell(5).setCellValue("BUFFALO TURBINE");
            item1.createCell(6).setCellValue("1291");

            sheet.createRow(20).createCell(0).setCellValue(
                    "A competitive procurement resulting in a firm fixed price (FFP) contract.\nTERMS OF PAYMENT: NET 60");
            sheet.createRow(21).createCell(0).setCellValue("https://v2x.procuredinc.com/60277");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void parsesRealV2xWorkbookHeadersCustomerAddressDatesAndPartNumbers() throws Exception {
        byte[] bytes = createV2xWorkbook(true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "v2x-110627.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        RfqImportPreviewResponse preview = parser.parse(file, "CUSTOMER_SOFTWARE", null);
        var req = preview.normalizedRfq();

        assertEquals("110627", req.sourceRfqId());
        assertEquals("V2X Systems Corporation", req.customerName());
        assertEquals("VANESSA K BENTON", req.sourceQuotationManagerName());
        assertEquals(LocalDateTime.of(2026, 8, 6, 9, 0), req.dueAtLocal());
        assertEquals(LocalDate.of(2026, 11, 20), req.requestedDeliveryDate());
        assertEquals("Marty Barclay", req.buyerName());
        assertEquals("v2x.rfqadmin@procureline.com", req.buyerEmail());
        assertTrue(req.shipToText().contains("OAKLAND CROSS-DOCK"));
        assertTrue(req.shipToText().contains("8757 SAN LEANDRO ST"));
        assertTrue(req.shipToText().contains("USA"));
        assertEquals("https://v2x.procuredinc.com/60277", req.sourceUrl());
        assertTrue(req.termsAndConditions().contains("NET 60"));
        assertTrue(String.valueOf(req.sourceData().get("customerAddress")).contains("PO Box 39710"));

        assertEquals(1, req.lineItems().size());
        var line = req.lineItems().getFirst();
        assertEquals(new BigDecimal("4"), line.requestedQuantity());
        assertEquals("EA", line.unitOfMeasure());
        assertEquals("3030-09-V00-0119", line.customerPartNumber());
        assertEquals("BUFFALO TURBINE", line.requestedManufacturer());
        assertEquals("1291", line.requestedPartNumber());
    }

    @Test
    void warnsGracefullyWhenPrNumberIsMissingFromWorkbook() throws Exception {
        byte[] bytes = createV2xWorkbook(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "no-pr.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        RfqImportPreviewResponse preview = parser.parse(file, "CUSTOMER_SOFTWARE", null);

        assertEquals("", preview.normalizedRfq().sourceRfqId());
        assertEquals(1, preview.normalizedRfq().lineItems().size());
        assertTrue(preview.warnings().stream()
                .anyMatch(w -> w.toLowerCase().contains("pr / customer rfq # was not found")));
    }

    @Test
    void respectsExplicitPrOverrideWhenSupplied() throws Exception {
        byte[] bytes = createV2xWorkbook(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "override.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        RfqImportPreviewResponse preview = parser.parse(file, "CUSTOMER_SOFTWARE", "MANUAL-PR-99");
        assertEquals("MANUAL-PR-99", preview.normalizedRfq().sourceRfqId());
    }
}
