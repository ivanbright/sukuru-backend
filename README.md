# Sukuru Backend API

Spring Boot REST API for multi-tenant retail inventory, purchasing, sales, payments, and reporting. The service uses PostgreSQL, Flyway migrations, JWT authentication, and PostgreSQL row-level security.

## Technology

- Java 21 and Spring Boot 3.5
- PostgreSQL 16
- Spring Web, Security, Data JPA, and Bean Validation
- Flyway database migrations
- JWT access tokens and rotating refresh tokens
- OpenAPI / Swagger UI
- Docker image build and GitHub Actions CI

## Repository contents

The backend source is under `src/`. Database migrations are under `src/main/resources/db/migration/`. The `frontend/` directory is kept locally for integration work and is ignored by Git so it is not included in the backend repository. `API_ENDPOINTS.md` contains the endpoint reference. The API also serves its OpenAPI document at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Run locally

Requirements: Java 21, Docker Desktop, and a running Docker daemon.

1. Copy `.env.example` to `.env` and replace the development secrets if needed.
2. Start PostgreSQL:

   ```bash
   docker compose up -d db
   ```

3. Start the API from this directory:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`; `.\run-app.bat` is also available.

The API listens on `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

The local Compose database listens on port `5436`. It creates `app_migrator` for schema migrations and a restricted `app_runtime` role for API queries. The local `.env` file is loaded by Spring Boot and is excluded from Git.

To run both services in containers instead, use `docker compose up --build`. Avoid running the Compose API and a locally launched API at the same time because both use port `8080`.

## Configuration

Configuration is supplied with environment variables. `.env.example` documents the local setup. Do not commit `.env` or production credentials.

| Variable | Purpose |
| --- | --- |
| `PORT` | HTTP listen port; Railway supplies this automatically. Defaults to `8080`. |
| `DB_URL` | JDBC URL for the application database. If omitted, the API builds it from `PGHOST`, `PGPORT`, and `PGDATABASE`, with local defaults. |
| `DB_USER`, `DB_PASSWORD` | Restricted application database login. |
| `DB_POOL_SIZE` | Maximum application connection pool size. Defaults to `10`. |
| `FLYWAY_URL` | Optional migration JDBC URL; defaults to `DB_URL`. |
| `FLYWAY_USER`, `FLYWAY_PASSWORD` | Database login Flyway uses to apply migrations. Defaults support local Docker Compose and Railway's `PGUSER` / `PGPASSWORD`. |
| `JWT_SECRET` | Required signing secret of at least 32 bytes. Use a unique, randomly generated value in each deployed environment. |
| `JWT_ISSUER` | JWT issuer identifier. Defaults to `rw-shop`. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins. Local default is `http://localhost:3000`. |
| `MAIL_NOTIFICATIONS_ENABLED` | Set `true` to send Premium upgrade requests to `BILLING_SUPPORT_EMAIL`; defaults to `false`. |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server configuration. Gmail uses `smtp.gmail.com` and port `587`. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP account and app password. Keep the app password in local `.env` or Railway Variables, never in Git. |
| `MAIL_FROM` | Optional sender address; defaults to `MAIL_USERNAME`. For Gmail, use the authenticated mailbox. |

When testing the Railway API from the local frontend, include `http://localhost:3000` in the Railway API's `CORS_ALLOWED_ORIGINS` value. You can include the future deployed frontend origin as a second comma-separated origin when it is available.

## Deploy to Railway

Deploy this directory as the API service. Railway detects the root `Dockerfile`; it builds the application with Java 21 and runs it on the port provided through `PORT`. Add a Railway PostgreSQL service to the same project and connect it to the API over Railway's private network.

Configure these variables on the API service, using Railway's variable reference picker to reference the PostgreSQL service (shown below as `Postgres`):

| API service variable | Value |
| --- | --- |
| `PGHOST` | `${{Postgres.PGHOST}}` |
| `PGPORT` | `${{Postgres.PGPORT}}` |
| `PGDATABASE` | `${{Postgres.PGDATABASE}}` |
| `PGUSER` | `${{Postgres.PGUSER}}` |
| `PGPASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `FLYWAY_USER` | `${{Postgres.PGUSER}}` |
| `FLYWAY_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `DB_USER` | `app_runtime` |
| `DB_PASSWORD` | A strong secret you generate and also assign to the database role below. |
| `JWT_SECRET` | A separate random secret of at least 32 bytes. |
| `CORS_ALLOWED_ORIGINS` | The deployed frontend's exact origin, for example `https://your-frontend.example`. |

The API uses Railway's PostgreSQL credentials for Flyway migrations. The application itself connects as the restricted `app_runtime` role. Before the first API deployment, create that role on the Railway PostgreSQL service using its database query tool or a privileged SQL connection. Set the password to exactly the value configured as the API's `DB_PASSWORD`:

```sql
CREATE ROLE app_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'replace-with-the-DB_PASSWORD-value';
```

