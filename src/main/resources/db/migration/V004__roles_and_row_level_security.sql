DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_migrator') THEN
        CREATE ROLE app_migrator LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
        CREATE ROLE app_runtime LOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

ALTER TABLE businesses ENABLE ROW LEVEL SECURITY;
ALTER TABLE businesses FORCE ROW LEVEL SECURITY;
CREATE POLICY businesses_tenant_policy ON businesses
    USING (id = current_setting('app.business_id')::uuid)
    WITH CHECK (id = current_setting('app.business_id')::uuid);

ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY memberships_tenant_policy ON memberships
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;
CREATE POLICY customers_tenant_policy ON customers
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE suppliers FORCE ROW LEVEL SECURITY;
CREATE POLICY suppliers_tenant_policy ON suppliers
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories FORCE ROW LEVEL SECURITY;
CREATE POLICY categories_tenant_policy ON categories
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
CREATE POLICY products_tenant_policy ON products
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales FORCE ROW LEVEL SECURITY;
CREATE POLICY sales_tenant_policy ON sales
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_items FORCE ROW LEVEL SECURITY;
CREATE POLICY sale_items_tenant_policy ON sale_items
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchases FORCE ROW LEVEL SECURITY;
CREATE POLICY purchases_tenant_policy ON purchases
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE purchase_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_items FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_items_tenant_policy ON purchase_items
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY inventory_movements_tenant_policy ON inventory_movements
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE sale_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_payments FORCE ROW LEVEL SECURITY;
CREATE POLICY sale_payments_tenant_policy ON sale_payments
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE purchase_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_payments FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_payments_tenant_policy ON purchase_payments
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_log_tenant_policy ON audit_log
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY idempotency_keys_tenant_policy ON idempotency_keys
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

ALTER TABLE document_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_counters FORCE ROW LEVEL SECURITY;
CREATE POLICY document_counters_tenant_policy ON document_counters
    USING (business_id = current_setting('app.business_id')::uuid)
    WITH CHECK (business_id = current_setting('app.business_id')::uuid);

CREATE OR REPLACE FUNCTION fn_user_memberships(p_user_id uuid)
RETURNS TABLE (business_id uuid, business_name text, role membership_role, is_active boolean)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT m.business_id, b.name::text, m.role, m.is_active
    FROM memberships m
    JOIN businesses b ON b.id = m.business_id
    WHERE m.user_id = p_user_id
    ORDER BY b.name;
$$;

CREATE OR REPLACE FUNCTION fn_cleanup(p_idempotency_before timestamptz, p_refresh_before timestamptz)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM idempotency_keys WHERE created_at < p_idempotency_before;
    DELETE FROM refresh_tokens
    WHERE expires_at < p_refresh_before
       OR (revoked_at IS NOT NULL AND revoked_at < p_refresh_before);
END;
$$;

GRANT USAGE ON SCHEMA public TO app_runtime;

GRANT SELECT, INSERT, UPDATE ON businesses TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON memberships TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON users TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON customers TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON suppliers TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON categories TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON products TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON sales TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON sale_items TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchases TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_items TO app_runtime;
GRANT SELECT, INSERT ON inventory_movements TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON sale_payments TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON purchase_payments TO app_runtime;
GRANT SELECT, INSERT ON audit_log TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_keys TO app_runtime;
GRANT SELECT, INSERT, UPDATE ON document_counters TO app_runtime;

GRANT EXECUTE ON FUNCTION fn_user_memberships(uuid) TO app_runtime;
GRANT EXECUTE ON FUNCTION fn_cleanup(timestamptz, timestamptz) TO app_runtime;
