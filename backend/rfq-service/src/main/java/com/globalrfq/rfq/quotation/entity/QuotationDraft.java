package com.globalrfq.rfq.quotation.entity;

import com.globalrfq.rfq.intake.entity.Rfq;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "rfq_quotation_drafts")
public class QuotationDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false, unique = true)
    private Rfq rfq;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_json", nullable = false, columnDefinition = "json")
    private String draftJson;

    @Column(name = "saved_by_user_id", nullable = false)
    private Long savedByUserId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected QuotationDraft() { }

    public QuotationDraft(Rfq rfq, String draftJson, Long savedByUserId) {
        this.rfq = rfq;
        this.draftJson = draftJson;
        this.savedByUserId = savedByUserId;
        this.savedAt = Instant.now();
    }

    public void replace(String draftJson, Long savedByUserId) {
        this.draftJson = draftJson;
        this.savedByUserId = savedByUserId;
    }

    public String getDraftJson() { return draftJson; }
    public Instant getSavedAt() { return savedAt; }
    public long getVersion() { return version; }
}
