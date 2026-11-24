package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.attachment.dto.RfqAttachmentResponse;
import com.globalrfq.rfq.attachment.entity.AttachmentCategory;
import com.globalrfq.rfq.attachment.service.RfqAttachmentService;
import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.CreateRfqRequest;
import com.globalrfq.rfq.intake.dto.RfqImportConfirmRequest;
import com.globalrfq.rfq.intake.dto.RfqImportPreviewResponse;
import com.globalrfq.rfq.intake.dto.RfqImportResultResponse;
import com.globalrfq.rfq.intake.dto.RfqResponse;
import com.globalrfq.rfq.intake.entity.RfqImportPreview;
import com.globalrfq.rfq.intake.repository.RfqImportPreviewRepository;
import com.globalrfq.rfq.config.RfqFileProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RfqImportCoordinator {
    private final RfqDocumentParser parser;
    private final RfqCommandService commands;
    private final RfqAttachmentService attachments;
    private final RfqImportPreviewRepository previews;
    private final RfqImportPreviewStorage previewStorage;
    private final RfqFileProperties fileProperties;
    private final ObjectMapper mapper;

    public RfqImportCoordinator(RfqDocumentParser parser, RfqCommandService commands, RfqAttachmentService attachments,
                                RfqImportPreviewRepository previews, RfqImportPreviewStorage previewStorage,
                                RfqFileProperties fileProperties, ObjectMapper mapper) {
        this.parser = parser;
        this.commands = commands;
        this.attachments = attachments;
        this.previews = previews;
        this.previewStorage = previewStorage;
        this.fileProperties = fileProperties;
        this.mapper = mapper;
    }

    @Transactional
    public RfqImportPreviewResponse preview(MultipartFile file, String sourceSystem, String sourceRfqId, IdentityPrincipal actor) {
        requireAdmin(actor);
        RfqImportPreviewResponse parsed = parser.parse(file, sourceSystem, sourceRfqId);
        RfqImportPreviewStorage.StoredPreviewFile stored = previewStorage.store(file);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(Math.multiplyExact(fileProperties.getPreviewTtlMinutes(), 60));
        try {
            RfqImportPreview entity = new RfqImportPreview(token, actor.userId(), parsed.fileName(), stored.key(), stored.sha256(),
                    mapper.writeValueAsString(parsed.normalizedRfq()), mapper.writeValueAsString(parsed.fieldMappings()),
                    mapper.writeValueAsString(parsed.warnings()), expiresAt);
            previews.save(entity);
            deleteStageIfTransactionRollsBack(stored.key());
            return parsed.withStaging(token, expiresAt);
        } catch (Exception exception) {
            previewStorage.delete(stored.key());
            if (exception instanceof RfqException rfqException) throw rfqException;
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The RFQ import preview could not be saved safely");
        }
    }

    @Transactional
    public RfqImportResultResponse importAndStore(RfqImportConfirmRequest request, IdentityPrincipal actor) {
        requireAdmin(actor);
        RfqImportPreview preview = previews.findByTokenForUpdate(request.previewToken())
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "RFQ import preview was not found or has expired"));
        if (!preview.getCreatedByUserId().equals(actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only the Admin who created this import preview can confirm it");
        }
        if (preview.getStatus() != RfqImportPreview.Status.READY || !preview.getExpiresAt().isAfter(Instant.now())) {
            throw new RfqException(RfqErrorCode.INVALID_WORKFLOW_TRANSITION, "RFQ import preview is no longer available; create a new preview");
        }
        Path stagedFile = previewStorage.verifiedPath(preview.getStoredObjectKey(), preview.getFileSha256());
        try {
            CreateRfqRequest parsedRequest;
            try {
                parsedRequest = mapper.readValue(preview.getParsedRfqJson(), CreateRfqRequest.class);
            } catch (Exception exception) {
                throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The saved RFQ import preview could not be read");
            }
            CreateRfqRequest normalized = request.normalizedRfq() == null
                    ? parsedRequest : preserveSourceData(request.normalizedRfq(), parsedRequest);
            RfqResponse rfq = commands.create(normalized, actor);
            RfqAttachmentResponse attachment = attachments.uploadFromPath(rfq.id(), stagedFile, preview.getSourceFileName(),
                    AttachmentCategory.CUSTOMER_REQUEST, actor);
            preview.consume(rfq.id());
            previews.save(preview);
            deleteStageAfterTransactionCommits(preview.getStoredObjectKey());
            return new RfqImportResultResponse(rfq, attachment);
        } finally {
            previewStorage.deleteVerifiedCopy(stagedFile);
        }
    }

    private CreateRfqRequest preserveSourceData(CreateRfqRequest corrected, CreateRfqRequest parsed) {
        Map<String, Object> sourceData = new java.util.LinkedHashMap<>();
        if (corrected.sourceData() != null) sourceData.putAll(corrected.sourceData());
        if (parsed.sourceData() != null && !parsed.sourceData().isEmpty()) {
            sourceData.put("importOriginalSourceData", parsed.sourceData());
        }
        Map<String, Map<String, Object>> originalLineData = new java.util.HashMap<>();
        parsed.lineItems().forEach(line -> originalLineData.put(line.sourceLineId(), line.sourceData()));
        var correctedLines = corrected.lineItems().stream().map(line -> {
            Map<String, Object> lineData = new java.util.LinkedHashMap<>();
            if (line.sourceData() != null) lineData.putAll(line.sourceData());
            Map<String, Object> original = originalLineData.get(line.sourceLineId());
            if (original != null && !original.isEmpty()) lineData.put("importOriginalSourceData", original);
            return new CreateRfqRequest.CreateRfqLineItemRequest(line.sourceLineId(), line.lineNumber(), line.description(),
                    line.requestedQuantity(), line.unitOfMeasure(), line.customerPartNumber(), line.requestedManufacturer(),
                    line.requestedPartNumber(), line.procurementCategory(), line.taaRequired(),
                    line.technicalSpecifications(), lineData);
        }).toList();
        return new CreateRfqRequest(corrected.sourceSystem(), corrected.sourceRfqId(), corrected.sourceReference(),
                corrected.sourceQuotationManagerName(), corrected.customerName(), corrected.customerCompany(),
                corrected.buyerName(), corrected.buyerEmail(), corrected.buyerPhone(), corrected.requestTitle(),
                corrected.dueAtLocal(), corrected.dueTimeZone(), corrected.requestedDeliveryDate(), corrected.shipToText(),
                corrected.sourceUrl(), corrected.termsAndConditions(), sourceData, correctedLines);
    }

    private void deleteStageIfTransactionRollsBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) previewStorage.delete(key);
            }
        });
    }

    private void deleteStageAfterTransactionCommits(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { previewStorage.delete(key); }
        });
    }

    private void requireAdmin(IdentityPrincipal actor) {
        if (!actor.roles().contains("ADMIN") && !actor.roles().contains("SUPER_ADMIN")) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "Only an Admin can import customer RFQs");
        }
    }
}
