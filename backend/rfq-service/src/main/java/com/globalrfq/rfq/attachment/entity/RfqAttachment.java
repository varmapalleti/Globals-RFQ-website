package com.globalrfq.rfq.attachment.entity;

import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqLineItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rfq_attachments")
public class RfqAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_line_item_id")
    private RfqLineItem lineItem;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_object_key", nullable = false, unique = true, length = 36)
    private String storedObjectKey;

    @Column(name = "file_extension", nullable = false, length = 12)
    private String fileExtension;

    @Column(name = "detected_media_type", nullable = false, length = 100)
    private String detectedMediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AttachmentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_state", nullable = false, length = 16)
    private AttachmentScanState scanState = AttachmentScanState.PENDING_SCAN;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RfqAttachment() { }

    public RfqAttachment(Rfq rfq, RfqLineItem lineItem, String originalFileName, String storedObjectKey, String fileExtension,
                         String detectedMediaType, long byteSize, String sha256,
                         AttachmentCategory category, Long uploadedByUserId) {
        this.rfq = rfq;
        this.lineItem = lineItem;
        this.originalFileName = originalFileName;
        this.storedObjectKey = storedObjectKey;
        this.fileExtension = fileExtension;
        this.detectedMediaType = detectedMediaType;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.category = category;
        this.uploadedByUserId = uploadedByUserId;
    }

    public void setScanState(AttachmentScanState scanState) { this.scanState = scanState; }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Rfq getRfq() { return rfq; }
    public RfqLineItem getLineItem() { return lineItem; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStoredObjectKey() { return storedObjectKey; }
    public String getFileExtension() { return fileExtension; }
    public String getDetectedMediaType() { return detectedMediaType; }
    public long getByteSize() { return byteSize; }
    public String getSha256() { return sha256; }
    public AttachmentCategory getCategory() { return category; }
    public AttachmentScanState getScanState() { return scanState; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
