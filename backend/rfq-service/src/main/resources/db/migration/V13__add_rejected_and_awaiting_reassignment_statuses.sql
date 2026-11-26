-- =====================================================================
-- V13: Widen RFQ status column to accommodate new workflow statuses
-- New statuses: REJECTED_BY_REVIEWER, AWAITING_REASSIGNMENT
-- =====================================================================

-- The status column already uses VARCHAR(40), and both new enum values
-- (REJECTED_BY_REVIEWER = 20 chars, AWAITING_REASSIGNMENT = 22 chars)
-- fit within the existing constraint. No schema change is needed;
-- this migration exists as a marker that the application now uses
-- these additional status values.

-- Update any RFQs stuck in REVISION_IN_PROGRESS that were actually
-- reviewer-rejected (they had no manager edits since the rejection).
-- This is a no-op on fresh databases but handles existing data.
-- (Intentionally left as a comment since we cannot reliably distinguish
-- automatically in a migration script.)
