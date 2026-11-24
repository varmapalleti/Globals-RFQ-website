package com.globalrfq.rfq.intake.dto;

import com.globalrfq.rfq.intake.entity.CustomerDecision;
import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RfqResponse(
        String id,
        String sourceSystem,
        String sourceRfqId,
        String sourceReference,
        String sourceQuotationManagerName,
        String customerName,
        String customerCompany,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        String requestTitle,
        LocalDateTime dueAtLocal,
        String dueTimeZone,
        LocalDate requestedDeliveryDate,
        String shipToText,
        String sourceUrl,
        String termsAndConditions,
        RfqStatus status,
        CustomerDecision customerDecision,
        boolean quotationManagerCanAssignReviewer,
        long version,
        Instant createdAt,
        List<LineItem> lineItems,
        List<Assignment> assignments
) {
    public record LineItem(String sourceLineId, int lineNumber, String description, BigDecimal requestedQuantity,
                           String unitOfMeasure, String customerPartNumber, String requestedManufacturer,
                           String requestedPartNumber, String procurementCategory, Boolean taaRequired,
                           String technicalSpecifications, String sourceDataJson) { }
    public record Assignment(String role, Long userId, Long assignedByUserId, String reason, Instant assignedAt) { }

    public static RfqResponse from(Rfq rfq, List<com.globalrfq.rfq.intake.entity.RfqAssignment> assignments) {
        var lines = rfq.getLineItems().stream().map(line -> new LineItem(line.getSourceLineId(), line.getLineNumber(),
                line.getDescription(), line.getRequestedQuantity(), line.getUnitOfMeasure(), line.getCustomerPartNumber(),
                line.getRequestedManufacturer(), line.getRequestedPartNumber(), line.getProcurementCategory(),
                line.getTaaRequired(), line.getTechnicalSpecifications(),
                line.getSourceDataJson())).toList();
        var assignmentResponses = assignments.stream().map(assignment -> new Assignment(
                assignment.getAssignmentRole().name(), assignment.getAssignedUserId(), assignment.getAssignedByUserId(),
                assignment.getReason(), assignment.getAssignedAt())).toList();
        return new RfqResponse(rfq.getPublicId(), rfq.getSourceSystem(), rfq.getSourceRfqId(), rfq.getSourceReference(),
                rfq.getSourceQuotationManagerName(), rfq.getCustomerName(), rfq.getCustomerCompany(), rfq.getBuyerName(), rfq.getBuyerEmail(), rfq.getBuyerPhone(),
                rfq.getRequestTitle(), rfq.getDueAtLocal(), rfq.getDueTimeZone(), rfq.getRequestedDeliveryDate(),
                rfq.getShipToText(), rfq.getSourceUrl(), rfq.getTermsAndConditions(), rfq.getStatus(),
                rfq.getCustomerDecision(), rfq.isQuotationManagerCanAssignReviewer(), rfq.getVersion(), rfq.getCreatedAt(), lines, assignmentResponses);
    }
}
