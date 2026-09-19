-- Existing outbound records had no scheduled date. Backfill from their creation date
-- in the default warehouse business zone; future records receive an explicit plan date.
ALTER TABLE outbound_record ADD COLUMN planned_outbound_date DATE;
UPDATE outbound_record
SET planned_outbound_date = (created_at AT TIME ZONE 'Asia/Shanghai')::date;
ALTER TABLE outbound_record ALTER COLUMN planned_outbound_date SET NOT NULL;
CREATE INDEX idx_outbound_record_status_planned_date
    ON outbound_record (status, planned_outbound_date);
