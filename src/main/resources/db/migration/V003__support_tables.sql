CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    family_id uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX refresh_tokens_token_hash_uq ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at);

CREATE TABLE idempotency_keys (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    key varchar(100) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status_code integer,
    response_body jsonb,
    created_at timestamptz DEFAULT now(),
    CONSTRAINT idempotency_keys_business_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT idempotency_keys_business_key_uq UNIQUE (business_id, key)
);

CREATE INDEX idempotency_keys_created_at_idx ON idempotency_keys (created_at);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    business_id uuid NOT NULL,
    actor_user_id uuid,
    action varchar(100) NOT NULL,
    entity_type varchar(100) NOT NULL,
    entity_id uuid,
    before jsonb,
    after jsonb,
    correlation_id varchar(100),
    created_at timestamptz DEFAULT now(),
    CONSTRAINT audit_log_business_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT audit_log_actor_fk FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX audit_log_business_created_at_idx ON audit_log (business_id, created_at DESC);
CREATE INDEX audit_log_business_entity_idx ON audit_log (business_id, entity_type, entity_id);

CREATE TABLE document_counters (
    business_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    last_value bigint NOT NULL DEFAULT 0,
    CONSTRAINT document_counters_pk PRIMARY KEY (business_id, kind),
    CONSTRAINT document_counters_business_fk FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT document_counters_kind_check CHECK (kind IN ('sale'))
);
