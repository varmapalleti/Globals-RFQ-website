ALTER TABLE rfq_customer_submissions
    ADD CONSTRAINT fk_customer_submission_quotation_version
    FOREIGN KEY (quotation_version_id) REFERENCES rfq_quotation_versions(id) ON DELETE RESTRICT;

ALTER TABLE rfq_customer_decisions
    ADD CONSTRAINT fk_customer_decision_quotation_version
    FOREIGN KEY (quotation_version_id) REFERENCES rfq_quotation_versions(id) ON DELETE RESTRICT;
