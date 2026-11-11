-- Add support for sale returns with refunds

CREATE TYPE refund_method AS ENUM ('cash', 'mobile_money', 'bank_transfer', 'store_credit');

CREATE TABLE sale_returns (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    customer_id uuid,
    created_by_user_id uuid NOT NULL,
    reference_number varchar(100),
    total_amount decimal(14,2) NOT NULL,
    refund_method refund_method,
    notes text,
    returned_at timestamptz,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX sale_returns_business_id_id_uq ON sale_returns (business_id, id);
CREATE INDEX sale_returns_sale_id_idx ON sale_returns (business_id, sale_id);

CREATE TABLE sale_return_items (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    sale_return_id uuid NOT NULL,
    sale_item_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity_returned decimal(14,3) NOT NULL,
    unit_price decimal(14,2) NOT NULL,
    subtotal decimal(14,2) NOT NULL,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT return_quantity_positive CHECK (quantity_returned > 0)
);

CREATE UNIQUE INDEX sale_return_items_business_id_id_product_id_uq ON sale_return_items (business_id, id, product_id);

-- Foreign keys
ALTER TABLE sale_returns
    ADD CONSTRAINT sale_returns_business_id_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT;
ALTER TABLE sale_returns
    ADD CONSTRAINT sale_returns_sale_fk FOREIGN KEY (business_id, sale_id) REFERENCES sales (business_id, id) ON DELETE RESTRICT;
ALTER TABLE sale_returns
    ADD CONSTRAINT sale_returns_customer_fk FOREIGN KEY (business_id, customer_id) REFERENCES customers (business_id, id) ON DELETE RESTRICT;
ALTER TABLE sale_returns
    ADD CONSTRAINT sale_returns_created_by_fk FOREIGN KEY (business_id, created_by_user_id) REFERENCES memberships (business_id, user_id) ON DELETE RESTRICT;

ALTER TABLE sale_return_items
    ADD CONSTRAINT sale_return_items_return_fk FOREIGN KEY (business_id, sale_return_id) REFERENCES sale_returns (business_id, id) ON DELETE CASCADE;
ALTER TABLE sale_return_items
    ADD CONSTRAINT sale_return_items_sale_item_fk FOREIGN KEY (business_id, sale_item_id, product_id) REFERENCES sale_items (business_id, id, product_id) ON DELETE RESTRICT;
ALTER TABLE sale_return_items
    ADD CONSTRAINT sale_return_items_product_fk FOREIGN KEY (business_id, product_id) REFERENCES products (business_id, id) ON DELETE RESTRICT;

-- Update inventory_movements to support return items
ALTER TABLE inventory_movements
    ADD COLUMN sale_return_item_id uuid;

-- Update constraint to include return items
ALTER TABLE inventory_movements DROP CONSTRAINT movement_type_consistency;
ALTER TABLE inventory_movements ADD CONSTRAINT movement_type_consistency CHECK (
    (movement_type = 'sale'       AND quantity_delta < 0 AND sale_item_id IS NOT NULL AND purchase_item_id IS NULL AND sale_return_item_id IS NULL)
 OR (movement_type = 'purchase'   AND quantity_delta > 0 AND purchase_item_id IS NOT NULL AND sale_item_id IS NULL AND sale_return_item_id IS NULL)
 OR (movement_type = 'return'     AND quantity_delta > 0 AND sale_return_item_id IS NOT NULL AND sale_item_id IS NULL AND purchase_item_id IS NULL)
 OR (movement_type = 'adjustment' AND quantity_delta <> 0 AND sale_item_id IS NULL AND purchase_item_id IS NULL AND sale_return_item_id IS NULL)
);

ALTER TABLE inventory_movements
    ADD CONSTRAINT inventory_movements_return_item_fk FOREIGN KEY (business_id, sale_return_item_id, product_id) 
    REFERENCES sale_return_items (business_id, id, product_id) ON DELETE RESTRICT;

-- Update document_counters to support return kind
ALTER TABLE document_counters DROP CONSTRAINT IF EXISTS document_counters_kind_check;
ALTER TABLE document_counters ADD CONSTRAINT document_counters_kind_check CHECK (kind IN ('sale', 'return'));

-- Add RLS policies for returns
ALTER TABLE sale_returns ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_returns FORCE ROW LEVEL SECURITY;
CREATE POLICY sale_returns_tenant_policy ON sale_returns
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE sale_return_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_return_items FORCE ROW LEVEL SECURITY;
CREATE POLICY sale_return_items_tenant_policy ON sale_return_items
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

COMMENT ON TABLE sale_returns IS 'Customer returns of previously sold items with optional refunds';
COMMENT ON COLUMN sale_returns.refund_method IS 'How the refund is processed - null if no refund (exchange only)';
COMMENT ON TABLE sale_return_items IS 'Individual items returned in a sale return';
