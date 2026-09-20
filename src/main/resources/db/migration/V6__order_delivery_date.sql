-- Legacy orders have no recorded delivery commitment; preserve NULL until explicitly assigned.
ALTER TABLE sales_order ADD COLUMN delivery_date DATE;
CREATE INDEX idx_sales_order_status_delivery_date ON sales_order (status, delivery_date);
