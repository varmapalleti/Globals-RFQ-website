ALTER TABLE rfq_line_items
    MODIFY COLUMN customer_part_number VARCHAR(255) NULL,
    MODIFY COLUMN requested_manufacturer VARCHAR(255) NULL,
    MODIFY COLUMN requested_part_number VARCHAR(255) NULL,
    ADD COLUMN procurement_category VARCHAR(120) NULL AFTER requested_part_number,
    ADD COLUMN taa_required BOOLEAN NULL AFTER procurement_category;
