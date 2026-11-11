DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
        CREATE ROLE app_runtime LOGIN PASSWORD 'runtime' NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;
