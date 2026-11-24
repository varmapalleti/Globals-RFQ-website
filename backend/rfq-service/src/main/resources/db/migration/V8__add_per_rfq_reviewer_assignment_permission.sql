ALTER TABLE rfq_workflow_policy
    DROP COLUMN quotation_manager_can_assign_reviewers;

ALTER TABLE rfqs
    ADD COLUMN quotation_manager_can_assign_reviewer BOOLEAN NOT NULL DEFAULT FALSE AFTER customer_decision;
