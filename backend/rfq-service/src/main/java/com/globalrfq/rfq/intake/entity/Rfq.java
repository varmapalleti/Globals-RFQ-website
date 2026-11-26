package com.globalrfq.rfq.intake.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rfqs")
public class Rfq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "source_system", nullable = false, length = 80)
    private String sourceSystem;

    @Column(name = "source_rfq_id", nullable = false, length = 120)
    private String sourceRfqId;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "source_quotation_manager_name", length = 190)
    private String sourceQuotationManagerName;

    @Column(name = "customer_name", nullable = false, length = 190)
    private String customerName;

    @Column(name = "customer_company", length = 190)
    private String customerCompany;

    @Column(name = "buyer_name", length = 150)
    private String buyerName;

    @Column(name = "buyer_email", length = 190)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 40)
    private String buyerPhone;

    @Column(name = "request_title", length = 255)
    private String requestTitle;

    @Column(name = "due_at_local")
    private LocalDateTime dueAtLocal;

    @Column(name = "due_time_zone", length = 80)
    private String dueTimeZone;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    @Column(name = "ship_to_text", columnDefinition = "text")
    private String shipToText;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "terms_and_conditions", columnDefinition = "longtext")
    private String termsAndConditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_data_json", columnDefinition = "json")
    private String sourceDataJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RfqStatus status = RfqStatus.IMPORTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_decision", nullable = false, length = 32)
    private CustomerDecision customerDecision = CustomerDecision.PENDING;

    @Column(name = "quotation_manager_can_assign_reviewer", nullable = false)
    private boolean quotationManagerCanAssignReviewer;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.PERSIST)
    private List<RfqLineItem> lineItems = new ArrayList<>();

    protected Rfq() { }

    public Rfq(String sourceSystem, String sourceRfqId, String sourceReference, String sourceQuotationManagerName, String customerName,
               String customerCompany, String buyerName, String buyerEmail, String buyerPhone,
               String requestTitle, LocalDateTime dueAtLocal, String dueTimeZone,
               LocalDate requestedDeliveryDate, String shipToText, String sourceUrl,
               String termsAndConditions, String sourceDataJson, Long createdBy) {
        this.sourceSystem = sourceSystem;
        this.sourceRfqId = sourceRfqId;
        this.sourceReference = sourceReference;
        this.sourceQuotationManagerName = sourceQuotationManagerName;
        this.customerName = customerName;
        this.customerCompany = customerCompany;
        this.buyerName = buyerName;
        this.buyerEmail = buyerEmail;
        this.buyerPhone = buyerPhone;
        this.requestTitle = requestTitle;
        this.dueAtLocal = dueAtLocal;
        this.dueTimeZone = dueTimeZone;
        this.requestedDeliveryDate = requestedDeliveryDate;
        this.shipToText = shipToText;
        this.sourceUrl = sourceUrl;
        this.termsAndConditions = termsAndConditions;
        this.sourceDataJson = sourceDataJson;
        this.createdBy = createdBy;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    public void addLineItem(RfqLineItem item) {
        item.attachTo(this);
        lineItems.add(item);
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getSourceSystem() { return sourceSystem; }
    public String getSourceRfqId() { return sourceRfqId; }
    public String getSourceReference() { return sourceReference; }
    public String getSourceQuotationManagerName() { return sourceQuotationManagerName; }
    public String getCustomerName() { return customerName; }
    public String getCustomerCompany() { return customerCompany; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public String getBuyerPhone() { return buyerPhone; }
    public String getRequestTitle() { return requestTitle; }
    public LocalDateTime getDueAtLocal() { return dueAtLocal; }
    public String getDueTimeZone() { return dueTimeZone; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public String getShipToText() { return shipToText; }
    public String getSourceUrl() { return sourceUrl; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public String getSourceDataJson() { return sourceDataJson; }
    public RfqStatus getStatus() { return status; }
    public CustomerDecision getCustomerDecision() { return customerDecision; }
    public boolean isQuotationManagerCanAssignReviewer() { return quotationManagerCanAssignReviewer; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<RfqLineItem> getLineItems() { return List.copyOf(lineItems); }

    public void setStatus(RfqStatus status) { this.status = status; }
    public void setCustomerDecision(CustomerDecision customerDecision) { this.customerDecision = customerDecision; }
    public void setQuotationManagerCanAssignReviewer(boolean allowed) { this.quotationManagerCanAssignReviewer = allowed; }
}
