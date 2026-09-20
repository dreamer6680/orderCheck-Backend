-- Preserve established abnormal causes when migrating legacy data.
ALTER TABLE sales_order ADD COLUMN abnormal_type VARCHAR(30);

UPDATE sales_order o
SET abnormal_type = CASE
    WHEN EXISTS (
        SELECT 1 FROM outbound_record r
        WHERE r.order_id = o.id AND r.status = 'COMPLETED'
          AND r.actual_quantity < r.planned_quantity
    ) THEN 'SHORT_DELIVERY'
    WHEN EXISTS (
        SELECT 1 FROM outbound_record r
        WHERE r.order_id = o.id AND r.status = 'CANCELLED'
    ) THEN 'OUTBOUND_CANCELLED'
    WHEN NOT EXISTS (SELECT 1 FROM outbound_record r WHERE r.order_id = o.id)
         AND o.exception_reason LIKE '%可用%'
    THEN 'STOCK_SHORTAGE'
    ELSE 'OTHER'
END
WHERE o.status = 'ABNORMAL';

ALTER TABLE sales_order
    ADD CONSTRAINT ck_sales_order_abnormal_type
        CHECK (status <> 'ABNORMAL' OR abnormal_type IS NOT NULL);
