package com.globalrfq.rfq.attachment.service;

import com.globalrfq.rfq.attachment.dto.AttachmentScanResultRequest;
import com.globalrfq.rfq.attachment.dto.PendingAttachmentScanResponse;
import com.globalrfq.rfq.attachment.dto.RfqAttachmentResponse;
import com.globalrfq.rfq.attachment.entity.AttachmentCategory;
import com.globalrfq.rfq.attachment.entity.AttachmentScanEvent;
import com.globalrfq.rfq.attachment.entity.AttachmentScanState;
import com.globalrfq.rfq.attachment.entity.RfqAttachment;
import com.globalrfq.rfq.attachment.repository.AttachmentScanEventRepository;
import com.globalrfq.rfq.attachment.repository.RfqAttachmentRepository;
import com.globalrfq.rfq.attachment.storage.RfqObjectStorage;
import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import com.globalrfq.rfq.intake.entity.AssignmentRole;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqAssignment;
import com.globalrfq.rfq.intake.repository.RfqAssignmentRepository;
import com.globalrfq.rfq.intake.repository.RfqRepository;
import com.globalrfq.rfq.intake.repository.RfqLineItemRepository;
import com.globalrfq.rfq.workflow.entity.RfqWorkflowEvent;
import com.globalrfq.rfq.workflow.repository.RfqWorkflowEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class RfqAttachmentService {
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private final RfqFileProperties properties;
    private final RfqRepository rfqs;
    private final RfqLineItemRepository lineItems;
    private final RfqAssignmentRepository assignments;
    private final RfqAttachmentRepository attachments;
    private final AttachmentScanEventRepository scanEvents;
    private final RfqWorkflowEventRepository workflowEvents;
    private final RfqObjectStorage objectStorage;
    private final ObjectMapper mapper;

    public RfqAttachmentService(RfqFileProperties properties, RfqRepository rfqs, RfqLineItemRepository lineItems,
                                RfqAssignmentRepository assignments, RfqAttachmentRepository attachments,
                                AttachmentScanEventRepository scanEvents, RfqWorkflowEventRepository workflowEvents,
                                RfqObjectStorage objectStorage, ObjectMapper mapper) {
        this.properties = properties;
        this.rfqs = rfqs;
        this.lineItems = lineItems;
        this.assignments = assignments;
        this.attachments = attachments;
        this.scanEvents = scanEvents;
        this.workflowEvents = workflowEvents;
        this.objectStorage = objectStorage;
        this.mapper = mapper;
    }

    @Transactional
    public RfqAttachmentResponse upload(String rfqPublicId, MultipartFile file, AttachmentCategory category,
                                        Integer lineNumber, IdentityPrincipal actor) {
        Rfq rfq = findRfq(rfqPublicId);
        requireUploadAccess(rfq, category, actor);
        var lineItem = findLineItem(rfq, lineNumber);
        if (file == null || file.isEmpty()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Select a non-empty file");
        if (file.getSize() > properties.getMaxAttachmentSizeBytes()) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The attachment exceeds the configured file size limit");
        }
        try (InputStream input = file.getInputStream()) {
            return storeAttachment(rfq, lineItem, file.getOriginalFilename(), input, category, actor);
        } catch (IOException exception) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The attachment could not be read");
        }
    }

    @Transactional
    public RfqAttachmentResponse uploadFromPath(String rfqPublicId, Path sourcePath, String fileName,
                                                AttachmentCategory category, IdentityPrincipal actor) {
        Rfq rfq = findRfq(rfqPublicId);
        requireUploadAccess(rfq, category, actor);
        try {
            if (!Files.isRegularFile(sourcePath) || Files.size(sourcePath) == 0) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The staged import source file is missing or empty");
            }
            if (Files.size(sourcePath) > properties.getMaxAttachmentSizeBytes()) {
                throw new RfqException(RfqErrorCode.PAYLOAD_TOO_LARGE, "The attachment exceeds the configured file size limit");
            }
            try (InputStream input = Files.newInputStream(sourcePath)) {
                return storeAttachment(rfq, null, fileName, input, category, actor);
            }
        } catch (IOException exception) {
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The staged import source file could not be read");
        }
    }

    private RfqAttachmentResponse storeAttachment(Rfq rfq, com.globalrfq.rfq.intake.entity.RfqLineItem lineItem,
                                                   String originalFileName, InputStream input,
                                                   AttachmentCategory category, IdentityPrincipal actor) {
        String fileName = safeFileName(originalFileName);
        String extension = extension(fileName);
        if (!properties.getAllowedAttachmentTypes().contains(extension)) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "This attachment file type is not allowed");
        }
        String key = UUID.randomUUID().toString();
        Path validationCopy = null;
        try {
            validationCopy = Files.createTempFile("rfq-attachment-", ".upload");
            CopyDigest copy = copyAndHash(input, validationCopy);
            String detectedType = inspectContent(validationCopy, extension);
            objectStorage.put(RfqObjectStorage.ObjectKind.ATTACHMENT, key, validationCopy);
            RfqAttachment attachment = attachments.save(new RfqAttachment(rfq, lineItem, fileName, key, extension,
                    detectedType, copy.size(), copy.sha256(), category, actor.userId()));
            appendEvent(rfq, "ATTACHMENT_UPLOADED", actor, Map.of("attachmentId", attachment.getPublicId(),
                    "category", category.name(), "fileName", fileName, "sizeBytes", copy.size(), "sha256", copy.sha256()));
            cleanupIfTransactionRollsBack(key);
            return RfqAttachmentResponse.from(attachment);
        } catch (RuntimeException | IOException exception) {
            // The key is unique, so deleting is safe even when the storage operation timed out after writing.
            deleteStoredQuietly(RfqObjectStorage.ObjectKind.ATTACHMENT, key);
            if (exception instanceof RfqException rfqException) throw rfqException;
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The attachment could not be validated or stored");
        } finally {
            deleteQuietly(validationCopy);
        }
    }

    @Transactional(readOnly = true)
    public List<RfqAttachmentResponse> list(String rfqPublicId, IdentityPrincipal actor) {
        Rfq rfq = findRfq(rfqPublicId);
        requireReadAccess(rfq, actor);
        return attachments.findByRfqIdOrderByCreatedAtAsc(rfq.getId()).stream().map(RfqAttachmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DownloadAttachment download(String rfqPublicId, String attachmentPublicId, IdentityPrincipal actor) {
        Rfq rfq = findRfq(rfqPublicId);
        requireReadAccess(rfq, actor);
        RfqAttachment attachment = attachments.findByPublicIdAndRfqId(attachmentPublicId, rfq.getId())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ attachment was not found"));
        if (attachment.getScanState() == AttachmentScanState.INFECTED) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "The attachment was rejected by the security scanner");
        }
        if (attachment.getScanState() != AttachmentScanState.CLEAN) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "The attachment is unavailable until its malware scan is complete");
        }
        if (!sha256(RfqObjectStorage.ObjectKind.ATTACHMENT, attachment.getStoredObjectKey()).equals(attachment.getSha256())) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The stored attachment failed its integrity check");
        }
        try {
            return new DownloadAttachment(attachment.getOriginalFileName(), attachment.getDetectedMediaType(),
                    new InputStreamResource(objectStorage.open(RfqObjectStorage.ObjectKind.ATTACHMENT, attachment.getStoredObjectKey())));
        } catch (IOException exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The stored attachment could not be opened");
        }
    }

    @Transactional(readOnly = true)
    public List<PendingAttachmentScanResponse> pendingScans() {
        return attachments.findTop100ByScanStateOrderByCreatedAtAsc(AttachmentScanState.PENDING_SCAN).stream()
                .map(PendingAttachmentScanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Resource scannerContent(String attachmentPublicId) {
        RfqAttachment attachment = attachments.findByPublicId(attachmentPublicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Attachment was not found"));
        if (attachment.getScanState() != AttachmentScanState.PENDING_SCAN) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "Only an attachment awaiting scan can be retrieved by the scanner");
        }
        if (!sha256(RfqObjectStorage.ObjectKind.ATTACHMENT, attachment.getStoredObjectKey()).equals(attachment.getSha256())) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The stored attachment failed its integrity check");
        }
        try {
            return new InputStreamResource(objectStorage.open(RfqObjectStorage.ObjectKind.ATTACHMENT, attachment.getStoredObjectKey()));
        } catch (IOException exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The stored attachment could not be opened");
        }
    }

    @Transactional
    public void recordScan(String attachmentPublicId, AttachmentScanResultRequest request) {
        if (request.state() == AttachmentScanState.PENDING_SCAN) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Scanner result must be CLEAN or INFECTED");
        }
        RfqAttachment attachment = attachments.findByPublicId(attachmentPublicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Attachment was not found"));
        if (attachment.getScanState() != AttachmentScanState.PENDING_SCAN) {
            if (attachment.getScanState() == request.state()) return;
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "A final malware scan result has already been recorded");
        }
        attachment.setScanState(request.state());
        scanEvents.save(new AttachmentScanEvent(attachment, request.state(), request.scannerName().trim(), request.scanReference()));
        attachments.save(attachment);
    }

    private Rfq findRfq(String publicId) {
        return rfqs.findByPublicId(publicId)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ was not found"));
    }

    private com.globalrfq.rfq.intake.entity.RfqLineItem findLineItem(Rfq rfq, Integer lineNumber) {
        if (lineNumber == null) return null;
        return lineItems.findByRfqIdAndLineNumber(rfq.getId(), lineNumber)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND,
                        "The requested RFQ line item was not found"));
    }

    private void requireUploadAccess(Rfq rfq, AttachmentCategory category, IdentityPrincipal actor) {
        boolean admin = isAdmin(actor);
        if (category == AttachmentCategory.CUSTOMER_REQUEST || category == AttachmentCategory.CUSTOMER_RESPONSE) {
            if (!admin) throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can upload customer source documents or responses");
            return;
        }
        AssignmentRole needed = switch (category) {
            case QUOTATION_SUPPORT -> AssignmentRole.QUOTATION_MANAGER;
            case REVIEW_SUPPORT -> AssignmentRole.REVIEWER;
            default -> null;
        };
        if (admin) return;
        if (needed == null || !isAssigned(rfq, needed, actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have permission to upload this attachment category");
        }
    }

    private void requireReadAccess(Rfq rfq, IdentityPrincipal actor) {
        if (!isAdmin(actor) && !assignments.existsByRfqIdAndAssignedUserIdAndActiveTrue(rfq.getId(), actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You do not have access to this RFQ");
        }
    }

    private boolean isAssigned(Rfq rfq, AssignmentRole role, Long userId) {
        return assignments.findByRfqIdAndAssignmentRoleAndActiveTrue(rfq.getId(), role)
                .map(assignment -> assignment.getAssignedUserId().equals(userId)).orElse(false);
    }

    private boolean isAdmin(IdentityPrincipal actor) {
        return actor.roles().contains("ADMIN") || actor.roles().contains("SUPER_ADMIN");
    }

    private CopyDigest copyAndHash(InputStream input, Path output) throws IOException {
        MessageDigest digest = digest();
        long copied = 0;
        try (InputStream in = input; var out = Files.newOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                copied += read;
                if (copied > properties.getMaxAttachmentSizeBytes()) {
                    throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The attachment exceeds the configured file size limit");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return new CopyDigest(copied, HexFormat.of().formatHex(digest.digest()));
    }

    private String inspectContent(Path path, String extension) throws IOException {
        byte[] prefix = new byte[16];
        int count;
        try (InputStream input = Files.newInputStream(path)) { count = input.read(prefix); }
        if (count < 0) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Attachment is empty");
        boolean valid;
        String mediaType;
        switch (extension) {
            case "pdf" -> { valid = startsWith(prefix, count, new byte[]{'%', 'P', 'D', 'F', '-'}); mediaType = "application/pdf"; }
            case "png" -> { valid = startsWith(prefix, count, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}); mediaType = "image/png"; }
            case "jpg", "jpeg" -> { valid = count >= 3 && prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xd8 && prefix[2] == (byte) 0xff; mediaType = "image/jpeg"; }
            case "doc", "xls" -> {
                valid = startsWith(prefix, count, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
                mediaType = extension.equals("doc") ? "application/msword" : "application/vnd.ms-excel";
            }
            case "docx", "xlsx" -> {
                valid = inspectOfficeZip(path, extension);
                mediaType = extension.equals("docx")
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            case "csv", "txt" -> {
                valid = validUtf8(path);
                mediaType = extension.equals("csv") ? "text/csv" : "text/plain";
            }
            default -> throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "This attachment file type is not allowed");
        }
        if (!valid) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The file content does not match its file extension");
        if (extension.equals("pdf")) validatePdf(path);
        return mediaType;
    }

    private boolean inspectOfficeZip(Path path, String extension) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        long expanded = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var enumerator = zip.entries();
            while (enumerator.hasMoreElements()) {
                ZipEntry entry = enumerator.nextElement();
                if (++entries > 2000) return false;
                if (entry.getName().equals("[Content_Types].xml")) contentTypes = true;
                if (extension.equals("docx") && entry.getName().equals("word/document.xml")) document = true;
                if (extension.equals("xlsx") && entry.getName().equals("xl/workbook.xml")) document = true;
                long size = entry.getSize();
                if (size > 0) {
                    expanded += size;
                    if (expanded > MAX_UNCOMPRESSED_BYTES) return false;
                    long compressed = entry.getCompressedSize();
                    if (compressed > 0 && size / Math.max(compressed, 1) > 200) return false;
                }
            }
        }
        return contentTypes && document;
    }

    private boolean validUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private void validatePdf(Path path) {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000) {
                throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "PDF is encrypted or outside the allowed page range");
            }
        } catch (RfqException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "The PDF file could not be safely opened");
        }
    }

    private boolean startsWith(byte[] bytes, int count, byte[] signature) {
        if (count < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Attachment file name is required");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")) {
            throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Attachment file name is invalid");
        }
        return name;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) throw new RfqException(RfqErrorCode.VALIDATION_FAILED, "Attachment file extension is missing");
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(RfqObjectStorage.ObjectKind kind, String key) {
        try (InputStream input = objectStorage.open(kind, key)) {
            MessageDigest digest = digest();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The stored attachment could not be verified");
        }
    }

    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private void appendEvent(Rfq rfq, String type, IdentityPrincipal actor, Map<String, ?> payload) {
        try {
            workflowEvents.save(new RfqWorkflowEvent(rfq, type, actor.userId(), mapper.writeValueAsString(actor.roles().stream().sorted().toList()),
                    rfq.getStatus(), rfq.getStatus(), null, mapper.writeValueAsString(payload), MDC.get("correlationId")));
        } catch (Exception exception) {
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The attachment audit event could not be written");
        }
    }

    private void cleanupIfTransactionRollsBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteStoredQuietly(RfqObjectStorage.ObjectKind.ATTACHMENT, key);
            }
        });
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { }
    }

    private void deleteStoredQuietly(RfqObjectStorage.ObjectKind kind, String key) {
        try { objectStorage.delete(kind, key); }
        catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not remove RFQ storage object after failed operation; key={}", key, exception);
        }
    }

    public record DownloadAttachment(String fileName, String mediaType, Resource resource) { }
    private record CopyDigest(long size, String sha256) { }
}
