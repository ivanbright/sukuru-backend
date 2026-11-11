CREATE TABLE account_plans (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE RESTRICT,
    plan_code varchar(30) NOT NULL DEFAULT 'free' CHECK (plan_code IN ('free', 'premium')),
    status varchar(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled')),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE upgrade_requests (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    plan_code varchar(30) NOT NULL CHECK (plan_code = 'premium'),
    payment_method varchar(30) NOT NULL CHECK (payment_method IN ('mobile_money', 'email')),
    payment_reference varchar(120),
    status varchar(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz
);

CREATE INDEX upgrade_requests_user_requested_idx ON upgrade_requests (user_id, requested_at DESC);
CREATE UNIQUE INDEX upgrade_requests_one_pending_per_user_uq
    ON upgrade_requests (user_id) WHERE status = 'pending';

ALTER TABLE account_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_plans FORCE ROW LEVEL SECURITY;
CREATE POLICY account_plans_user_policy ON account_plans
    USING (user_id = current_setting('app.user_id', true)::uuid)
    WITH CHECK (user_id = current_setting('app.user_id', true)::uuid);

ALTER TABLE upgrade_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE upgrade_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY upgrade_requests_user_policy ON upgrade_requests
    USING (user_id = current_setting('app.user_id', true)::uuid)
    WITH CHECK (user_id = current_setting('app.user_id', true)::uuid);

CREATE OR REPLACE FUNCTION fn_approve_upgrade(p_request_id uuid, p_user_id uuid, p_reviewed_at timestamptz)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_plan_code varchar(30);
BEGIN
    PERFORM set_config('app.user_id', p_user_id::text, true);

    SELECT plan_code INTO v_plan_code
    FROM upgrade_requests
    WHERE id = p_request_id AND user_id = p_user_id AND status = 'pending'
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    INSERT INTO account_plans (user_id, plan_code, status, updated_at)
    VALUES (p_user_id, v_plan_code, 'active', p_reviewed_at)
    ON CONFLICT (user_id) DO UPDATE
    SET plan_code = EXCLUDED.plan_code, status = 'active', updated_at = EXCLUDED.updated_at;

    UPDATE upgrade_requests
    SET status = 'approved', reviewed_at = p_reviewed_at
    WHERE id = p_request_id AND user_id = p_user_id;

    RETURN p_request_id;
END;
$$;

GRANT SELECT, INSERT, UPDATE ON account_plans TO app_runtime;
GRANT SELECT, INSERT ON upgrade_requests TO app_runtime;
GRANT EXECUTE ON FUNCTION fn_approve_upgrade(uuid, uuid, timestamptz) TO app_runtime;
