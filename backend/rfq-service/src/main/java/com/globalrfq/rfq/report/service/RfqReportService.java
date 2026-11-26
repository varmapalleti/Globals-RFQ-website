package com.globalrfq.rfq.report.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqTimeProperties;
import com.globalrfq.rfq.intake.entity.CustomerDecision;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.report.dto.RfqOperationalReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RfqReportService {
    private static final int MAX_XLSX_ROWS = 10_000;
    private static final int MAX_PDF_ROWS = 500;
    private final RfqRepository rfqs;
    private final RfqAssignmentRepository assignments;
    private final RfqTimeProperties timeProperties;

    public RfqReportService(RfqRepository rfqs, RfqAssignmentRepository assignments, RfqTimeProperties timeProperties) {
        this.rfqs = rfqs;
        this.assignments = assignments;
        this.timeProperties = timeProperties;
    }

    @Transactional(readOnly = true)
    public RfqOperationalReportResponse summary(IdentityPrincipal actor, RfqStatus status, LocalDate from, LocalDate through) {
        requireAdmin(actor);
        validateDates(from, through);
        ZoneId reportZone = ZoneId.of(timeProperties.getDefaultTimeZone());
        Instant start = from == null ? null : from.atStartOfDay(reportZone).toInstant();
        Instant endExclusive = through == null ? null : through.plusDays(1).atStartOfDay(reportZone).toInstant();
        Map<String, Long> statuses = new LinkedHashMap<>();
        rfqs.countStatusesForReport(status, start, endExclusive).forEach(row -> statuses.put(row.getStatus().name(), row.getAmount()));
        Map<String, Long> decisions = new LinkedHashMap<>();
        rfqs.countCustomerDecisionsForReport(status, start, endExclusive).forEach(row -> decisions.put(row.getDecision().name(), row.getAmount()));
        long total = statuses.values().stream().mapToLong(Long::longValue).sum();
        List<RfqOperationalReportResponse.AssignmentWorkload> workload = assignments.findActiveWorkload().stream()
                .map(row -> new RfqOperationalReportResponse.AssignmentWorkload(row.getRole().name(), row.getUserId(), row.getActiveRfqCount()))
                .toList();
        return new RfqOperationalReportResponse(Instant.now(), from, through, status, total, statuses, decisions, workload);
    }

    @Transactional(readOnly = true)
    public ExportFile export(IdentityPrincipal actor, RfqStatus status, LocalDate from, LocalDate through, String format) {
        RfqOperationalReportResponse summary = summary(actor, status, from, through);
        int limit = "pdf".equalsIgnoreCase(format) ? MAX_PDF_ROWS : MAX_XLSX_ROWS;
        if (summary.totalRfqs() > limit) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "This report has " + summary.totalRfqs()
                    + " RFQs. Narrow the date range; this format supports at most " + limit + " rows.");
        }
        ZoneId reportZone = ZoneId.of(timeProperties.getDefaultTimeZone());
        Instant start = from == null ? null : from.atStartOfDay(reportZone).toInstant();
        Instant endExclusive = through == null ? null : through.plusDays(1).atStartOfDay(reportZone).toInstant();
        List<Rfq> rows = new ArrayList<>();
        Page<Rfq> page = rfqs.findForReport(status, start, endExclusive, PageRequest.of(0, 1000));
        rows.addAll(page.getContent());
        for (int pageNumber = 1; pageNumber < page.getTotalPages(); pageNumber++) {
            rows.addAll(rfqs.findForReport(status, start, endExclusive, PageRequest.of(pageNumber, 1000)).getContent());
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "xlsx" -> new ExportFile("RFQ-operational-report.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(summary, rows));
            case "pdf" -> new ExportFile("RFQ-operational-report.pdf", "application/pdf", pdf(summary, rows));
            default -> throw new RfqException(RfqErrorCode.BAD_REQUEST, "Report format must be xlsx or pdf");
        };
    }

    private byte[] xlsx(RfqOperationalReportResponse summary, List<Rfq> rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var heading = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(font); heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var summarySheet = workbook.createSheet("Summary");
            summarySheet.createRow(0).createCell(0).setCellValue("Global RFQ Operational Report");
            summarySheet.createRow(1).createCell(0).setCellValue("Generated UTC"); summarySheet.getRow(1).createCell(1).setCellValue(summary.generatedAt().toString());
            summarySheet.createRow(2).createCell(0).setCellValue("Created from"); summarySheet.getRow(2).createCell(1).setCellValue(summary.createdFrom() == null ? "All" : summary.createdFrom().toString());
            summarySheet.createRow(3).createCell(0).setCellValue("Created through"); summarySheet.getRow(3).createCell(1).setCellValue(summary.createdThrough() == null ? "All" : summary.createdThrough().toString());
            summarySheet.createRow(4).createCell(0).setCellValue("Total RFQs"); summarySheet.getRow(4).createCell(1).setCellValue(summary.totalRfqs());
            summarySheet.createRow(6).createCell(0).setCellValue("Status"); summarySheet.getRow(6).getCell(0).setCellStyle(heading);
            summarySheet.getRow(6).createCell(1).setCellValue("Count"); summarySheet.getRow(6).getCell(1).setCellStyle(heading);
            int row = 7;
            for (var entry : summary.statusCounts().entrySet()) { summarySheet.createRow(row).createCell(0).setCellValue(entry.getKey()); summarySheet.getRow(row++).createCell(1).setCellValue(entry.getValue()); }
            row += 1;
            summarySheet.createRow(row).createCell(0).setCellValue("Customer Decision"); summarySheet.getRow(row).getCell(0).setCellStyle(heading);
            summarySheet.getRow(row).createCell(1).setCellValue("Count"); summarySheet.getRow(row).getCell(1).setCellStyle(heading); row++;
            for (var entry : summary.customerDecisionCounts().entrySet()) { summarySheet.createRow(row).createCell(0).setCellValue(entry.getKey()); summarySheet.getRow(row++).createCell(1).setCellValue(entry.getValue()); }
            row += 1;
            summarySheet.createRow(row).createCell(0).setCellValue("Active Assignment Workload"); summarySheet.getRow(row).getCell(0).setCellStyle(heading);
            row++;
            summarySheet.createRow(row).createCell(0).setCellValue("Role"); summarySheet.getRow(row).getCell(0).setCellStyle(heading);
            summarySheet.getRow(row).createCell(1).setCellValue("Identity User ID"); summarySheet.getRow(row).getCell(1).setCellStyle(heading);
            summarySheet.getRow(row).createCell(2).setCellValue("Active RFQs"); summarySheet.getRow(row).getCell(2).setCellStyle(heading); row++;
            for (var entry : summary.activeAssignmentWorkload()) {
                summarySheet.createRow(row).createCell(0).setCellValue(entry.role());
                summarySheet.getRow(row).createCell(1).setCellValue(entry.identityUserId());
                summarySheet.getRow(row++).createCell(2).setCellValue(entry.activeRfqs());
            }
            for (int i = 0; i < 3; i++) summarySheet.autoSizeColumn(i);

            var details = workbook.createSheet("RFQs");
            String[] headers = {"RFQ ID", "Source System", "Source RFQ ID", "Customer", "Customer Company", "Buyer", "Buyer Email", "Due At Local", "Time Zone", "Delivery Date", "Status", "Customer Decision", "Created At UTC"};
            var header = details.createRow(0);
            for (int i = 0; i < headers.length; i++) { header.createCell(i).setCellValue(headers[i]); header.getCell(i).setCellStyle(heading); }
            int i = 1;
            for (Rfq rfq : rows) {
                var current = details.createRow(i++);
                String[] values = {rfq.getPublicId(), rfq.getSourceSystem(), rfq.getSourceRfqId(), rfq.getCustomerName(), rfq.getCustomerCompany(),
                        rfq.getBuyerName(), rfq.getBuyerEmail(), rfq.getDueAtLocal() == null ? null : rfq.getDueAtLocal().toString(),
                        rfq.getDueTimeZone(), rfq.getRequestedDeliveryDate() == null ? null : rfq.getRequestedDeliveryDate().toString(),
                        rfq.getStatus().name(), rfq.getCustomerDecision().name(), rfq.getCreatedAt().toString()};
                for (int col = 0; col < values.length; col++) current.createCell(col).setCellValue(values[col] == null ? "" : values[col]);
            }
            for (int col = 0; col < headers.length; col++) details.autoSizeColumn(col);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The RFQ Excel report could not be generated");
        }
    }

    private byte[] pdf(RfqOperationalReportResponse summary, List<Rfq> rows) {
        try (PDDocument document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PdfReportWriter writer = new PdfReportWriter(document);
            writer.line("GLOBAL RFQ OPERATIONAL REPORT", true, 15);
            writer.line("Generated UTC: " + summary.generatedAt(), false, 9);
            writer.line("Date filter: " + summary.createdFrom() + " through " + summary.createdThrough(), false, 9);
            writer.line("Total RFQs: " + summary.totalRfqs(), true, 11);
            writer.line("Status summary", true, 11);
            summary.statusCounts().forEach((key, value) -> writer.tryLine(key + ": " + value, false, 9));
            writer.line("Customer decisions", true, 11);
            summary.customerDecisionCounts().forEach((key, value) -> writer.tryLine(key + ": " + value, false, 9));
            writer.line("RFQ records", true, 11);
            for (Rfq rfq : rows) {
                writer.tryWrapped(rfq.getSourceRfqId() + " | " + rfq.getCustomerName() + " | " + rfq.getStatus()
                        + " | Customer: " + rfq.getCustomerDecision() + " | Created: " + rfq.getCreatedAt());
            }
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The RFQ PDF report could not be generated");
        }
    }

    private void validateDates(LocalDate from, LocalDate through) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The report start date must be before or equal to the end date");
        }
    }

    private void requireAdmin(IdentityPrincipal actor) {
        if (!actor.roles().contains("ADMIN") && !actor.roles().contains("SUPER_ADMIN")) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can view RFQ reports");
        }
    }

    public record ExportFile(String fileName, String mediaType, byte[] bytes) { }

    private static final class PdfReportWriter {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;
        private PdfReportWriter(PDDocument document) throws Exception { this.document = document; newPage(); }
        void tryLine(String text, boolean strong, int size) { try { line(text, strong, size); } catch (Exception e) { throw new IllegalStateException(e); } }
        void tryWrapped(String text) { try { wrapped(text); } catch (Exception e) { throw new IllegalStateException(e); } }
        private void line(String text, boolean strong, int size) throws Exception {
            if (y < 50) newPage();
            stream.beginText(); stream.setFont(strong ? bold : regular, size); stream.newLineAtOffset(40, y);
            stream.showText(ascii(text)); stream.endText(); y -= size + 5;
        }
        private void wrapped(String text) throws Exception {
            String value = ascii(text); int max = 100; StringBuilder current = new StringBuilder();
            for (String word : value.split("\\s+")) {
                if (current.length() + word.length() + 1 > max) { line(current.toString(), false, 8); current.setLength(0); }
                if (!current.isEmpty()) current.append(' '); current.append(word);
            }
            if (!current.isEmpty()) line(current.toString(), false, 8);
        }
        private void newPage() throws Exception {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - 40;
        }
        private void close() throws Exception { if (stream != null) stream.close(); }
        private String ascii(String text) { return text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[^\\x20-\\xFF]", "?"); }
    }
}
