ALTER TABLE rfq_attachments
    ADD COLUMN rfq_line_item_id BIGINT NULL AFTER rfq_id,
    ADD CONSTRAINT fk_rfq_attachment_line_item
        FOREIGN KEY (rfq_line_item_id) REFERENCES rfq_line_items(id) ON DELETE RESTRICT,
    ADD INDEX idx_rfq_attachment_line_item (rfq_line_item_id);