If the role already exists, use `ALTER ROLE app_runtime PASSWORD 'replace-with-the-DB_PASSWORD-value';` instead. Do not use the Railway database administrator as `DB_USER`. Flyway runs the migrations on API startup; the migrations grant `app_runtime` the application permissions and enable row-level security. Keep backups enabled for production data.

After deployment, check `https://<your-api-domain>/actuator/health` and open `https://<your-api-domain>/swagger-ui.html`. Set the frontend's `NEXT_PUBLIC_API_BASE_URL` to the API's public HTTPS origin. Configure CORS to allow that frontend origin before testing browser requests.

## API behavior

- API routes are versioned under `/api/v1`.
- Register a business with `POST /api/v1/auth/register-business`; sign in with `POST /api/v1/auth/login`.
- Send the access token as a Bearer token on authenticated requests. Refresh tokens are rotated through `/api/v1/auth/refresh`.
- Mutating requests outside `/api/v1/auth/` require an `Idempotency-Key` header containing a UUID. Reusing a key with a different request returns `422 IDEMPOTENCY_KEY_REUSED`.
- Money and quantities are serialized as decimal strings (for example, `"1250.00"` and `"2.500"`). List routes are paginated with `page` and `size` (maximum size 100).
- Business membership and PostgreSQL row-level security scope data to the business in the authenticated request. Staff responses omit protected cost data.

Core areas include authentication, businesses and memberships, categories and products, customers and suppliers, inventory, purchases, sales, returns, payments, audit logs, and reports. See [API_ENDPOINTS.md](API_ENDPOINTS.md) or the generated Swagger UI for routes and request schemas.

## Database migrations

Flyway applies the ordered SQL files in `src/main/resources/db/migration` at application startup. The migrations define the schema, constraints and indexes, refresh tokens, idempotency and audit support, tenant row-level security, soft deletes, and sale returns. Do not edit an already-applied migration in a deployed environment; add a new migration instead.

## Tests and build

Run the test suite:

```bash
./mvnw test
./mvnw verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Integration tests use Testcontainers and require Docker. The GitHub Actions workflow runs the full Maven verification lifecycle, including tests and the JaCoCo coverage threshold, for pull requests and pushes to `main` and `develop`. It builds the Docker image after validation succeeds. Dependabot checks Maven and GitHub Actions dependencies weekly.

## Merge protection on GitHub

The workflow runs for pull requests targeting `main` or `develop`. To make failed CI jobs block merges into `main`, configure a repository ruleset after the first workflow run:

1. Open **Settings → Rules → Rulesets** and create an active branch ruleset targeting `main`.
2. Require a pull request before merging.
3. Require status checks and select both **Validate and Test** and **Build Container** from the **Backend CI** workflow.
4. Require branches to be up to date before merging, and block force pushes.

The workflow file cannot turn these repository settings on by itself. Once required, a failed test, coverage check, or Docker build blocks the pull request from merging. GitHub documents this behavior in [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

Build the deployable JAR locally with `./mvnw clean package`. Build the container with `docker build -t sukuru-api .`.

## Local frontend integration check

Keep `frontend/` in the working directory for local integration, even though Git ignores it in this backend repository. Start PostgreSQL and the API as described above, then in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend defaults to `http://localhost:8080` for its API and `http://localhost:3000` for its own origin, matching the backend's local CORS default. Sign in or register a test business, then verify dashboard loading and one read/write workflow. Do not use production data for this check.

## Plans and manual payments

The `/payment` page provides a Free plan (one owner workspace) and Premium (up to five owner workspaces). A Premium request is stored as `pending`; submitting the request does not activate paid access. The customer can arrange Mobile Money to the configured number or contact the configured email. Record a payment reference when available, then confirm the payment before approval.

Configure these backend environment variables in production:

- `BILLING_MOBILE_MONEY_NUMBER` (defaults to `+250783453911`)
- `BILLING_SUPPORT_EMAIL` (defaults to `mugishaivanbright250@gmail.com`)
- `BILLING_ADMIN_KEY` (set a long, random secret; approval is disabled if this is empty)
- `MAIL_NOTIFICATIONS_ENABLED=true`
- `MAIL_HOST=smtp.gmail.com` and `MAIL_PORT=587`
- `MAIL_USERNAME` and `MAIL_FROM` set to the sending Gmail mailbox
- `MAIL_PASSWORD` set to a newly generated Google App Password in Railway Variables

After verifying payment, an authenticated business owner with the admin key can approve a request through `POST /api/v1/billing/admin/requests/{requestId}/approve`, sending `X-Billing-Admin-Key` and a JSON body containing the request owner's `userId`. The payment page displays both IDs for a pending request. Approval activates Premium and updates the request in one database transaction. Keep the admin key private and rotate it if it is exposed. Upgrade requests and account plans are stored in the PostgreSQL migration `V008__account_billing.sql`.

The page also lists possible future paid add-ons (AI insights, downloadable reports, and larger team plans) as proposals. They are not represented as currently available paid features.
