ALTER TABLE sale_items
    ADD CONSTRAINT sale_items_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT sale_items_unit_price_non_negative CHECK (unit_price >= 0),
    ADD CONSTRAINT sale_items_unit_cost_non_negative CHECK (unit_cost >= 0),
    ADD CONSTRAINT sale_items_subtotal_non_negative CHECK (subtotal >= 0);

ALTER TABLE purchase_items
    ADD CONSTRAINT purchase_items_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT purchase_items_unit_cost_non_negative CHECK (unit_cost >= 0),
    ADD CONSTRAINT purchase_items_subtotal_non_negative CHECK (subtotal >= 0);

ALTER TABLE sales
    ADD CONSTRAINT sales_total_amount_non_negative CHECK (total_amount >= 0);

ALTER TABLE purchases
    ADD CONSTRAINT purchases_total_amount_non_negative CHECK (total_amount >= 0);

ALTER TABLE sale_payments
    ADD CONSTRAINT sale_payments_amount_positive CHECK (amount > 0);

ALTER TABLE purchase_payments
    ADD CONSTRAINT purchase_payments_amount_positive CHECK (amount > 0);

ALTER TABLE products
    ADD CONSTRAINT products_selling_price_non_negative CHECK (selling_price IS NULL OR selling_price >= 0),
    ADD CONSTRAINT products_purchase_price_non_negative CHECK (purchase_price IS NULL OR purchase_price >= 0);

ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_reason_required CHECK (
        movement_type NOT IN ('adjustment', 'return')
        OR (reason IS NOT NULL AND btrim(reason) <> '')
    );

CREATE UNIQUE INDEX sales_business_id_reference_number_uq ON sales (business_id, reference_number);
CREATE UNIQUE INDEX purchases_business_id_reference_number_uq ON purchases (business_id, reference_number);

CREATE INDEX sale_items_business_id_sale_id_idx ON sale_items (business_id, sale_id);
CREATE INDEX purchase_items_business_id_purchase_id_idx ON purchase_items (business_id, purchase_id);
CREATE INDEX sale_payments_business_id_sale_id_idx ON sale_payments (business_id, sale_id);
CREATE INDEX purchase_payments_business_id_purchase_id_idx ON purchase_payments (business_id, purchase_id);
CREATE INDEX sales_business_id_status_sold_at_idx ON sales (business_id, status, sold_at);
CREATE INDEX purchases_business_id_status_purchased_at_idx ON purchases (business_id, status, purchased_at);
CREATE INDEX products_business_id_name_idx ON products (business_id, name);
CREATE INDEX customers_business_id_name_idx ON customers (business_id, name);
CREATE INDEX suppliers_business_id_name_idx ON suppliers (business_id, name);
