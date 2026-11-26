package com.globalrfq.rfq.export.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.service.RfqQueryService;
import com.globalrfq.rfq.quotation.dto.QuotationSnapshot;
import com.globalrfq.rfq.quotation.entity.QuotationReviewDecision;
import com.globalrfq.rfq.quotation.entity.QuotationVersion;
import com.globalrfq.rfq.quotation.repository.QuotationReviewDecisionRepository;
import com.globalrfq.rfq.quotation.repository.QuotationVersionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RfqExportService {
    private static final List<String> CLIENT_HEADERS = List.of(
            "ITEM #", "QTY", "UI", "V2X PART #", "DESCRIPTION", "MFG", "PN",
            "UP", "TOTAL", "FREIGHT", "EDD", "WARRANTY", "NOTES");
    private static final List<String> INTERNAL_AUDIT_HEADERS = List.of(
            "ITEM #", "QTY", "UI", "CATEGORY", "TAA REQUIRED", "V2X PART #", "REQUESTED MANUFACTURER",
            "REQUESTED PART / MODEL", "DESCRIPTION", "TECHNICAL SPECIFICATIONS", "OFFERED MANUFACTURER",
            "OFFERED PART / MODEL", "TAA COMPLIANT", "UNIT PRICE", "LINE TOTAL", "FREIGHT",
            "DELIVERY TIMELINE", "WARRANTY", "NOTE", "URL", "SOURCE LINE ID");

    private final RfqRepository rfqs;
    private final RfqQueryService queries;
    private final QuotationVersionRepository versions;
    private final QuotationReviewDecisionRepository reviews;
    private final ObjectMapper mapper;

    public RfqExportService(RfqRepository rfqs, RfqQueryService queries,
                            QuotationVersionRepository versions,
                            QuotationReviewDecisionRepository reviews, ObjectMapper mapper) {
        this.rfqs = rfqs;
        this.queries = queries;
        this.versions = versions;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ExportFile export(String publicId, Long versionId, String format, IdentityPrincipal actor) {
        queries.get(publicId, actor);
        Rfq rfq = rfqs.findWithLineItemsByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
        QuotationVersion version = versionId == null
                ? versions.findTopByRfqIdOrderByVersionNumberDesc(rfq.getId()).orElse(null)
                : versions.findByRfqIdAndId(rfq.getId(), versionId)
                    .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Quotation version was not found for this RFQ"));
        QuotationSnapshot quote = version == null ? null : readSnapshot(version.getQuotationJson());
        QuotationReviewDecision review = version == null ? null : reviews.findByQuotationVersionId(version.getId()).orElse(null);
        String quoteDecision = version == null ? "" : review == null ? "PENDING_REVIEW"
                : review.getDecision() + " by user " + review.getReviewerUserId();
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "xlsx" -> new ExportFile(fileName(rfq, "xlsx"),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(rfq, quote, version, review, quoteDecision));
            case "pdf" -> new ExportFile(fileName(rfq, "pdf"), "application/pdf", pdf(rfq, quote, version, review, quoteDecision));
            default -> throw new RfqException(RfqErrorCode.BAD_REQUEST, "Export format must be xlsx or pdf");
        };
    }

    private byte[] xlsx(Rfq rfq, QuotationSnapshot quote, QuotationVersion version,
                        QuotationReviewDecision review, String quoteDecision) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            CellStyle title = workbook.createCellStyle();
            Font titleFont = workbook.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 15);
            title.setFont(titleFont); title.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex()); title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle heading = workbook.createCellStyle();
            Font headingFont = workbook.createFont(); headingFont.setBold(true); headingFont.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(headingFont); heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            heading.setWrapText(true);
            CellStyle label = workbook.createCellStyle();
            Font labelFont = workbook.createFont(); labelFont.setBold(true); label.setFont(labelFont);
            CellStyle wrapped = workbook.createCellStyle(); wrapped.setWrapText(true); wrapped.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            String customerAddress = readCustomerAddress(rfq);
            String due = rfq.getDueAtLocal() == null ? "" : rfq.getDueAtLocal().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                    + (rfq.getDueTimeZone() == null ? "" : " " + rfq.getDueTimeZone());
            String deliveryDate = rfq.getRequestedDeliveryDate() == null ? "" : rfq.getRequestedDeliveryDate().toString();

            var clientSheet = workbook.createSheet("Client Quotation");
            clientSheet.createRow(0).createCell(0).setCellValue("REQUEST FOR QUOTE");
            clientSheet.getRow(0).getCell(0).setCellStyle(title);
            int clientRow = 2;
            putField(clientSheet, clientRow++, "Customer", rfq.getCustomerName(), label);
            if (!blank(rfq.getCustomerCompany()) && !rfq.getCustomerCompany().equals(rfq.getCustomerName())) {
                putField(clientSheet, clientRow++, "Customer Company", rfq.getCustomerCompany(), label);
            }
            if (!blank(customerAddress)) {
                putField(clientSheet, clientRow++, "Customer Address", customerAddress, label);
            }
            putField(clientSheet, clientRow++, "PR #", rfq.getSourceRfqId(), label);
            if (!blank(rfq.getSourceQuotationManagerName())) {
                putField(clientSheet, clientRow++, "Quotation Contact", rfq.getSourceQuotationManagerName(), label);
            }
            putField(clientSheet, clientRow++, "Due Date & Time", due, label);
            putField(clientSheet, clientRow++, "Requested Delivery Date", deliveryDate, label);
            putField(clientSheet, clientRow++, "Buyer Name", rfq.getBuyerName(), label);
            putField(clientSheet, clientRow++, "Buyer Email", rfq.getBuyerEmail(), label);
            if (!blank(rfq.getBuyerPhone())) {
                putField(clientSheet, clientRow++, "Buyer Phone", rfq.getBuyerPhone(), label);
            }
            putField(clientSheet, clientRow++, "Ship To", rfq.getShipToText(), label);
            if (!blank(rfq.getRequestTitle())) {
                putField(clientSheet, clientRow++, "Request Title", rfq.getRequestTitle(), label);
            }
            putField(clientSheet, clientRow++, "Currency", quote == null ? "" : quote.currency(), label);

            clientRow++;
            var headers = clientSheet.createRow(clientRow++);
            for (int i = 0; i < CLIENT_HEADERS.size(); i++) {
                headers.createCell(i).setCellValue(CLIENT_HEADERS.get(i));
                headers.getCell(i).setCellStyle(heading);
            }
            List<QuotationSnapshot.QuotedLineSnapshot> quoteLines = quote == null ? List.of() : quote.items();
            Map<String, List<QuotationSnapshot.QuotedLineSnapshot>> bySource = new LinkedHashMap<>();
            quoteLines.forEach(item -> bySource.computeIfAbsent(item.sourceLineId(), ignored -> new ArrayList<>()).add(item));
            for (var item : rfq.getLineItems()) {
                List<QuotationSnapshot.QuotedLineSnapshot> mapped = bySource.getOrDefault(item.getSourceLineId(), List.of());
                if (mapped.isEmpty()) {
                    writeClientRow(clientSheet.createRow(clientRow++), item.getLineNumber(), item.getRequestedQuantity(),
                            item.getUnitOfMeasure(), item.getCustomerPartNumber(), item.getDescription(),
                            item.getRequestedManufacturer(), item.getRequestedPartNumber(), null, quote, wrapped);
                } else {
                    for (var quoted : mapped) {
                        writeClientRow(clientSheet.createRow(clientRow++), item.getLineNumber(), item.getRequestedQuantity(),
                                item.getUnitOfMeasure(), item.getCustomerPartNumber(), item.getDescription(),
                                item.getRequestedManufacturer(), item.getRequestedPartNumber(), quoted, quote, wrapped);
                    }
                }
            }
            if (quote != null) {
                clientRow++;
                putField(clientSheet, clientRow++, "Subtotal", amount(quote.subtotal()), label);
                putField(clientSheet, clientRow++, "Freight", amount(quote.freight()), label);
                if (quote.otherCharges() != null && quote.otherCharges().signum() != 0) {
                    putField(clientSheet, clientRow++, "Other Charges", amount(quote.otherCharges()), label);
                }
                if (quote.discount() != null && quote.discount().signum() != 0) {
                    putField(clientSheet, clientRow++, "Discount", amount(quote.discount()), label);
                }
                if (quote.taxAmount() != null && quote.taxAmount().signum() != 0) {
                    putField(clientSheet, clientRow++, "Tax Amount", amount(quote.taxAmount()), label);
                }
                putField(clientSheet, clientRow++, "Grand Total (" + safe(quote.currency()) + ")", amount(quote.grandTotal()), label);
                if (!blank(quote.notes())) {
                    putField(clientSheet, clientRow++, "Quotation Notes", quote.notes(), label);
                }
            }
            if (!blank(rfq.getTermsAndConditions())) {
                clientRow++;
                putField(clientSheet, clientRow++, "Terms and Conditions",
                        excelTextOrLongTextReference(rfq.getTermsAndConditions(), "TERMS_AND_CONDITIONS"), label);
            }
            if (!blank(rfq.getSourceUrl())) {
                putField(clientSheet, clientRow++, "Bid Submission URL", rfq.getSourceUrl(), label);
            }
            int[] clientWidths = {10, 10, 10, 24, 58, 26, 24, 15, 16, 14, 24, 24, 36};
            setColumnWidths(clientSheet, clientWidths);
            clientSheet.setColumnWidth(0, 24 * 256);
            clientSheet.setColumnWidth(1, 38 * 256);

            var auditSheet = workbook.createSheet("Internal Audit");
            auditSheet.createRow(0).createCell(0).setCellValue("Internal Audit & Governance — " + (version == null ? "Draft" : "V" + version.getVersionNumber()));
            auditSheet.getRow(0).getCell(0).setCellStyle(title);
            putField(auditSheet, 2, "Internal RFQ ID", rfq.getPublicId(), label);
            putField(auditSheet, 3, "Source System", rfq.getSourceSystem(), label);
            putField(auditSheet, 4, "PR / Source RFQ #", rfq.getSourceRfqId(), label);
            putField(auditSheet, 5, "Source Reference / Event ID", rfq.getSourceReference(), label);
            putField(auditSheet, 6, "Status", rfq.getStatus().name(), label);
            putField(auditSheet, 7, "Customer Decision", rfq.getCustomerDecision().name(), label);
            putField(auditSheet, 8, "Quotation Manager Hint", rfq.getSourceQuotationManagerName(), label);
            putField(auditSheet, 9, "Quotation Version Number", version == null ? "" : String.valueOf(version.getVersionNumber()), label);
            putField(auditSheet, 10, "Quotation Version ID", version == null ? "" : String.valueOf(version.getId()), label);
            putField(auditSheet, 11, "Submitted By User ID", version == null ? "" : String.valueOf(version.getSubmittedByUserId()), label);
            putField(auditSheet, 12, "Submitted At (UTC)", version == null ? "" : version.getSubmittedAt().toString(), label);
            putField(auditSheet, 13, "Review Decision", quoteDecision, label);
            putField(auditSheet, 14, "Reviewer User ID", review == null ? "" : String.valueOf(review.getReviewerUserId()), label);
            putField(auditSheet, 15, "Review Reason", review == null ? "" : review.getReason(), label);
            putField(auditSheet, 16, "Currency", quote == null ? "" : quote.currency(), label);
            putField(auditSheet, 17, "Subtotal", quote == null ? "" : amount(quote.subtotal()), label);
            putField(auditSheet, 18, "Freight", quote == null ? "" : amount(quote.freight()), label);
            putField(auditSheet, 19, "Other Charges", quote == null ? "" : amount(quote.otherCharges()), label);
            putField(auditSheet, 20, "Discount", quote == null ? "" : amount(quote.discount()), label);
            putField(auditSheet, 21, "Tax Amount", quote == null ? "" : amount(quote.taxAmount()), label);
            putField(auditSheet, 22, "Grand Total", quote == null ? "" : amount(quote.grandTotal()), label);
            putField(auditSheet, 23, "Delivery Timeline", quote == null ? "" : quote.deliveryTimeline(), label);
            putField(auditSheet, 24, "Warranty", quote == null ? "" : quote.warranty(), label);
            putField(auditSheet, 25, "Notes", quote == null ? "" : quote.notes(), label);
            int auditTableHeader = 27;
            var auditHeaderRow = auditSheet.createRow(auditTableHeader);
            for (int i = 0; i < INTERNAL_AUDIT_HEADERS.size(); i++) {
                auditHeaderRow.createCell(i).setCellValue(INTERNAL_AUDIT_HEADERS.get(i));
                auditHeaderRow.getCell(i).setCellStyle(heading);
            }
            int auditRow = auditTableHeader + 1;
            for (var requested : rfq.getLineItems()) {
                List<QuotationSnapshot.QuotedLineSnapshot> matching = quote == null ? List.of() : quote.items().stream()
                        .filter(line -> line.sourceLineId().equals(requested.getSourceLineId())).toList();
                if (matching.isEmpty()) {
                    writeAuditRow(auditSheet.createRow(auditRow++), requested.getLineNumber(), requested.getSourceLineId(),
                            requested.getRequestedQuantity(), requested.getProcurementCategory(), requested.getTaaRequired(),
                            requested.getUnitOfMeasure(), requested.getCustomerPartNumber(), requested.getRequestedManufacturer(),
                            requested.getRequestedPartNumber(), requested.getDescription(), requested.getTechnicalSpecifications(),
                            null, quote, wrapped);
                } else {
                    for (var line : matching) {
                        writeAuditRow(auditSheet.createRow(auditRow++), requested.getLineNumber(), requested.getSourceLineId(),
                                requested.getRequestedQuantity(), requested.getProcurementCategory(), requested.getTaaRequired(),
                                requested.getUnitOfMeasure(), requested.getCustomerPartNumber(), requested.getRequestedManufacturer(),
                                requested.getRequestedPartNumber(), requested.getDescription(), requested.getTechnicalSpecifications(),
                                line, quote, wrapped);
                    }
                }
            }
            int[] auditWidths = {10, 10, 10, 22, 16, 26, 28, 28, 58, 58, 28, 28, 17, 15, 15, 15, 24, 24, 36, 42, 34};
            setColumnWidths(auditSheet, auditWidths);
            auditSheet.setColumnWidth(0, 24 * 256);
            auditSheet.setColumnWidth(1, 40 * 256);
            writeLongTextSheet(workbook, rfq, heading, wrapped);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The RFQ workbook could not be generated");
        }
    }

    private void writeClientRow(org.apache.poi.ss.usermodel.Row row, int lineNumber, BigDecimal requestedQty,
                                String uom, String customerPart, String description,
                                String requestedManufacturer, String requestedPartNumber,
                                QuotationSnapshot.QuotedLineSnapshot quoteLine,
                                QuotationSnapshot quote, CellStyle wrapped) {
        String effectiveQty = quoteLine != null && quoteLine.quantity() != null
                ? amount(quoteLine.quantity()) : amount(requestedQty);
        String effectiveUom = quoteLine != null && !blank(quoteLine.unitOfMeasure())
                ? quoteLine.unitOfMeasure() : uom;
        String effectiveDesc = quoteLine != null && !blank(quoteLine.description())
                ? quoteLine.description() : description;
        String effectiveMfg = quoteLine != null && !blank(quoteLine.manufacturer())
                ? quoteLine.manufacturer() : requestedManufacturer;
        String effectivePn = quoteLine != null && !blank(quoteLine.partNumber())
                ? quoteLine.partNumber() : requestedPartNumber;
        String edd = quoteLine != null && !blank(quoteLine.deliveryTimeline())
                ? quoteLine.deliveryTimeline() : (quote == null ? null : quote.deliveryTimeline());
        String warranty = quoteLine != null && !blank(quoteLine.warranty())
                ? quoteLine.warranty() : (quote == null ? null : quote.warranty());
        String notes = quoteLine != null && !blank(quoteLine.note())
                ? quoteLine.note() : (quote == null ? null : quote.notes());
        set(row, 0, String.valueOf(lineNumber));
        set(row, 1, effectiveQty);
        set(row, 2, effectiveUom);
        set(row, 3, customerPart);
        set(row, 4, effectiveDesc);
        set(row, 5, effectiveMfg);
        set(row, 6, effectivePn);
        set(row, 7, quoteLine == null ? null : amount(quoteLine.unitPrice()));
        set(row, 8, quoteLine == null ? null : amount(quoteLine.lineTotal()));
        set(row, 9, null);
        set(row, 10, edd);
        set(row, 11, warranty);
        set(row, 12, notes);
        wrapRow(row, wrapped, CLIENT_HEADERS.size());
    }

    private void writeAuditRow(org.apache.poi.ss.usermodel.Row row, int lineNumber, String sourceLineId, BigDecimal requestedQty,
                               String category, Boolean taaRequired, String uom,
                               String customerPart, String requestedManufacturer, String requestedPartNumber,
                               String description, String technicalSpecifications,
                               QuotationSnapshot.QuotedLineSnapshot quoteLine,
                               QuotationSnapshot quote, CellStyle wrapped) {
        set(row, 0, String.valueOf(lineNumber));
        set(row, 1, quoteLine != null && quoteLine.quantity() != null ? amount(quoteLine.quantity()) : amount(requestedQty));
        set(row, 2, quoteLine != null && !blank(quoteLine.unitOfMeasure()) ? quoteLine.unitOfMeasure() : uom);
        set(row, 3, category);
        set(row, 4, yesNoUnknown(taaRequired));
        set(row, 5, customerPart);
        set(row, 6, requestedManufacturer);
        set(row, 7, requestedPartNumber);
        set(row, 8, quoteLine != null && !blank(quoteLine.description()) ? quoteLine.description() : description);
        set(row, 9, excelTextOrLongTextReference(technicalSpecifications, sourceLineId));
        set(row, 10, quoteLine == null ? null : quoteLine.manufacturer());
        set(row, 11, quoteLine == null ? null : quoteLine.partNumber());
        set(row, 12, quoteLine == null ? null : yesNoUnknown(quoteLine.taaCompliant()));
        set(row, 13, quoteLine == null ? null : amount(quoteLine.unitPrice()));
        set(row, 14, quoteLine == null ? null : amount(quoteLine.lineTotal()));
        set(row, 15, quote == null ? null : amount(quote.freight()));
        set(row, 16, quoteLine == null ? (quote == null ? null : quote.deliveryTimeline())
                : (blank(quoteLine.deliveryTimeline()) ? quote.deliveryTimeline() : quoteLine.deliveryTimeline()));
        set(row, 17, quoteLine == null ? (quote == null ? null : quote.warranty())
                : (blank(quoteLine.warranty()) ? quote.warranty() : quoteLine.warranty()));
        set(row, 18, quoteLine == null ? (quote == null ? null : quote.notes()) : quoteLine.note());
        set(row, 19, quoteLine == null ? null : quoteLine.productUrl());
        set(row, 20, sourceLineId);
        wrapRow(row, wrapped, INTERNAL_AUDIT_HEADERS.size());
    }

    private String readCustomerAddress(Rfq rfq) {
        String rawJson = rfq.getSourceDataJson();
        if (blank(rawJson)) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(rawJson, Map.class);
            Object addr = parsed == null ? null : parsed.get("customerAddress");
            return addr == null ? null : String.valueOf(addr).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setColumnWidths(org.apache.poi.ss.usermodel.Sheet sheet, int[] widthsInCharacters) {
        for (int column = 0; column < widthsInCharacters.length; column++) {
            sheet.setColumnWidth(column, Math.min(255 * 256, widthsInCharacters[column] * 256));
        }
    }

    private void writeLongTextSheet(XSSFWorkbook workbook, Rfq rfq, CellStyle heading, CellStyle wrapped) {
        List<LongTextEntry> entries = new ArrayList<>();
        if (!blank(rfq.getTermsAndConditions())) entries.add(new LongTextEntry("TERMS_AND_CONDITIONS", "", "", rfq.getTermsAndConditions()));
        for (var item : rfq.getLineItems()) {
            String text = item.getTechnicalSpecifications();
            if (text != null && text.length() > 32_000) {
                entries.add(new LongTextEntry("LINE_TECHNICAL_SPECIFICATIONS", String.valueOf(item.getLineNumber()), item.getSourceLineId(), text));
            }
        }
        if (entries.isEmpty()) return;
        var sheet = workbook.createSheet("Long Text");
        String[] headers = {"TYPE", "ITEM #", "SOURCE LINE ID", "SEGMENT", "TEXT"};
        var header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) { header.createCell(i).setCellValue(headers[i]); header.getCell(i).setCellStyle(heading); }
        int rowIndex = 1;
        final int segmentLength = 30_000;
        for (LongTextEntry entry : entries) {
            int segments = (entry.text().length() + segmentLength - 1) / segmentLength;
            for (int segment = 0, start = 0; start < entry.text().length(); segment++) {
                int end = Math.min(start + segmentLength, entry.text().length());
                if (end < entry.text().length() && Character.isHighSurrogate(entry.text().charAt(end - 1))
                        && Character.isLowSurrogate(entry.text().charAt(end))) end--;
                var row = sheet.createRow(rowIndex++);
                set(row, 0, entry.type()); set(row, 1, entry.lineNumber()); set(row, 2, entry.sourceLineId());
                set(row, 3, (segment + 1) + "/" + segments); set(row, 4, entry.text().substring(start, end));
                wrapRow(row, wrapped, headers.length);
                start = end;
            }
        }
        setColumnWidths(sheet, new int[]{30, 10, 24, 12, 90});
    }

    private String excelTextOrLongTextReference(String text, String sourceLineId) {
        if (text == null) return null;
        return text.length() <= 32_000 ? text : "See Long Text tab; source line " + sourceLineId;
    }

    private record LongTextEntry(String type, String lineNumber, String sourceLineId, String text) { }

    private void wrapRow(org.apache.poi.ss.usermodel.Row row, CellStyle style, int cellCount) {
        for (int column = 0; column < cellCount; column++) {
            var cell = row.getCell(column);
            if (cell != null) cell.setCellStyle(style);
        }
    }

    private String yesNoUnknown(Boolean value) { return value == null ? "" : value ? "Yes" : "No"; }

    private void putField(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, String key, String value, CellStyle style) {
        var row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(key); row.getCell(0).setCellStyle(style);
        var valueCell = row.createCell(1);
        valueCell.setCellValue(value == null ? "" : value);
        CellStyle wrappedValue = sheet.getWorkbook().createCellStyle();
        wrappedValue.setWrapText(true);
        wrappedValue.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
        valueCell.setCellStyle(wrappedValue);
    }

    private void set(org.apache.poi.ss.usermodel.Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private String amount(BigDecimal value) { return value == null ? "" : value.toPlainString(); }

    private byte[] pdf(Rfq rfq, QuotationSnapshot quote, QuotationVersion version,
                       QuotationReviewDecision review, String quoteDecision) {
        try (PDDocument document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PdfTextWriter writer = new PdfTextWriter(document);
            String customerAddress = readCustomerAddress(rfq);
            writer.line("GLOBAL RFQ - REQUEST FOR QUOTE", true, 16);
            writer.line("Customer: " + safe(rfq.getCustomerName()), true, 11);
            if (!blank(customerAddress)) {
                writer.wrapped("Customer Address: " + safe(customerAddress), false, 10);
            }
            writer.wrapped("Customer company: " + safe(rfq.getCustomerCompany()) + " | Request: " + safe(rfq.getRequestTitle()), false, 10);
            writer.wrapped("Source RFQ: " + safe(rfq.getSourceRfqId()) + " | Source System: " + safe(rfq.getSourceSystem()), false, 10);
            writer.wrapped("Internal RFQ ID: " + rfq.getPublicId() + " | Source reference: " + safe(rfq.getSourceReference()), false, 9);
            if (version != null) {
                writer.wrapped("Quotation Version ID: " + version.getId() + " | Version: V" + version.getVersionNumber()
                        + " | Submitted by user ID: " + version.getSubmittedByUserId()
                        + " | Submitted at UTC: " + version.getSubmittedAt(), false, 9);
            }
            if (review != null) {
                writer.wrapped("Review: " + review.getDecision() + " | Reviewer user ID: " + review.getReviewerUserId()
                        + " | Decided at: " + review.getDecidedAt(), false, 9);
                writer.wrapped("Review reason: " + safe(review.getReason()), false, 9);
            }
            writer.wrapped("Status: " + rfq.getStatus() + " | Customer decision: " + rfq.getCustomerDecision(), false, 10);
            writer.wrapped("Quotation Manager Hint: " + safe(rfq.getSourceQuotationManagerName()), false, 10);
            writer.wrapped("Due: " + safe(rfq.getDueAtLocal() == null ? null : rfq.getDueAtLocal() + " " + safe(rfq.getDueTimeZone())), false, 10);
            writer.wrapped("Requested delivery: " + safe(rfq.getRequestedDeliveryDate() == null ? null : rfq.getRequestedDeliveryDate().toString()), false, 10);
            writer.wrapped("Buyer: " + safe(rfq.getBuyerName()) + " | " + safe(rfq.getBuyerEmail()) + " | " + safe(rfq.getBuyerPhone()), false, 10);
            writer.wrapped("Source URL: " + safe(rfq.getSourceUrl()), false, 9);
            writer.wrapped("Ship To: " + safe(rfq.getShipToText()), false, 10);
            writer.line(" ", false, 10);
            writer.line("LINE ITEMS", true, 12);
            writer.line("ITEM | QTY | UOM | CUSTOMER PART | DESCRIPTION", true, 9);
            writer.line("------------------------------------------------------------", false, 9);
            Map<String, List<QuotationSnapshot.QuotedLineSnapshot>> mapped = new LinkedHashMap<>();
            if (quote != null) quote.items().forEach(item -> mapped.computeIfAbsent(item.sourceLineId(), ignored -> new ArrayList<>()).add(item));
            for (var item : rfq.getLineItems()) {
                writer.wrapped("Item " + item.getLineNumber() + " | Qty " + amount(item.getRequestedQuantity()) + " | UOM "
                        + safe(item.getUnitOfMeasure()) + " | Customer part " + safe(item.getCustomerPartNumber())
                        + " | Source line ID " + safe(item.getSourceLineId()), true, 9);
                writer.wrapped("Category: " + safe(item.getProcurementCategory()) + " | TAA required: " + yesNoUnknown(item.getTaaRequired()), false, 9);
                writer.wrapped("Requested manufacturer/part: " + safe(item.getRequestedManufacturer()) + " / " + safe(item.getRequestedPartNumber()), false, 9);
                writer.wrapped("Requested: " + safe(item.getDescription()), false, 9);
                writer.wrapped("Technical specifications: " + safe(item.getTechnicalSpecifications()), false, 9);
                for (var quoted : mapped.getOrDefault(item.getSourceLineId(), List.of())) {
                    writer.wrapped("Quote: " + safe(quoted.manufacturer()) + " " + safe(quoted.partNumber())
                            + " | TAA compliant: " + yesNoUnknown(quoted.taaCompliant())
                            + " | Unit Price " + amount(quoted.unitPrice()) + " | Line Total " + amount(quoted.lineTotal()), false, 9);
                }
            }
            if (quote != null) {
                writer.line(" ", false, 10);
                writer.line("QUOTATION VERSION " + version.getVersionNumber() + " - " + quoteDecision, true, 11);
                writer.line("Currency: " + safe(quote.currency()) + " | Subtotal: " + amount(quote.subtotal())
                        + " | Freight: " + amount(quote.freight()) + " | Other: " + amount(quote.otherCharges()), false, 10);
                writer.line("Discount: " + amount(quote.discount()) + " | Tax: " + amount(quote.taxAmount())
                        + " | Grand Total: " + amount(quote.grandTotal()), true, 10);
                writer.wrapped("Delivery: " + safe(quote.deliveryTimeline()) + " | Warranty: " + safe(quote.warranty()), false, 10);
                writer.wrapped("Notes: " + safe(quote.notes()), false, 10);
            }
            writer.wrapped("Terms and Conditions: " + safe(rfq.getTermsAndConditions()), false, 9);
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The RFQ PDF could not be generated");
        }
    }

    private QuotationSnapshot readSnapshot(String json) {
        try { return mapper.readValue(json, QuotationSnapshot.class); }
        catch (Exception exception) { throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "A stored quotation snapshot could not be read"); }
    }

    private String fileName(Rfq rfq, String extension) { return "RFQ-" + rfq.getSourceRfqId().replaceAll("[^A-Za-z0-9._-]", "_") + "." + extension; }
    private String safe(String value) { return value == null ? "" : value.replace('\n', ' ').replace('\r', ' '); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record ExportFile(String fileName, String mediaType, byte[] bytes) { }

    private static final class PdfTextWriter {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        private PdfTextWriter(PDDocument document) throws Exception { this.document = document; newPage(); }

        private void line(String value, boolean isBold, int size) throws Exception {
            if (y < 55) newPage();
            stream.beginText();
            stream.setFont(isBold ? bold : regular, size);
            stream.newLineAtOffset(48, y);
            stream.showText(ascii(value));
            stream.endText();
            y -= size + 6;
        }

        private void wrapped(String text, boolean isBold, int size) throws Exception {
            String normalized = ascii(text);
            int max = Math.max(30, 112 - size * 2);
            if (normalized.isBlank()) { line(" ", isBold, size); return; }
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                if (word.length() > max) {
                    if (!current.isEmpty()) { line(current.toString(), isBold, size); current.setLength(0); }
                    for (int start = 0; start < word.length(); start += max) {
                        line(word.substring(start, Math.min(start + max, word.length())), isBold, size);
                    }
                    continue;
                }
                if (current.length() + word.length() + 1 > max) { line(current.toString(), isBold, size); current.setLength(0); }
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
            if (!current.isEmpty()) line(current.toString(), isBold, size);
        }

        private void newPage() throws Exception {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - 50;
        }

        private void close() throws Exception { if (stream != null) stream.close(); }

        private String ascii(String input) {
            if (input == null) return "";
            return input.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[^\\x20-\\xFF]", "?");
        }
    }
}
