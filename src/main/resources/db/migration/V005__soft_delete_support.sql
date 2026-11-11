-- Add is_active column to customers, suppliers, and categories for soft delete support

ALTER TABLE customers ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
CREATE INDEX idx_customers_active ON customers(business_id, is_active);

ALTER TABLE suppliers ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
CREATE INDEX idx_suppliers_active ON suppliers(business_id, is_active);

ALTER TABLE categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
CREATE INDEX idx_categories_active ON categories(business_id, is_active);

-- Add comments
COMMENT ON COLUMN customers.is_active IS 'Soft delete flag - inactive customers are hidden but preserved for history';
COMMENT ON COLUMN suppliers.is_active IS 'Soft delete flag - inactive suppliers are hidden but preserved for history';
COMMENT ON COLUMN categories.is_active IS 'Soft delete flag - inactive categories are hidden but preserved for history';
