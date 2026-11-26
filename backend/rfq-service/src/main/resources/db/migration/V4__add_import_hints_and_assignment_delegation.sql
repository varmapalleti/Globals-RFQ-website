ALTER TABLE rfqs
    ADD COLUMN source_quotation_manager_name VARCHAR(190) NULL AFTER source_reference;

ALTER TABLE rfq_workflow_policy
    ADD COLUMN quotation_manager_can_assign_reviewers BOOLEAN NOT NULL DEFAULT FALSE AFTER max_formal_rejections;
