package com.globalrfq.rfq.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "rfq_line_items")
public class RfqLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "source_line_id", nullable = false, length = 120)
    private String sourceLineId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requestedQuantity;

    @Column(name = "unit_of_measure", length = 40)
    private String unitOfMeasure;

    @Column(name = "customer_part_number", length = 255)
    private String customerPartNumber;

    @Column(name = "requested_manufacturer", length = 255)
    private String requestedManufacturer;

    @Column(name = "requested_part_number", length = 255)
    private String requestedPartNumber;

    @Column(name = "procurement_category", length = 120)
    private String procurementCategory;

    @Column(name = "taa_required")
    private Boolean taaRequired;

    @Column(name = "technical_specifications", columnDefinition = "longtext")
    private String technicalSpecifications;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_data_json", columnDefinition = "json")
    private String sourceDataJson;

    protected RfqLineItem() { }

    public RfqLineItem(String sourceLineId, int lineNumber, String description, BigDecimal requestedQuantity,
                       String unitOfMeasure, String customerPartNumber, String requestedManufacturer,
                       String requestedPartNumber, String procurementCategory, Boolean taaRequired,
                       String technicalSpecifications, String sourceDataJson) {
        this.sourceLineId = sourceLineId;
        this.lineNumber = lineNumber;
        this.description = description;
        this.requestedQuantity = requestedQuantity;
        this.unitOfMeasure = unitOfMeasure;
        this.customerPartNumber = customerPartNumber;
        this.requestedManufacturer = requestedManufacturer;
        this.requestedPartNumber = requestedPartNumber;
        this.procurementCategory = procurementCategory;
        this.taaRequired = taaRequired;
        this.technicalSpecifications = technicalSpecifications;
        this.sourceDataJson = sourceDataJson;
    }

    void attachTo(Rfq rfq) { this.rfq = rfq; }

    public Long getId() { return id; }
    public String getSourceLineId() { return sourceLineId; }
    public int getLineNumber() { return lineNumber; }
    public String getDescription() { return description; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public String getCustomerPartNumber() { return customerPartNumber; }
    public String getRequestedManufacturer() { return requestedManufacturer; }
    public String getRequestedPartNumber() { return requestedPartNumber; }
    public String getProcurementCategory() { return procurementCategory; }
    public Boolean getTaaRequired() { return taaRequired; }
    public String getTechnicalSpecifications() { return technicalSpecifications; }
    public String getSourceDataJson() { return sourceDataJson; }
}
