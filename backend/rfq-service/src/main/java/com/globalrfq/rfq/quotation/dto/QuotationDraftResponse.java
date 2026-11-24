package com.globalrfq.rfq.quotation.dto;

import java.time.Instant;

public record QuotationDraftResponse(QuotationDraftRequest draft, long revision, Instant savedAt) { }
