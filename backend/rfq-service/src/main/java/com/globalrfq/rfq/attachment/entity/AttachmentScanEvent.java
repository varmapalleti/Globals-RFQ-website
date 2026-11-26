package com.globalrfq.rfq.attachment.entity;

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

@Entity
@Table(name = "rfq_attachment_scan_events")
public class AttachmentScanEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private RfqAttachment attachment;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_state", nullable = false, length = 16)
    private AttachmentScanState scanState;

    @Column(name = "scanner_name", nullable = false, length = 120)
    private String scannerName;

    @Column(name = "scan_reference", length = 255)
    private String scanReference;

    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    protected AttachmentScanEvent() { }

    public AttachmentScanEvent(
            RfqAttachment attachment,
            AttachmentScanState scanState,
            String scannerName,
            String scanReference ) {

        this.attachment = attachment;
        this.scanState = scanState;
        this.scannerName = scannerName;
        this.scanReference = scanReference;
    }
}
