package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqTimeProperties;
import com.globalrfq.rfq.intake.dto.CreateRfqRequest;
import com.globalrfq.rfq.intake.dto.RfqImportPreviewResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RfqDocumentParser {
    private static final Pattern PR_NUMBER = Pattern.compile("(?i)PR\\s*#\\s*:?\\s*([A-Za-z0-9._/-]+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_LINE_ITEM = Pattern.compile("^\\s*(\\d{1,4})\\s+(\\d+(?:\\.\\d+)?)\\s+([A-Za-z]{1,12})\\s+([A-Za-z0-9._/-]{2,})\\s+(.+)$");
    private static final int MAX_SOURCE_CELLS = 20_000;
    private static final int MAX_SOURCE_TEXT_CHARS = 150_000;

    private final RfqTimeProperties timeProperties;

    public RfqDocumentParser(RfqTimeProperties timeProperties) {
        this.timeProperties = timeProperties;
    }

    public RfqImportPreviewResponse parse(MultipartFile file, String sourceSystemOverride, String sourceRfqIdOverride) {
        if (file == null || file.isEmpty()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Select a non-empty Excel or PDF file");
        String fileName = safeName(file.getOriginalFilename());
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            if (extension.equals("xlsx") || extension.equals("xls")) {
                return parseWorkbook(fileName, file.getBytes(), valueOr(sourceSystemOverride, "CUSTOMER_SOFTWARE"), sourceRfqIdOverride);
            }
            if (extension.equals("pdf")) {
                return parsePdf(fileName, file.getBytes(), valueOr(sourceSystemOverride, "CUSTOMER_SOFTWARE"), sourceRfqIdOverride);
            }
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Import preview supports Excel workbooks (.xlsx, .xls) and PDF files");
        } catch (RfqException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The source file could not be parsed safely");
        }
    }

    private RfqImportPreviewResponse parseWorkbook(String fileName, byte[] bytes, String sourceSystem, String idOverride) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<String> warnings = new ArrayList<>();
            Map<String, String> raw = new LinkedHashMap<>();
            int cells = 0;
            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                var sheet = workbook.getSheetAt(si);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String value = cellText(cell, formatter);
                        if (!value.isBlank()) {
                            if (++cells > MAX_SOURCE_CELLS) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Workbook contains too many populated cells for import");
                            if (value.length() > 10_000) value = value.substring(0, 10_000);
                            raw.put(sheet.getSheetName() + "!" + cell.getAddress().formatAsString(), value);
                        }
                    }
                }
            }
            TableHeader header = findTableHeader(workbook, formatter);
            if (header == null) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Could not find the RFQ item table headers. Expected ITEM #, QTY, and DESCRIPTION columns.");

            String pr = findLabelValue(workbook, formatter, Set.of("pr#", "prnumber", "purchaseorderrequest"));
            if (blank(pr)) pr = idFromRaw(raw);
            if (!blank(idOverride)) pr = idOverride.trim();
            if (blank(pr)) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Could not map a PR #. Supply the source RFQ ID explicitly.");
            String company = findCompany(workbook, formatter);
            if (blank(company)) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Could not identify the customer company in this workbook; no RFQ was imported.");

            List<CreateRfqRequest.CreateRfqLineItemRequest> items = parseWorkbookItems(header, formatter, warnings);
            if (items.isEmpty()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The workbook item table did not contain any importable line items");
            String dueRaw = findLabelValue(workbook, formatter, Set.of("duedate&time", "duedateandtime", "duedatetime"));
            LocalDateTime due = parseLocalDateTime(dueRaw);
            String deliveryRaw = findLabelValue(workbook, formatter, Set.of("requesteddeliverydate", "requireddeliverydate"));
            LocalDate delivery = parseLocalDate(deliveryRaw);
            String buyer = findLabelValue(workbook, formatter, Set.of("buyername"));
            String email = findLabelValue(workbook, formatter, Set.of("buyeremail"));
            String shipTo = findLabelValue(workbook, formatter, Set.of("shipto", "shippingaddress"));
            String managerHint = quotationManagerHint(workbook, formatter);
            String sourceUrl = raw.values().stream().filter(value -> value.matches("(?i)^https?://.+")).findFirst().orElse(null);
            String terms = collectTerms(raw);
            Map<String, Object> sourceData = new LinkedHashMap<>(raw);
            sourceData.put("originalFileName", fileName);
            sourceData.put("importFormat", "EXCEL");
            sourceData.put("configuredDefaultTimeZone", timeProperties.getDefaultTimeZone());
            if (managerHint != null) sourceData.put("importedQuotationManagerHint", managerHint);
            if (due != null) warnings.add("The workbook does not state a timezone. The due time will be interpreted using RFQ_DEFAULT_TIME_ZONE=" + timeProperties.getDefaultTimeZone() + ".");
            if (managerHint != null) warnings.add("Quotation manager hint '" + managerHint + "' was imported as a source hint. An Admin must map it to an active Identity account before assignment.");
            warnings.add("Unmapped workbook cells are preserved in sourceData and in the original attachment.");

            CreateRfqRequest request = new CreateRfqRequest(sourceSystem, pr, null, managerHint, company.trim(), company.trim(),
                    trimOrNull(buyer), trimOrNull(email), null, "Request for Quote " + pr, due,
                    due == null ? null : timeProperties.getDefaultTimeZone(), delivery, shipTo, sourceUrl, terms,
                    sourceData, items);
            Map<String, String> mappings = new LinkedHashMap<>();
            mappings.put("PR #", "sourceRfqId");
            mappings.put("Company name", "customerName/customerCompany");
            mappings.put("Buyer Name", "buyerName");
            mappings.put("Buyer Email", "buyerEmail");
            mappings.put("Due Date & Time", "dueAtLocal + configured timezone");
            mappings.put("Requested Delivery Date", "requestedDeliveryDate");
            mappings.put("Ship To", "shipToText");
            mappings.put("PALLETI cell beside PR #", "sourceQuotationManagerName (assignment hint only)");
            mappings.put("Item table columns", "rfq line items; unrecognized columns retained in lineItem.sourceData");
            return new RfqImportPreviewResponse(fileName, "EXCEL", request, mappings, warnings, null, null);
        }
    }

    private List<CreateRfqRequest.CreateRfqLineItemRequest> parseWorkbookItems(TableHeader header, DataFormatter formatter,
                                                                                List<String> warnings) {
        List<CreateRfqRequest.CreateRfqLineItemRequest> lines = new ArrayList<>();
        int nextLine = 1;
        for (int ri = header.row().getRowNum() + 1; ri <= header.sheet().getLastRowNum(); ri++) {
            Row row = header.sheet().getRow(ri);
            if (row == null) continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> col : header.columns().entrySet()) {
                values.put(col.getValue(), cellText(row.getCell(col.getKey()), formatter));
            }
            String description = value(values, "description");
            String qty = value(values, "qty");
            if (description.isBlank() && qty.isBlank()) continue;
            if (description.isBlank() || qty.isBlank()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED,
                    "Workbook row " + (ri + 1) + " contains an incomplete item. Complete its description and quantity before importing.");
            String sourceLineId = value(values, "item");
            if (sourceLineId.isBlank()) sourceLineId = String.valueOf(nextLine);
            int lineNumber = parseInt(sourceLineId, nextLine);
            BigDecimal quantity = parseDecimal(qty);
            if (quantity == null || quantity.signum() <= 0) throw new RfqException(RfqErrorCode.VALIDATION_FAILED,
                    "Workbook row " + (ri + 1) + " has an invalid quantity");
            Map<String, Object> rawLine = new LinkedHashMap<>();
            values.forEach((key, value) -> rawLine.put(key, value));
            String itemDescription = description;
            String taaValue = value(values, "taarequired");
            Boolean taaRequired = parseTaaRequired(taaValue);
            if (!taaValue.isBlank() && taaRequired == null
                    && !warnings.contains("One or more TAA values could not be interpreted as yes/no. Review the original cell; its source value is preserved.")) {
                warnings.add("One or more TAA values could not be interpreted as yes/no. Review the original cell; its source value is preserved.");
            }
            lines.add(new CreateRfqRequest.CreateRfqLineItemRequest(sourceLineId, lineNumber,
                    itemDescription, quantity, value(values, "uom"), value(values, "customerpart"),
                    value(values, "manufacturer"), value(values, "partnumber"), value(values, "category"),
                    taaRequired, null, rawLine));
            nextLine++;
            if (lines.size() > 500) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "A single RFQ may contain at most 500 line items");
        }
        return lines;
    }

    private RfqImportPreviewResponse parsePdf(String fileName, byte[] bytes, String sourceSystem, String idOverride) throws Exception {
        String text;
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "PDF is encrypted or outside the allowed page range");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        if (text.length() > MAX_SOURCE_TEXT_CHARS) text = text.substring(0, MAX_SOURCE_TEXT_CHARS);
        String pr = firstMatch(PR_NUMBER, text, 1);
        if (!blank(idOverride)) pr = idOverride.trim();
        if (blank(pr)) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Could not identify the PR # in this PDF. Use the Excel import or provide the RFQ data manually.");
        String company = findCompany(text);
        if (blank(company)) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Could not identify the customer company in this PDF; no RFQ was imported.");
        String buyerBlock = firstMatch(Pattern.compile("(?is)BUYER:\\s*(.{1,500}?)(?:SHIP\\s*TO:|$)"), text, 1);
        String buyerName = firstNonBlankLine(buyerBlock);
        String buyerEmail = firstMatch(EMAIL, buyerBlock, 0);
        String dueRaw = firstMatch(Pattern.compile("(?im)Due\\s*Date\\s*&\\s*Time\\s*:\\s*(.+)$"), text, 1);
        String deliveryRaw = firstMatch(Pattern.compile("(?im)(?:Required|Requested)\\s*Delivery\\s*Date\\s*:\\s*(.+)$"), text, 1);
        LocalDateTime due = parseLocalDateTime(dueRaw);
        LocalDate delivery = parseLocalDate(deliveryRaw);
        String shipTo = firstMatch(Pattern.compile("(?is)SHIP\\s*TO:\\s*(.{1,1000}?)(?:\\n\\s*(?:ITEM\\s*#|A competitive procurement|TERMS|To review)|$)"), text, 1);
        String sourceUrl = firstMatch(Pattern.compile("(?i)(https?://[^\\s<>]+)"), text, 1);
        List<CreateRfqRequest.CreateRfqLineItemRequest> items = parsePdfItems(text);
        if (items.isEmpty()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED,
                "The PDF item table could not be mapped reliably. Use the Excel template or enter the line items manually; no RFQ was imported.");
        String terms = afterFirst(text, "A competitive procurement");
        Map<String, Object> sourceData = new LinkedHashMap<>();
        sourceData.put("originalFileName", fileName);
        sourceData.put("importFormat", "PDF_TEXT");
        sourceData.put("rawExtractedText", text);
        sourceData.put("configuredDefaultTimeZone", timeProperties.getDefaultTimeZone());
        List<String> warnings = new ArrayList<>();
        warnings.add("PDF text and item rows were extracted heuristically. Review every imported value against the original PDF before saving.");
        if (due != null) warnings.add("The due time will be interpreted using RFQ_DEFAULT_TIME_ZONE=" + timeProperties.getDefaultTimeZone() + ".");
        warnings.add("The original PDF should be uploaded as a CUSTOMER_REQUEST attachment after the RFQ is created.");
        CreateRfqRequest request = new CreateRfqRequest(sourceSystem, pr, null, null, company.trim(), company.trim(),
                trimOrNull(buyerName), trimOrNull(buyerEmail), null, "Request for Quote " + pr, due,
                due == null ? null : timeProperties.getDefaultTimeZone(), delivery, trimOrNull(shipTo), trimOrNull(sourceUrl),
                trimOrNull(terms), sourceData, items);
        return new RfqImportPreviewResponse(fileName, "PDF_TEXT", request,
                Map.of("PR #", "sourceRfqId", "Buyer name/email", "buyerName/buyerEmail",
                        "Due Date & Time", "dueAtLocal + configured timezone", "Ship To", "shipToText",
                        "Item table", "rfq line items"), warnings, null, null);
    }

    private List<CreateRfqRequest.CreateRfqLineItemRequest> parsePdfItems(String text) {
        List<CreateRfqRequest.CreateRfqLineItemRequest> result = new ArrayList<>();
        boolean inTable = false;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.toUpperCase(Locale.ROOT).contains("ITEM #") && line.toUpperCase(Locale.ROOT).contains("DESCRIPTION")) {
                inTable = true;
                continue;
            }
            if (!inTable) continue;
            if (line.startsWith("A competitive procurement") || line.startsWith("TERMS OF PAYMENT") || line.startsWith("NOTE :")
                    || line.startsWith("To submit your bid")) break;
            Matcher matcher = PDF_LINE_ITEM.matcher(line);
            if (!matcher.matches()) continue;
            StringBuilder description = new StringBuilder(matcher.group(5));
            for (int j = i + 1; j < lines.length; j++) {
                String follow = lines[j].trim();
                if (follow.isBlank() || follow.startsWith("A competitive procurement") || follow.startsWith("TERMS OF PAYMENT")) break;
                if (PDF_LINE_ITEM.matcher(follow).matches()) break;
                description.append(' ').append(follow);
                i = j;
            }
            BigDecimal quantity = parseDecimal(matcher.group(2));
            String itemId = matcher.group(1);
            result.add(new CreateRfqRequest.CreateRfqLineItemRequest(itemId, parseInt(itemId, result.size() + 1),
                    description.toString().trim(), quantity, matcher.group(3), null, null,
                    matcher.group(4), null, null, null, Map.of("pdfTableRow", line)));
        }
        return result;
    }

    private TableHeader findTableHeader(Workbook workbook, DataFormatter formatter) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            var sheet = workbook.getSheetAt(si);
            for (Row row : sheet) {
                Map<Integer, String> columns = new HashMap<>();
                for (Cell cell : row) {
                    String normalized = normalize(cellText(cell, formatter));
                    if (!normalized.isBlank()) columns.put(cell.getColumnIndex(), normalizedHeader(normalized));
                }
                if (columns.containsValue("item") && columns.containsValue("qty") && columns.containsValue("description")) {
                    return new TableHeader(sheet, row, columns);
                }
            }
        }
        return null;
    }

    private String normalizedHeader(String normalized) {
        if (normalized.equals("item") || normalized.equals("itemnumber") || normalized.equals("item#")) return "item";
        if (normalized.equals("qty") || normalized.equals("quantity")) return "qty";
        if (normalized.equals("ui") || normalized.equals("uom") || normalized.equals("unit")) return "uom";
        if (normalized.contains("description")) return "description";
        if (normalized.equals("v2xpart") || normalized.equals("v2xpart#") || normalized.equals("v2xpartnumber") || normalized.equals("customerpartnumber")) return "customerpart";
        if (normalized.equals("manufacturer") || normalized.equals("brand")) return "manufacturer";
        if (normalized.equals("partno") || normalized.equals("partnumber") || normalized.equals("modelnumber")) return "partnumber";
        if (normalized.equals("category") || normalized.equals("commodity") || normalized.equals("productcategory")
                || normalized.equals("producttype") || normalized.equals("itemcategory")
                || normalized.equals("categorytype") || normalized.equals("commodityclass")) return "category";
        if (normalized.equals("taa") || normalized.equals("taacompliant") || normalized.equals("taarequired")
                || normalized.equals("taarequirement") || normalized.equals("tradeagreementsact")
                || normalized.equals("tradeagreementsactcompliant") || normalized.equals("tradeagreementsacttaacompliant")) return "taarequired";
        return normalized;
    }

    private String findLabelValue(Workbook workbook, DataFormatter formatter, java.util.Set<String> labels) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            for (Row row : workbook.getSheetAt(si)) {
                for (Cell cell : row) {
                    if (!labels.contains(normalize(cellText(cell, formatter)))) continue;
                    for (int column = cell.getColumnIndex() + 1; column < Math.min(cell.getColumnIndex() + 5, row.getLastCellNum()); column++) {
                        String value = cellText(row.getCell(column), formatter);
                        if (!value.isBlank()) return value.trim();
                    }
                }
            }
        }
        return null;
    }

    private String findCompany(Workbook workbook, DataFormatter formatter) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            var sheet = workbook.getSheetAt(si);
            int lastRow = Math.min(sheet.getLastRowNum(), 10);
            for (int ri = 0; ri <= lastRow; ri++) {
                Row row = sheet.getRow(ri);
                if (row == null) continue;
                for (Cell cell : row) {
                    String value = cellText(cell, formatter).trim();
                    String low = value.toLowerCase(Locale.ROOT);
                    if ((low.contains("corporation") || low.endsWith(" llc") || low.endsWith(" inc") || low.endsWith(" inc."))
                            && !value.equalsIgnoreCase("REQUEST FOR QUOTE")) return value;
                }
            }
        }
        return null;
    }

    private String quotationManagerHint(Workbook workbook, DataFormatter formatter) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            for (Row row : workbook.getSheetAt(si)) {
                for (Cell cell : row) {
                    String label = normalize(cellText(cell, formatter));
                    if (label.equals("pr#") || label.equals("prnumber")) {
                        String hint = cellText(row.getCell(cell.getColumnIndex() + 2), formatter).trim();
                        return hint.isBlank() ? null : hint;
                    }
                }
            }
        }
        return null;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (blank(value)) return null;
        String text = value.trim().replaceAll("\\s+(EDT|EST|UTC|GMT)$", "");
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("M/d/yy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try { return LocalDateTime.parse(text, formatter); }
            catch (DateTimeParseException ignored) { }
        }
        return parseLocalDate(text) == null ? null : parseLocalDate(text).atStartOfDay();
    }

    private LocalDate parseLocalDate(String value) {
        if (blank(value)) return null;
        String text = value.trim();
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(text, formatter); }
            catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime value = DateUtil.getLocalDateTime(cell.getNumericCellValue());
            return value.toString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String collectTerms(Map<String, String> raw) {
        List<String> terms = raw.values().stream().filter(value -> {
            String low = value.toLowerCase(Locale.ROOT);
            return low.contains("competitive procurement") || low.contains("terms of payment")
                    || low.contains("terms & conditions") || low.contains("not an order")
                    || low.contains("substitutions are authorized") || low.contains("complete quotation must be returned");
        }).toList();
        return terms.isEmpty() ? null : String.join("\n\n", terms);
    }

    private String findCompany(String text) {
        Matcher matcher = Pattern.compile("(?im)^.{0,100}\\b(?:Corporation|LLC|Inc\\.?)\\b.{0,100}$").matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private String firstNonBlankLine(String value) {
        if (blank(value)) return null;
        return value.lines().map(String::trim).filter(line -> !line.isBlank() && !line.contains("@"))
                .findFirst().orElse(null);
    }

    private String firstMatch(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    private String afterFirst(String text, String marker) {
        int index = text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
        return index < 0 ? null : text.substring(index).trim();
    }

    private String idFromRaw(Map<String, String> raw) {
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getValue().equalsIgnoreCase("PR #") || normalize(entry.getValue()).equals("pr#")) {
                String address = entry.getKey().substring(entry.getKey().lastIndexOf('!') + 1);
                String next = raw.get(entry.getKey().substring(0, entry.getKey().lastIndexOf('!') + 1) + columnAfter(address));
                if (next != null) return next.trim();
            }
        }
        return null;
    }

    private String columnAfter(String address) {
        Matcher matcher = Pattern.compile("^([A-Z]+)(\\d+)$").matcher(address);
        if (!matcher.matches()) return "";
        String letters = matcher.group(1);
        int number = 0;
        for (int i = 0; i < letters.length(); i++) number = number * 26 + (letters.charAt(i) - 'A' + 1);
        number++;
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            int remainder = (number - 1) % 26;
            result.insert(0, (char) ('A' + remainder));
            number = (number - 1) / 26;
        }
        return result + matcher.group(2);
    }

    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9#]", ""); }
    private String value(Map<String, String> values, String key) { return values.getOrDefault(key, "").trim(); }
    private Boolean parseTaaRequired(String value) {
        if (blank(value)) return null;
        return switch (normalize(value)) {
            case "true", "yes", "y", "1", "required", "compliant", "taacompliant" -> true;
            case "false", "no", "n", "0", "notrequired", "noncompliant", "notcompliant", "notapplicable", "na" -> false;
            default -> null;
        };
    }
    private BigDecimal parseDecimal(String value) {
        try { return new BigDecimal(value.replace(",", "").trim()); }
        catch (Exception ignored) { return null; }
    }
    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }
    private String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "File name is required");
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
    private String valueOr(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private String trimOrNull(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record TableHeader(org.apache.poi.ss.usermodel.Sheet sheet, Row row, Map<Integer, String> columns) { }
}
