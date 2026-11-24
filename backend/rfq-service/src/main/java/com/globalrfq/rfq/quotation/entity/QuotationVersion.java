package com.globalrfq.rfq.quotation.entity;

import com.globalrfq.rfq.intake.entity.Rfq;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Submitted quotation snapshot. This entity intentionally has no mutators. */
@Entity
@Table(name = "rfq_quotation_versions")
public class QuotationVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_sha256", nullable = false, length = 64)
    private String requestSha256;

    @Column(length = 3)
    private String currency;

    @Column(precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal freight;

    @Column(name = "other_charges", nullable = false, precision = 18, scale = 2)
    private BigDecimal otherCharges;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discount;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "grand_total", precision = 18, scale = 2)
    private BigDecimal grandTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quotation_json", nullable = false, columnDefinition = "json")
    private String quotationJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mandatory_fields_snapshot_json", nullable = false, columnDefinition = "json")
    private String mandatoryFieldsSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_mandatory_fields_json", nullable = false, columnDefinition = "json")
    private String missingMandatoryFieldsJson;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    protected QuotationVersion() { }

    public QuotationVersion(Rfq rfq, int versionNumber, String idempotencyKey, String requestSha256,
                            String currency, BigDecimal subtotal, BigDecimal freight, BigDecimal otherCharges,
                            BigDecimal discount, BigDecimal taxAmount, BigDecimal grandTotal,
                            String quotationJson, String mandatoryFieldsSnapshotJson,
                            String missingMandatoryFieldsJson, Long submittedByUserId) {
        this.rfq = rfq;
        this.versionNumber = versionNumber;
        this.idempotencyKey = idempotencyKey;
        this.requestSha256 = requestSha256;
        this.currency = currency;
        this.subtotal = subtotal;
        this.freight = freight;
        this.otherCharges = otherCharges;
        this.discount = discount;
        this.taxAmount = taxAmount;
        this.grandTotal = grandTotal;
        this.quotationJson = quotationJson;
        this.mandatoryFieldsSnapshotJson = mandatoryFieldsSnapshotJson;
        this.missingMandatoryFieldsJson = missingMandatoryFieldsJson;
        this.submittedByUserId = submittedByUserId;
    }

    public Long getId() { return id; }
    public Rfq getRfq() { return rfq; }
    public int getVersionNumber() { return versionNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestSha256() { return requestSha256; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getFreight() { return freight; }
    public BigDecimal getOtherCharges() { return otherCharges; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public String getQuotationJson() { return quotationJson; }
    public String getMandatoryFieldsSnapshotJson() { return mandatoryFieldsSnapshotJson; }
    public String getMissingMandatoryFieldsJson() { return missingMandatoryFieldsJson; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public Instant getSubmittedAt() { return submittedAt; }
}
