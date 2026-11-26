ALTER TABLE rfq_reviewer_reassignment_requests
    ADD COLUMN rfq_status_before_request VARCHAR(40) NULL AFTER status;

-- Legacy pending requests predate the workflow transition. Preserve the live
-- RFQ state as their return state when the request is resolved.
UPDATE rfq_reviewer_reassignment_requests request_row
JOIN rfqs ON rfqs.id = request_row.rfq_id
SET request_row.rfq_status_before_request = rfqs.status
WHERE request_row.rfq_status_before_request IS NULL;
