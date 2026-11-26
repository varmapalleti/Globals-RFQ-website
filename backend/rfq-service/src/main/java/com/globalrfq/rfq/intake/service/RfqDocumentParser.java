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
    private static final Pattern PR_NUMBER = Pattern.compile("(?im)(?:PR|RFQ)\\s*(?:#|No\\.?|Number)?\\s*:?\\s*([A-Za-z0-9._/-]+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_LINE_ITEM = Pattern.compile("^\\s*(\\d{1,4})\\s+(\\d+(?:\\.\\d+)?)\\s+([A-Za-z]{1,12})\\s+([A-Za-z0-9._/-]{2,})\\s+(.+)$");
    private static final Set<String> KNOWN_HEADER_LABELS = Set.of(
            "pr#", "prnumber", "prno", "rfq#", "rfqnumber", "purchaserequest", "purchaseorderrequest",
            "duedate&time", "duedateandtime", "duedatetime", "requesteddeliverydate", "requireddeliverydate",
            "buyername", "buyeremail", "buyerphone", "shipto", "shippingaddress", "customer", "customername",
            "customercompany", "company", "companyname", "billto", "requesttitle", "sourceurl", "termsandconditions");
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

            String pr = findLabelValue(workbook, formatter, Set.of("pr#", "prnumber", "prno", "rfq#", "rfqnumber",
                    "purchaserequest", "purchaseorderrequest"), 2);
            if (blank(pr)) pr = idFromRaw(raw);
            if (!blank(idOverride)) pr = idOverride.trim();
            if (blank(pr)) {
                pr = "";
                warnings.add("PR / Customer RFQ # was not found in the workbook. Enter it manually before confirming.");
            }
            CompanyBlock companyBlock = findCompanyBlock(workbook, formatter);
            String company = companyBlock.company();
            if (blank(company)) {
                company = "";
                warnings.add("Customer name or company could not be identified in this workbook. Enter it manually before confirming.");
            }

            List<CreateRfqRequest.CreateRfqLineItemRequest> items = parseWorkbookItems(header, formatter, warnings);
            if (items.isEmpty()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The workbook item table did not contain any importable line items");
            String dueRaw = findLabelValue(workbook, formatter, Set.of("duedate&time", "duedateandtime", "duedatetime"));
            LocalDateTime due = parseLocalDateTime(dueRaw);
            String deliveryRaw = findLabelValue(workbook, formatter, Set.of("requesteddeliverydate", "requireddeliverydate"));
            LocalDate delivery = parseLocalDate(deliveryRaw);
            String buyer = findLabelValue(workbook, formatter, Set.of("buyername", "buyer"));
            String email = findLabelValue(workbook, formatter, Set.of("buyeremail", "email"));
            String phone = findLabelValue(workbook, formatter, Set.of("buyerphone", "phone"));
            String shipTo = findAddressBlockValue(workbook, formatter, Set.of("shipto", "shippingaddress"));
            String managerHint = quotationManagerHint(workbook, formatter, pr);
            String sourceUrl = raw.values().stream().filter(value -> value.matches("(?i)^https?://.+")).findFirst().orElse(null);
            String terms = collectTerms(raw);
            Map<String, Object> sourceData = new LinkedHashMap<>(raw);
            sourceData.put("originalFileName", fileName);
            sourceData.put("importFormat", "EXCEL");
            sourceData.put("configuredDefaultTimeZone", timeProperties.getDefaultTimeZone());
            if (!blank(companyBlock.address())) sourceData.put("customerAddress", companyBlock.address());
            if (managerHint != null) sourceData.put("importedQuotationManagerHint", managerHint);
            if (due != null) warnings.add("The workbook does not state a timezone. The due time will be interpreted using RFQ_DEFAULT_TIME_ZONE=" + timeProperties.getDefaultTimeZone() + ".");
            if (managerHint != null) warnings.add("Quotation manager hint '" + managerHint + "' was imported as a source hint. An Admin must map it to an active Identity account before assignment.");
            warnings.add("Unmapped workbook cells are preserved in sourceData and in the original attachment.");

            String title = blank(pr) ? "" : "Request for Quote " + pr;
            CreateRfqRequest request = new CreateRfqRequest(sourceSystem, pr, null, managerHint, company.trim(),
                    blank(company) ? null : company.trim(),
                    trimOrNull(buyer), trimOrNull(email), trimOrNull(phone), title, due,
                    due == null ? null : timeProperties.getDefaultTimeZone(), delivery, trimOrNull(shipTo), sourceUrl, terms,
                    sourceData, items);
            Map<String, String> mappings = new LinkedHashMap<>();
            if (!blank(pr)) mappings.put("PR #", "sourceRfqId");
            if (!blank(company)) mappings.put("Company name", "customerName/customerCompany");
            if (!blank(companyBlock.address())) mappings.put("Customer address", "sourceData.customerAddress");
            if (!blank(buyer)) mappings.put("Buyer Name", "buyerName");
            if (!blank(email)) mappings.put("Buyer Email", "buyerEmail");
            if (due != null) mappings.put("Due Date & Time", "dueAtLocal + configured timezone");
            if (delivery != null) mappings.put("Requested Delivery Date", "requestedDeliveryDate");
            if (!blank(shipTo)) mappings.put("Ship To", "shipToText");
            if (!blank(managerHint)) mappings.put("Source contact beside PR #", "sourceQuotationManagerName (assignment hint only)");
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
            if (qty.isBlank() && isFooterRow(row, formatter)) break;
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
                    itemDescription, quantity, trimOrNull(value(values, "uom")), trimOrNull(value(values, "customerpart")),
                    trimOrNull(value(values, "manufacturer")), trimOrNull(value(values, "partnumber")),
                    trimOrNull(value(values, "category")),
                    taaRequired, trimOrNull(value(values, "technicalspecifications")), rawLine));
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
        List<String> warnings = new ArrayList<>();
        String pr = firstMatch(PR_NUMBER, text, 1);
        if (!blank(idOverride)) pr = idOverride.trim();
        if (blank(pr)) {
            pr = "";
            warnings.add("PR / Customer RFQ # was not found in this PDF. Enter it manually before confirming.");
        }
        String company = findCompany(text);
        if (blank(company)) {
            company = "";
            warnings.add("Customer name or company could not be identified in this PDF. Enter it manually before confirming.");
        }
        String buyerBlock = firstMatch(Pattern.compile("(?is)BUYER\\s*:?\\s*(.{1,500}?)(?:SHIP\\s*TO\\s*:?|ITEM\\s*#|$)"), text, 1);
        String buyerName = firstMatch(Pattern.compile("(?im)^Buyer\\s*Name\\s*:?\\s*(.+)$"), text, 1);
        if (blank(buyerName)) buyerName = firstNonBlankLine(buyerBlock);
        String buyerEmail = firstMatch(Pattern.compile("(?im)^Buyer\\s*Email\\s*:?\\s*(" + EMAIL.pattern() + ")"), text, 1);
        if (blank(buyerEmail)) buyerEmail = firstMatch(EMAIL, buyerBlock, 0);
        String dueRaw = firstMatch(Pattern.compile("(?im)Due\\s*Date\\s*(?:&|and)?\\s*Time\\s*:?\\s*(.+)$"), text, 1);
        String deliveryRaw = firstMatch(Pattern.compile("(?im)(?:Required|Requested)\\s*Delivery\\s*Date\\s*:?\\s*(.+)$"), text, 1);
        LocalDateTime due = parseLocalDateTime(dueRaw);
        LocalDate delivery = parseLocalDate(deliveryRaw);
        String shipTo = firstMatch(Pattern.compile("(?is)SHIP\\s*TO\\s*:?\\s*(.{1,1000}?)(?:\\n\\s*(?:ITEM\\s*#|A competitive procurement|TERMS|To review|To submit)|$)"), text, 1);
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
        warnings.add("PDF text and item rows were extracted heuristically. Review every imported value against the original PDF before saving.");
        if (due != null) warnings.add("The due time will be interpreted using RFQ_DEFAULT_TIME_ZONE=" + timeProperties.getDefaultTimeZone() + ".");
        warnings.add("The original PDF should be uploaded as a CUSTOMER_REQUEST attachment after the RFQ is created.");
        String title = blank(pr) ? "" : "Request for Quote " + pr;
        CreateRfqRequest request = new CreateRfqRequest(sourceSystem, pr, null, null, company.trim(),
                blank(company) ? null : company.trim(),
                trimOrNull(buyerName), trimOrNull(buyerEmail), null, title, due,
                due == null ? null : timeProperties.getDefaultTimeZone(), delivery, trimOrNull(shipTo), trimOrNull(sourceUrl),
                trimOrNull(terms), sourceData, items);
        Map<String, String> mappings = new LinkedHashMap<>();
        if (!blank(pr)) mappings.put("PR #", "sourceRfqId");
        if (!blank(company)) mappings.put("Company name", "customerName/customerCompany");
        if (!blank(buyerName) || !blank(buyerEmail)) mappings.put("Buyer name/email", "buyerName/buyerEmail");
        if (due != null) mappings.put("Due Date & Time", "dueAtLocal + configured timezone");
        if (!blank(shipTo)) mappings.put("Ship To", "shipToText");
        mappings.put("Item table", "rfq line items");
        return new RfqImportPreviewResponse(fileName, "PDF_TEXT", request, mappings, warnings, null, null);
    }

    private List<CreateRfqRequest.CreateRfqLineItemRequest> parsePdfItems(String text) {
        List<CreateRfqRequest.CreateRfqLineItemRequest> result = new ArrayList<>();
        boolean hasV2xPartColumn = text.lines().map(this::normalize)
                .anyMatch(line -> line.contains("v2xpart#") || line.contains("v2xpartnumber") || line.contains("v2xpartid"));
        boolean inTable = false;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.toUpperCase(Locale.ROOT).contains("ITEM #") && line.toUpperCase(Locale.ROOT).contains("DESCRIPTION")) {
                inTable = true;
                continue;
            }
            if (!inTable) continue;
            if (line.startsWith("A competitive procurement") || line.startsWith("TERMS OF PAYMENT") || line.startsWith("NOTE:")
                    || line.startsWith("NOTE :") || line.startsWith("To submit your bid")) break;
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
                    description.toString().trim(), quantity, matcher.group(3),
                    hasV2xPartColumn ? matcher.group(4) : null, null,
                    hasV2xPartColumn ? null : matcher.group(4), null, null, null, Map.of("pdfTableRow", line)));
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
        if (normalized.equals("item") || normalized.equals("itemnumber") || normalized.equals("item#")
                || normalized.equals("itemno")) return "item";
        if (normalized.equals("qty") || normalized.equals("quantity")) return "qty";
        if (normalized.equals("ui") || normalized.equals("uom") || normalized.equals("unit")
                || normalized.equals("uoi")) return "uom";
        if (normalized.contains("description")) return "description";
        if (normalized.equals("v2xpart") || normalized.equals("v2xpart#") || normalized.equals("v2xpartid")
                || normalized.equals("v2xpartnumber") || normalized.equals("customerpartnumber")
                || normalized.equals("customerpart#") || normalized.equals("customerpart")) return "customerpart";
        if (normalized.equals("mfg") || normalized.equals("mfr") || normalized.equals("manufacturer")
                || normalized.equals("brand") || normalized.equals("requestedmanufacturer")) return "manufacturer";
        if (normalized.equals("pn") || normalized.equals("part#") || normalized.equals("partno")
                || normalized.equals("partnumber") || normalized.equals("modelnumber")
                || normalized.equals("requestedpart") || normalized.equals("requestedpartmodel")
                || normalized.equals("requestedpartnumber")) return "partnumber";
        if (normalized.equals("technicalspecifications") || normalized.equals("techspecs")
                || normalized.equals("specifications") || normalized.equals("specs")) return "technicalspecifications";
        if (normalized.equals("category") || normalized.equals("commodity") || normalized.equals("productcategory")
                || normalized.equals("producttype") || normalized.equals("itemcategory")
                || normalized.equals("categorytype") || normalized.equals("commodityclass")) return "category";
        if (normalized.equals("taa") || normalized.equals("taacompliant") || normalized.equals("taarequired")
                || normalized.equals("taarequirement") || normalized.equals("tradeagreementsact")
                || normalized.equals("tradeagreementsactcompliant") || normalized.equals("tradeagreementsacttaacompliant")) return "taarequired";
        return normalized;
    }

    private String findLabelValue(Workbook workbook, DataFormatter formatter, Set<String> labels) {
        return findLabelValue(workbook, formatter, labels, 5);
    }

    private String findLabelValue(Workbook workbook, DataFormatter formatter, Set<String> labels, int maxColumnsRight) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            for (Row row : workbook.getSheetAt(si)) {
                for (Cell cell : row) {
                    if (!labels.contains(normalize(cellText(cell, formatter)))) continue;
                    for (int column = cell.getColumnIndex() + 1; column < Math.min(cell.getColumnIndex() + maxColumnsRight, row.getLastCellNum()); column++) {
                        String value = cellText(row.getCell(column), formatter);
                        if (!value.isBlank()) return value.trim();
                    }
                }
            }
        }
        return null;
    }

    private String findAddressBlockValue(Workbook workbook, DataFormatter formatter, Set<String> labels) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            var sheet = workbook.getSheetAt(si);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (!labels.contains(normalize(cellText(cell, formatter)))) continue;
                    int labelCol = cell.getColumnIndex();
                    for (int column = labelCol + 1; column < Math.min(labelCol + 5, row.getLastCellNum()); column++) {
                        String first = cellText(row.getCell(column), formatter);
                        if (first.isBlank()) continue;
                        List<String> lines = new ArrayList<>();
                        lines.add(first.trim());
                        for (int nextRi = row.getRowNum() + 1; nextRi <= Math.min(row.getRowNum() + 6, sheet.getLastRowNum()); nextRi++) {
                            Row nextRow = sheet.getRow(nextRi);
                            if (nextRow == null) break;
                            String nextLabel = normalize(cellText(nextRow.getCell(labelCol), formatter));
                            if (!nextLabel.isBlank() || isItemHeaderRow(nextRow, formatter)) break;
                            String continuation = cellText(nextRow.getCell(column), formatter).trim();
                            if (continuation.isBlank()) break;
                            lines.add(continuation);
                        }
                        return String.join("\n", lines);
                    }
                }
            }
        }
        return null;
    }

    private CompanyBlock findCompanyBlock(Workbook workbook, DataFormatter formatter) {
        String labeledCompany = findLabelValue(workbook, formatter, Set.of("customer", "customername", "customercompany", "company", "companyname", "billto"));
        String labeledAddress = findAddressBlockValue(workbook, formatter, Set.of("customeraddress", "companyaddress", "billingaddress"));
        if (!blank(labeledCompany)) {
            return new CompanyBlock(labeledCompany, labeledAddress);
        }
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            var sheet = workbook.getSheetAt(si);
            int lastRow = Math.min(sheet.getLastRowNum(), 15);
            for (int ri = 0; ri <= lastRow; ri++) {
                Row row = sheet.getRow(ri);
                if (row == null) continue;
                for (Cell cell : row) {
                    String value = cellText(cell, formatter).trim();
                    if (value.isBlank() || value.equalsIgnoreCase("REQUEST FOR QUOTE")) continue;
                    String low = value.toLowerCase(Locale.ROOT);
                    boolean looksLikeCompany = low.contains("corporation") || low.endsWith(" corp") || low.endsWith(" corp.")
                            || low.endsWith(" llc") || low.endsWith(" inc") || low.endsWith(" inc.")
                            || low.endsWith(" ltd") || low.endsWith(" ltd.") || low.contains("limited")
                            || low.contains("pvt") || low.contains("gmbh");
                    if (!looksLikeCompany) continue;
                    List<String> addressLines = new ArrayList<>();
                    int colIdx = cell.getColumnIndex();
                    for (int nextRi = ri + 1; nextRi <= Math.min(ri + 5, sheet.getLastRowNum()); nextRi++) {
                        Row nextRow = sheet.getRow(nextRi);
                        if (nextRow == null) break;
                        String nextVal = cellText(nextRow.getCell(colIdx), formatter).trim();
                        if (nextVal.isBlank() || KNOWN_HEADER_LABELS.contains(normalize(nextVal)) || isItemHeaderRow(nextRow, formatter)) break;
                        addressLines.add(nextVal);
                    }
                    return new CompanyBlock(value, addressLines.isEmpty() ? null : String.join("\n", addressLines));
                }
            }
        }
        return new CompanyBlock(null, null);
    }

    private boolean isItemHeaderRow(Row row, DataFormatter formatter) {
        boolean hasItem = false;
        boolean hasDesc = false;
        for (Cell c : row) {
            String n = normalize(cellText(c, formatter));
            if (n.equals("item") || n.equals("item#") || n.equals("itemnumber")) hasItem = true;
            if (n.contains("description")) hasDesc = true;
        }
        return hasItem && hasDesc;
    }

    private boolean isFooterRow(Row row, DataFormatter formatter) {
        for (Cell c : row) {
            String low = cellText(c, formatter).toLowerCase(Locale.ROOT).trim();
            if (low.startsWith("a competitive procurement") || low.startsWith("terms of payment")
                    || low.startsWith("terms & conditions") || low.startsWith("note:") || low.startsWith("note :")
                    || low.startsWith("to submit your bid")) {
                return true;
            }
        }
        return false;
    }

    private String quotationManagerHint(Workbook workbook, DataFormatter formatter, String prValue) {
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            for (Row row : workbook.getSheetAt(si)) {
                for (Cell cell : row) {
                    String label = normalize(cellText(cell, formatter));
                    if (label.equals("pr#") || label.equals("prnumber") || label.equals("prno")) {
                        int prValCol = -1;
                        for (int col = cell.getColumnIndex() + 1; col < Math.min(cell.getColumnIndex() + 4, row.getLastCellNum()); col++) {
                            String candidate = cellText(row.getCell(col), formatter).trim();
                            if (!candidate.isBlank()) {
                                prValCol = col;
                                break;
                            }
                        }
                        int startCol = prValCol >= 0 ? prValCol + 1 : cell.getColumnIndex() + 2;
                        for (int col = startCol; col < Math.min(startCol + 4, row.getLastCellNum()); col++) {
                            String hint = cellText(row.getCell(col), formatter).trim();
                            if (!hint.isBlank() && !hint.equals(prValue)) return hint;
                        }
                    }
                }
            }
        }
        return null;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (blank(value)) return null;
        String text = value.trim().replaceAll("\\s+(EDT|EST|UTC|GMT|IST)$", "");
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("dd-MM-yyyy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("dd-MM-yyyy H:mm:ss", Locale.US),
                DateTimeFormatter.ofPattern("MM-dd-yyyy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy H:mm", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try { return LocalDateTime.parse(text, formatter); }
            catch (DateTimeParseException ignored) { }
        }
        LocalDate dateOnly = parseLocalDate(text);
        return dateOnly == null ? null : dateOnly.atStartOfDay();
    }

    private LocalDate parseLocalDate(String value) {
        if (blank(value)) return null;
        String text = value.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
                DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
                DateTimeFormatter.ISO_LOCAL_DATE)) {
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
        Matcher matcher = Pattern.compile("(?im)^.{0,100}\\b(?:Corporation|Corp\\.?|LLC|Inc\\.?|Ltd\\.?|Limited|Pvt\\.?\\s*Ltd\\.?)\\b.{0,100}$").matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private String firstNonBlankLine(String value) {
        if (blank(value)) return null;
        return value.lines().map(String::trim).filter(line -> !line.isBlank() && !line.contains("@"))
                .findFirst().orElse(null);
    }

    private String firstMatch(Pattern pattern, String text, int group) {
        if (blank(text)) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    private String afterFirst(String text, String marker) {
        int index = text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
        return index < 0 ? null : text.substring(index).trim();
    }

    private String idFromRaw(Map<String, String> raw) {
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String n = normalize(entry.getValue());
            if (n.equals("pr#") || n.equals("prnumber") || n.equals("rfq#") || n.equals("rfqnumber")) {
                String address = entry.getKey().substring(entry.getKey().lastIndexOf('!') + 1);
                String next = raw.get(entry.getKey().substring(0, entry.getKey().lastIndexOf('!') + 1) + columnAfter(address));
                if (next != null && !next.isBlank()) return next.trim();
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
    private record CompanyBlock(String company, String address) { }
}
