package com.globalrfq.rfq.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "rfq_import_previews")
public class RfqImportPreview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preview_token", nullable = false, unique = true, length = 36)
    private String previewToken;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @Column(name = "stored_object_key", nullable = false, unique = true, length = 36)
    private String storedObjectKey;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_rfq_json", nullable = false, columnDefinition = "json")
    private String parsedRfqJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mappings_json", nullable = false, columnDefinition = "json")
    private String fieldMappingsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", nullable = false, columnDefinition = "json")
    private String warningsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.READY;

    @Column(name = "consumed_rfq_public_id", length = 36)
    private String consumedRfqPublicId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RfqImportPreview() { }

    public RfqImportPreview(String previewToken, Long createdByUserId, String sourceFileName, String storedObjectKey,
                            String fileSha256, String parsedRfqJson, String fieldMappingsJson,
                            String warningsJson, Instant expiresAt) {
        this.previewToken = previewToken;
        this.createdByUserId = createdByUserId;
        this.sourceFileName = sourceFileName;
        this.storedObjectKey = storedObjectKey;
        this.fileSha256 = fileSha256;
        this.parsedRfqJson = parsedRfqJson;
        this.fieldMappingsJson = fieldMappingsJson;
        this.warningsJson = warningsJson;
        this.expiresAt = expiresAt;
    }

    public void consume(String rfqPublicId) {
        this.status = Status.CONSUMED;
        this.consumedRfqPublicId = rfqPublicId;
    }

    public void expire() { this.status = Status.EXPIRED; }

    public Long getId() { return id; }
    public String getPreviewToken() { return previewToken; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public String getSourceFileName() { return sourceFileName; }
    public String getStoredObjectKey() { return storedObjectKey; }
    public String getFileSha256() { return fileSha256; }
    public String getParsedRfqJson() { return parsedRfqJson; }
    public String getFieldMappingsJson() { return fieldMappingsJson; }
    public String getWarningsJson() { return warningsJson; }
    public Status getStatus() { return status; }
    public String getConsumedRfqPublicId() { return consumedRfqPublicId; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }

    public enum Status { READY, CONSUMED, EXPIRED }
}
