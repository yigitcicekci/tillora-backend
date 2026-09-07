# Tillora Backend

Tillora Backend is a Java-based modular monolith API for small and medium-sized businesses that need simple business management, pre-accounting, and financial operations. The backend MVP covers company, user, authentication, current account, chart of accounts, cash account, bank account, voucher, product catalog, sales and purchase invoice workflows, private object storage, dashboard, reporting, audit, transactional email, and production observability.

PostgreSQL is the source of truth. Redis is used only for cache, sessions, locks, rate limits, and temporary state. Database schema changes are managed with Flyway migrations, and the application validates the schema with Hibernate `validate`.

## Technology

- Java 25
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Spring Modulith
- PostgreSQL
- Redis
- Flyway
- Maven
- Docker Compose
- MinIO
- Prometheus
- Grafana
- OpenAPI / Swagger UI
- Testcontainers

## Architecture

The codebase is organized as a modular monolith. Modules are separated by domain boundaries, and shared infrastructure is kept under `shared`.

Current main modules:

- `auth`: Login, mandatory password change, refresh token rotation, logout, and current user information.
- `company`: Company creation and company scoping.
- `user`: User, role, and permission management.
- `chartofaccount`: Chart of accounts and default accounting setup.
- `currentaccount`: Current account management.
- `finance`: Cash and bank account management and their posting-account provisioning.
- `voucher`: Guided collection and payment vouchers, offset vouchers, numbering, approval, and cancellation.
- `product`: Product catalog data, pricing, units, and VAT.
- `invoice`: Sales and purchase invoice drafts, backend-calculated totals, approval, current account movements, and accounting voucher integration.
- `objectstorage`: Company-scoped private file storage for report exports, with PostgreSQL metadata and MinIO content.
- `dashboard`: Short-lived Redis-cached financial and operational summary backed by PostgreSQL.
- `reporting`: Tenant-scoped, filtered, sorted, paginated report projections.
- `audit`: Append-only audit trail with safe before/after state and authorized query API.
- `shared`: Common error model, security, and web helpers.

## Local Development

Requirements:

- Docker
- Docker Compose
- OpenSSL (for local secret generation)

Java 25 and Maven are needed only when building or testing outside Docker.

The local environment uses `.env.development`. Production values are kept in `.env.production`.

Start the project:

```bash
bash scripts/init-local.sh
./start-local.sh
```

The initializer creates a mode-600 `.env.development` from `.env.development.example`, generates independent secrets, and refuses to overwrite an existing file. It does not change production configuration. The demo company uses synthetic data; its `admin` password is the generated `TILLORA_BOOTSTRAP_ADMIN_PASSWORD` in that file. After the first successful startup, disable `TILLORA_BOOTSTRAP_ENABLED` and remove the bootstrap password. Do not use this development configuration in production.

Only the API, PostgreSQL, and Redis start by default, with host ports bound to loopback. No cloud account, frontend image, email provider, or object store is required. Set `API_HOST_PORT` and `MANAGEMENT_PORT` to change the local API and health ports.

For local MinIO, set `TILLORA_OBJECT_STORAGE_ENABLED=true`; `start-local.sh` enables the `storage` profile automatically when the endpoint is `http://minio:9000`. When invoking Compose directly, also pass `--profile storage`. For Prometheus and Grafana, set `COMPOSE_PROFILES=monitoring` in `.env.development`; the initializer supplies a random Grafana password. Both profiles can be enabled together. Stopping the stack also stops optional-profile services and preserves database/storage volumes.

The script builds the Docker Compose services, waits for the API readiness endpoint, and then follows the API logs. Pressing `Ctrl+C` exits the log stream only; it does not stop the services.

Stop the services:

```bash
./stop-local.sh
```

## Local Services

- API: `http://127.0.0.1:8080`
- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`
- Readiness: `http://127.0.0.1:8081/actuator/health/readiness`
- Liveness: `http://127.0.0.1:8081/actuator/health/liveness`
- PostgreSQL: `127.0.0.1:5433`
- Redis: `127.0.0.1:6380`
- MinIO API (`storage`): `http://127.0.0.1:9000`
- MinIO Console (`storage`): `http://127.0.0.1:9001`
- Prometheus (`monitoring`): `http://127.0.0.1:9090`
- Grafana (`monitoring`): `http://127.0.0.1:3000`

Prometheus and detailed Actuator metrics require HTTP Basic authentication with username `tillora-monitoring` and the `TILLORA_MANAGEMENT_PASSWORD` secret. Health probes remain public on the loopback-bound management port.

Grafana is provisioned with the Prometheus datasource and the Tillora Backend dashboard. Set `GRAFANA_ADMIN_PASSWORD` before starting Compose. Prometheus includes alerts for API availability, server errors, and slow database operations.

## Authentication

Authentication is JWT-based. Google and Apple login flows are not included yet.

Current auth endpoints:

- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/change-password`
- `GET /api/v1/auth/me`

The bootstrap flow creates the company, default chart of accounts, roles, permissions, and the first admin user. Administrators create subsequent users with temporary passwords. New users, including the bootstrap admin, must change their password before accessing business endpoints.

Companies can also be provisioned without an existing user through `POST /api/v1/companies`. This endpoint does not require login or CSRF, but the request body must include a `provisioningToken` matching the `TILLORA_COMPANY_PROVISIONING_TOKEN` secret. Configure a cryptographically random value of at least 32 characters. The token is valid only for company creation and is never returned or written to the audit log.

The login flow uses `email`, globally unique `username`, and `password`. The company context is resolved from the authenticated user. Clients should redirect users with `mustChangePassword=true` to the password change screen. Until the change succeeds, business endpoints return `403 PASSWORD_CHANGE_REQUIRED`. This enforcement also applies immediately to existing users whose database flag is already true, so the matching frontend flow must be deployed together with this backend behavior.

A successful password change invalidates previous access and refresh tokens, returns a new access token, and replaces the HttpOnly refresh-token cookie. Except for login, state-changing requests require the `token` value returned in the `GET /api/v1/auth/csrf` response body to be sent in the `X-XSRF-TOKEN` header, including bearer-authenticated password changes. The browser sends the matching CSRF cookie with `credentials: include`; frontend code does not read that cookie. Run the Postman `CSRF Token` request before `Change Password`. Refresh tokens are stored as hashes in PostgreSQL and rotated on refresh.

## Cash Accounts

Cash account endpoints:

- `POST /api/v1/cash-accounts`
- `GET /api/v1/cash-accounts?active=true&page=0&size=20`
- `GET /api/v1/cash-accounts/{cashAccountId}`
- `PATCH /api/v1/cash-accounts/{cashAccountId}/disable`

The backend provisions immutable posting codes under the `CASH` system account as `100.01`, `100.02`, and subsequent values. Clients cannot supply or edit these codes. Currency is optional during creation and defaults to the company's currency when omitted.

The list endpoint accepts an optional `active` filter. When it is omitted, both active and disabled cash accounts are returned.

Cash balances and movements are not calculated by this module yet. Cash accounts are never deleted; the supported lifecycle operation is disabling the cash account and its posting account.

Cash account permissions:

- `ADMIN` and `ACCOUNTING`: `CASH_ACCOUNT_READ`, `CASH_ACCOUNT_CREATE`, and `CASH_ACCOUNT_DISABLE`.
- `VIEWER`: `CASH_ACCOUNT_READ`.
- `SALES`: no cash account permissions.

## Bank Accounts

Bank account endpoints:

- `POST /api/v1/bank-accounts`
- `GET /api/v1/bank-accounts?active=true&page=0&size=20`
- `GET /api/v1/bank-accounts/{bankAccountId}`
- `PATCH /api/v1/bank-accounts/{bankAccountId}/disable`

Bank account creation accepts the bank name, branch, IBAN, and account number. Currency is optional and defaults to the company's currency when omitted.

The backend provisions immutable posting codes under the `BANKS` system account as `102.01`, `102.02`, and subsequent values. Clients cannot supply or edit these codes.

The list endpoint accepts an optional `active` filter. When it is omitted, both active and disabled bank accounts are returned.

Bank balances and movements are not calculated by this module yet. Bank accounts are never deleted; the supported lifecycle operation is disabling the bank account and its posting account.

Bank account permissions:

- `ADMIN` and `ACCOUNTING`: `BANK_ACCOUNT_READ`, `BANK_ACCOUNT_CREATE`, and `BANK_ACCOUNT_DISABLE`.
- `VIEWER`: `BANK_ACCOUNT_READ`.
- `SALES`: no bank account permissions.

## Products

Product endpoints:

- `POST /api/v1/products`
- `GET /api/v1/products?active=true&page=0&size=20`
- `GET /api/v1/products/{productId}`
- `PUT /api/v1/products/{productId}`
- `PATCH /api/v1/products/{productId}/disable`

Product codes and optional barcodes are normalized to uppercase and must be unique within the company. Purchase price, sale price, and VAT rate are validated by both the application and PostgreSQL constraints.

Supported units are `PIECE`, `KILOGRAM`, `GRAM`, `LITER`, `MILLILITER`, `METER`, `SQUARE_METER`, `CUBIC_METER`, `PACKAGE`, `BOX`, `PAIR`, `SET`, `HOUR`, and `DAY`.

Products are catalog records used by invoice lines. Unused products may be deleted; products referenced by invoices are disabled to preserve historical records. The backend does not manage inventory quantities or warehouses.

Product permissions:

- `ADMIN`: `PRODUCT_READ` and `PRODUCT_MANAGE`.
- `ACCOUNTING`, `SALES`, and `VIEWER`: `PRODUCT_READ`.

## Invoices

Invoice endpoints:

- `POST /api/v1/invoices/sales`
- `POST /api/v1/invoices/purchases`
- `PUT /api/v1/invoices/{invoiceId}`
- `GET /api/v1/invoices?page=0&size=20`
- `GET /api/v1/invoices/{invoiceId}`
- `POST /api/v1/invoices/{invoiceId}/approve`
- `POST /api/v1/invoices/{invoiceId}/approve-with-settlement`
- `POST /api/v1/invoices/{invoiceId}/settlements`

Sales and purchase invoices are created as drafts. The backend calculates gross amounts, discounts, VAT, grand totals, and product costs. Sales costs use the product purchase-price snapshot; purchase costs use the invoice line's net amount after discount. Invoice numbers use the `SF-YYYY-NNNNNN` and `AF-YYYY-NNNNNN` formats and are generated independently for each company, type, and year.

Approval is atomic. A sales invoice creates a customer debit movement and an approved offset voucher containing customer receivable, domestic sales, calculated VAT, cost of goods sold, and trade goods entries. A purchase invoice creates a supplier credit movement and an approved offset voucher containing trade goods, deductible VAT, and supplier payable entries. If any part fails, the invoice remains a draft and no financial side effect is committed. Concurrent approvals cannot duplicate financial effects.

Standard approval leaves the invoice on account and does not change cash or bank balances. `approve-with-settlement` atomically approves the invoice and creates an approved cash or bank collection for sales or payment for purchases. `settlements` applies additional partial or final settlements to an approved invoice. Both settlement endpoints require an idempotency key, reject amounts above the remaining invoice balance, and serialize concurrent writes on the invoice. Invoice detail responses expose `paidAmount`, `remainingAmount`, and `paymentStatus` as `UNPAID`, `PARTIALLY_PAID`, or `PAID`. Cancelling an allocated collection or payment voucher removes it from the calculated paid amount without deleting its allocation history.

The create request requires an idempotency key. Retrying the same request returns the existing invoice, while reusing the key with different data is rejected. Approved invoices and their generated vouchers cannot be edited or cancelled directly.

Invoice permissions:

- `ADMIN` and `ACCOUNTING`: `INVOICE_READ`, `INVOICE_CREATE`, and `INVOICE_APPROVE`.
- `SALES`: `INVOICE_READ` and `INVOICE_CREATE`.
- `VIEWER`: `INVOICE_READ`.

## Vouchers

Voucher endpoints:

- `POST /api/v1/vouchers`
- `POST /api/v1/vouchers/collections`
- `POST /api/v1/vouchers/payments`
- `POST /api/v1/vouchers/transfers`
- `PUT /api/v1/vouchers/{voucherId}`
- `GET /api/v1/vouchers?page=0&size=20`
- `GET /api/v1/vouchers/{voucherId}`
- `POST /api/v1/vouchers/{voucherId}/approve`
- `POST /api/v1/vouchers/{voucherId}/cancel`

The manual voucher API accepts only `OFFSET` vouchers. Guided `COLLECTION` creation accepts a customer current account, a `CASH` or `BANK` settlement account, an amount, and a required idempotency key. The backend generates:

- Cash or bank posting account as debit.
- Customer receivable posting account as credit.

Guided `PAYMENT` creation accepts a supplier current account with the same settlement account options. The backend generates:

- Supplier payable posting account as debit.
- Cash or bank posting account as credit.

Guided `TRANSFER` creation accepts distinct source and target `CASH` or `BANK` accounts. The backend debits the target posting account and credits the source posting account. Cash-to-cash, cash-to-bank, bank-to-cash, and bank-to-bank transfers are supported in the company currency.

The same idempotency key and request return the existing voucher. Reusing the key with different request data or another guided voucher type is rejected. Collection and payment drafts cannot be replaced with manually selected accounting lines.

Voucher dates default to the current date in the company's timezone. Currency defaults to the company's currency, and the exchange rate defaults to `1`. Foreign-currency manual vouchers are deferred.

Draft vouchers may be unbalanced and may be replaced with `PUT`. Approval requires at least one debit and one credit entry, equal debit and credit totals, and active posting-enabled chart accounts. Approved vouchers are immutable.

Only approved vouchers may be cancelled. Cancellation preserves the voucher and all of its lines. A cancellation reason is required, and approval and cancellation operations are included in the audit trail.

Voucher permissions:

- `ADMIN` and `ACCOUNTING`: `VOUCHER_READ`, `VOUCHER_CREATE`, `VOUCHER_APPROVE`, and `VOUCHER_CANCEL`.
- `VIEWER`: `VOUCHER_READ`.
- `SALES`: no voucher permissions.

## Postman

Postman collection:

```text
postman/Tillora API.postman_collection.json
```

Local Postman environment:

```text
postman/Tillora Local.postman_environment.json
```

Production Postman environment:

```text
postman/Tillora Production.postman_environment.json
```

Platform Admin collection and local environment:

```text
postman/Tillora Platform Admin API.postman_collection.json
postman/Tillora Platform Admin Local.postman_environment.json
```

The Platform Admin collection targets the private `/internal/admin/**` API. Enable the explicit server-side bootstrap configuration for one startup only, then disable it and remove the bootstrap email and password from the runtime environment after the first admin is created. The database lock and existing-admin check make the operation idempotent, but they do not replace removing the bootstrap secret from deployment configuration.

Platform Admin JWT signing uses `TILLORA_PLATFORM_ADMIN_JWT_SECRET`; keep it separate from `TILLORA_JWT_SECRET`.

Set `companyProvisioningToken` in the selected Postman environment to the same secret configured as `TILLORA_COMPANY_PROVISIONING_TOKEN` before running `Create Company (Provisioning Token)`.

When auth requests succeed, the collection scripts update the `accessToken`, `companyId`, and `userId` environment variables. Creating a cash account, bank account, product, invoice, or voucher updates the matching ID variable, which is then used by follow-up requests. The refresh token remains in Postman's cookie jar.

The collection sends `Accept-Language` on every request using the `language` environment variable. Set it to `tr` or `en`; the local environment defaults to `tr`.

Run `Create Current Account` and create a cash or bank account before guided collection or payment requests. Collections require a `CUSTOMER` or `BOTH` current account; payments require a `SUPPLIER` or `BOTH` current account. Clear or replace `collectionIdempotencyKey` or `paymentIdempotencyKey` before starting a new operation; keep it unchanged when retrying the same request.

Run `List Chart of Accounts` before manual offset voucher requests and ensure `chartOfAccountId` identifies an active account with `postingAllowed=true`.

Sales invoices require a `CUSTOMER` or `BOTH` current account, while purchase invoices require a `SUPPLIER` or `BOTH` current account. Clear the matching invoice idempotency key before creating another invoice.

## Platform Admin Production Access

The production preset retains the optional downstream Platform Admin frontend and Cloudflare DNS-01 setup. It is not needed for local backend development. Set `TILLORA_API_DOMAIN` and `TILLORA_ADMIN_DOMAIN` explicitly before upgrading an existing deployment; there is no fallback to a Tillora-owned hostname. The hostnames below illustrate the original deployment; substitute your configured domains and CORS origins throughout.

Production exposes both API surfaces through the same backend instance:

```text
https://<api-domain>/api/**                         public through Caddy
https://<api-domain>/internal/admin/**              always 403 through Caddy
https://<admin-domain>/internal/admin/**            private API through Caddy → api:8080
https://<admin-domain>/<other-path>                 static frontend through Caddy → admin-web:8080
```

Caddy always returns 403 for `/internal/admin` and `/internal/admin/**` on `TILLORA_API_DOMAIN`, regardless of source address. The same paths on `TILLORA_ADMIN_DOMAIN` are proxied to the API; every other admin-host path is proxied to the static frontend. The normal public `/api/**` fallback remains unchanged.

The admin hostname intentionally has no Caddy `remote_ip` matcher because Docker NAT can hide the original Tailscale source from Caddy. Its private boundary is the production network setup: `TILLORA_ADMIN_DOMAIN` must resolve only to the server's Tailscale IPv4 address and must be reachable through the tailnet. DNS-only resolution is not a replacement for firewall policy; direct public-interface access and Cloudflare proxying for this hostname must be prevented manually.

No client-controlled forwarded header is used for routing or authorization. The repository has no incoming Cloudflare proxy configuration for these routes; the Cloudflare references in the examples are for DNS-01 and R2 object storage. Spring's forwarded-header processing remains for normal proxy URL/scheme handling and is not used for the Caddy route split.

`TILLORA_ADMIN_DOMAIN` is configured as a separate Caddy site for the static Platform Admin frontend. Its `/internal/admin` and `/internal/admin/**` paths reach `api:8080`; all other paths reach `admin-web:8080`. The frontend image serves `/healthz`, `/admin/login`, and SPA fallback routes internally. The admin frontend certificate uses the Cloudflare DNS-01 provider from the custom Caddy image. `TILLORA_API_DOMAIN` does not have a DNS provider configured and keeps its existing automatic HTTPS behavior.

The production CORS value is a comma-separated explicit allowlist. It must include `https://<admin-domain>` alongside the existing normal frontend origin, for example `https://<app-domain>,https://<admin-domain>`. Wildcards and arbitrary-origin reflection are not supported.

Platform Admin authentication keeps the existing cookie/CSRF model. `TILLORA_PLATFORM_ADMIN_REFRESH_TOKEN` is HttpOnly, Secure in production, `SameSite=Lax`, host-only because no Domain attribute is set, and scoped to `/internal/admin/auth`. It is separate from `TILLORA_REFRESH_TOKEN`, which remains scoped to `/api/v1/auth`. The admin frontend must call `/internal/admin/**` through `TILLORA_ADMIN_DOMAIN` (or use that host as its API base); the equivalent path on `TILLORA_API_DOMAIN` is intentionally always 403. The frontend must use `credentials: include`. `GET /internal/admin/auth/csrf` returns the CSRF token in the response and sets the host-only `XSRF-TOKEN` cookie; state-changing requests send the returned token in `X-XSRF-TOKEN`.

### Platform Admin Bootstrap Runbook

Use a short-lived, permission-restricted environment overlay or an equivalent secret-injection mechanism; do not keep the bootstrap password in the permanent Compose configuration.

1. Set `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_ENABLED=true`, `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_EMAIL`, and `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_PASSWORD` temporarily.
2. Start the API once with that overlay and wait for the normal readiness check.
3. From an allowed Tailscale path, log in through `POST /internal/admin/auth/login` and verify that the Platform Admin was created.
4. Set `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false`.
5. Remove `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` and `TILLORA_PLATFORM_ADMIN_BOOTSTRAP_PASSWORD` from the overlay, shell, or secret store.
6. Restart the service with the normal production configuration and delete the temporary overlay.

The bootstrap runner is separate from the company bootstrap flow. The Platform Admin JWT secret remains required after bootstrap; only the bootstrap flag and one-time credentials are removed.

## Testing

With Java 25 installed:

```bash
mvn verify
```

Validate local environment generation, optional Compose profiles, and production configuration without starting containers:

```bash
bash scripts/test-local-setup.sh
```

Docker build and runtime target Java 25.

## Object Storage

The `objectstorage` module stores file content in the private `tillora-files` MinIO bucket and keeps authoritative metadata in PostgreSQL. Metadata includes the company, object key, content type, SHA-256 checksum, size, lifecycle status, and timestamps.

Object keys are generated by the backend and scoped under `companies/{companyId}`. CSV, XLSX, and PDF report exports are supported. Paths, content types, object sizes, tenant ownership, and downloaded checksums are validated before content is returned.

Uploads reserve metadata transactionally, perform the MinIO call outside the database transaction, and then mark the object available. The same key and content can be retried safely; conflicting content is rejected. Configure local or deployed environments with:

- `TILLORA_OBJECT_STORAGE_ENABLED`
- `TILLORA_OBJECT_STORAGE_ENDPOINT`
- `TILLORA_OBJECT_STORAGE_ACCESS_KEY`
- `TILLORA_OBJECT_STORAGE_SECRET_KEY`
- `TILLORA_OBJECT_STORAGE_BUCKET`
- `TILLORA_OBJECT_STORAGE_MAX_SIZE`

## Transactional Email

Tillora can send current-account statements through the Brevo transactional email API. The request supplies recipients, subject, message, and a PDF statement; the backend generates the CSV attachment from tenant-scoped PostgreSQL data.

Endpoint:

- `POST /api/v1/current-accounts/{currentAccountId}/statement/email`

Sending requires `REPORT_VIEW`. Every request requires an `Idempotency-Key` header. Delivery state is persisted in PostgreSQL, while the provider call runs after the PENDING record commits. Provider failures do not alter financial state.

Configure email with:

- `TILLORA_EMAIL_ENABLED`
- `TILLORA_EMAIL_PROVIDER`
- `TILLORA_BREVO_API_KEY`
- `TILLORA_EMAIL_FROM_ADDRESS`
- `TILLORA_EMAIL_FROM_NAME`
- `TILLORA_EMAIL_REPLY_TO`
- `TILLORA_EMAIL_CONNECT_TIMEOUT`
- `TILLORA_EMAIL_READ_TIMEOUT`
- `TILLORA_EMAIL_MAX_RECIPIENTS`
- `TILLORA_EMAIL_MAX_ATTACHMENT_SIZE`

Email is disabled by default. Credentials must be supplied through the deployment environment and must never be stored in PostgreSQL, logs, or committed environment files.

## Electronic Documents

E-Archive, E-Invoice, GİB, UBL-TR, and electronic-document provider integrations are intentionally outside the open-source Tillora core. Invoice workflows remain local PostgreSQL sales and purchase invoice workflows; external tax-document issuance can be integrated separately by downstream deployments.

Existing deployments must back up PostgreSQL before upgrading. Historical Flyway migrations and existing electronic-document records are retained for migration compatibility; V43 revokes retired permissions and V44 removes the electronic-document search index. The removed electronic-document, company document-profile/logo, and invoice-email APIs are no longer available. Current-account statement email remains supported. Invoice approval no longer initiates electronic tax-document issuance. Export any legacy documents through the previous release before switching; the core does not provide a legacy-document reader.

## Dashboard

Read the tenant-scoped dashboard with `GET /api/v1/dashboard/summary?period=2026-07`. If `period` is omitted, the current month is resolved in the company timezone. The endpoint requires `DASHBOARD_VIEW` and returns the company currency, nine financial cards, two alert counts, and the generation time.

Financial cards and operational alerts are calculated from approved PostgreSQL invoices, voucher lines, and current-account data. Cancelled records are excluded.

Results are cached under `dashboard:summary:{companyId}:{period}` for 60 seconds by default. Redis failures fall back to PostgreSQL, and the cache never becomes the financial source of truth. Configure a TTL between 30 seconds and two minutes with `TILLORA_DASHBOARD_CACHE_TTL`.

## Reporting

All reporting endpoints are under `/api/v1/reports` and require `REPORT_VIEW`. Reports use the authenticated company, never accept a company identifier, and return PostgreSQL projections as pages of at most 100 rows.

Available reports:

- `GET /current-account-statement?currentAccountId=...`
- `GET /receivables`
- `GET /payables`
- `GET /vouchers`
- `GET /cash-movements`
- `GET /bank-movements`
- `GET /sales`
- `GET /purchases`
- `GET /audit`

Dated reports accept optional ISO-8601 `dateFrom` and `dateTo` filters. Entity-specific filters such as `status`, `voucherType`, and `accountId` are supported where applicable. Pagination uses `page`, `size`, and Spring-style `sort=property,direction`; each report permits only its documented response properties as sort keys. Invalid ranges, values, sort fields, unpaged requests, and page sizes above 100 are rejected.

Current-account statement, voucher, and cash/bank movement labels are localized from the request `Accept-Language` header. Transaction codes remain valid as request filter values.

The paginated response contracts are export-ready: clients and background export jobs can consume pages incrementally without loading an entire report into application memory. PostgreSQL remains the source of truth, and dedicated indexes support report and audit filters.

## Audit

Administrators with `AUDIT_VIEW` can read the dedicated append-only trail with `GET /api/v1/audit`. The endpoint supports `dateFrom`, `dateTo`, `userId`, `action`, `entityType`, pagination, and controlled sorting. Audit entries include correlation, client, and bounded before/after state data. Passwords, credentials, tokens, tax and identity numbers, IBANs, contact details, document content, and similar sensitive fields are rejected from audit state payloads.

PostgreSQL triggers prevent audit updates and deletes. Authentication failures use an independent transaction so the security event survives the failed login transaction.

## Observability and Production

The production profile enables ECS JSON console logs, graceful shutdown, bounded HTTP and database pools, secure cookies, forwarded-header processing, and authenticated health details. Correlation IDs are returned in `X-Correlation-Id`, added to structured log context, limited to safe characters, and regenerated when an untrusted value is supplied.

Readiness covers PostgreSQL, Redis, and MinIO when object storage is enabled. Liveness only represents application process state. Custom Prometheus metrics cover login outcomes, voucher creation and invoice approval duration, dashboard cache hit/miss, database operation latency, slow queries, and duplicate prevention.

The API container runs as an unprivileged user with a read-only filesystem, writable temporary memory, a Docker health check, and `no-new-privileges`. Use `SPRING_PROFILES_ACTIVE=production` for deployment and provide unique production values for JWT, management, database, object storage, and Grafana secrets.

## Database

Schema changes are made through Flyway migration files under `src/main/resources/db/migration`. Migrations run during application startup, and JPA schema validation checks entity and migration compatibility.

Core financial data rules:

- PostgreSQL is the source of truth.
- Duplicate financial operations are prevented with database constraints and transaction boundaries.
- Company scoping is enforced.
- N+1 query risks are considered at service and repository level.
- Retryable operations preserve idempotency.

## Useful Commands

List Docker services:

```bash
docker compose --env-file .env.development ps
```

Follow API logs:

```bash
docker compose --env-file .env.development logs -f tillora-api
```

Rebuild only the API container:

```bash
docker compose --env-file .env.development up -d --build tillora-api
```

## Manual Production Deployment (optional)

The supplied production preset is for installations that also provide a Platform Admin frontend and Cloudflare DNS credentials. Configure your own `TILLORA_API_DOMAIN`, `TILLORA_ADMIN_DOMAIN`, frontend image, and explicit CORS allowlist in `.env.production`; the domains in the runbook below are examples, not runtime defaults. Object storage is disabled by default: no R2 account or bucket is needed unless you enable it and supply S3-compatible connection settings.

GitHub Actions only verifies pull requests and the main branch. This open-source distribution does not publish images or automate production deployment; production installations are managed manually on the target server.

Production runs `api`, `admin-web`, PostgreSQL, Redis, and Caddy on a single x86_64 VPS. Only Caddy publishes host ports. PostgreSQL, Redis, the API port, the admin frontend port, and the management port remain private. Caddy terminates TLS for `TILLORA_API_DOMAIN` and `TILLORA_ADMIN_DOMAIN`; Swagger and Actuator are not routed publicly. The API image's `EXPOSE` metadata and the admin frontend's `expose` metadata do not publish host ports; `compose.production.yml` intentionally has no `ports` entry for `api` or `admin-web`.

Create `/opt/tillora`, `/opt/tillora/caddy-data`, and `/opt/tillora/caddy-config`. Assign the Caddy directories to UID/GID `10001:10001`. Copy `.env.production.example` to `/opt/tillora/.env.production`, replace every placeholder, set file mode `600`, set `TILLORA_ADMIN_WEB_IMAGE` to the frontend commit image such as `<registry>/<owner>/tillora-web:<frontend-commit-sha>`, and set `CLOUDFLARE_API_TOKEN` to a scoped token for your Cloudflare zone. Create the private R2 bucket before the first deployment if object storage is enabled. Point `TILLORA_API_DOMAIN` to the VPS and allow inbound TCP 80/443, UDP 443, and the restricted SSH port. Resolve `TILLORA_ADMIN_DOMAIN` only to the server's Tailscale IPv4 address and configure tailnet split DNS or equivalent routing so the admin frontend reaches the same hostname through Tailscale. The deployment user must own `/opt/tillora` and have permission to run Docker without interactive elevation.

Create the Cloudflare API token with only `Zone / DNS / Edit` and `Zone / Zone / Read` permissions, scoped to your Cloudflare zone. These are the `DNS Write` and `Zone Read` zone permission groups in Cloudflare's current permission model. Do not grant account-wide access, zone settings access, a Global API Key, or unrelated permissions. The token is consumed only as the Caddy container's `CLOUDFLARE_API_TOKEN` environment variable; it is not stored in the repository, Dockerfile, or Caddyfile. DNS-01 can issue the `TILLORA_ADMIN_DOMAIN` certificate even when its A record points only to a Tailscale address, but the authoritative Cloudflare zone must be discoverable from the production resolver.

Keep `TILLORA_ADMIN_DOMAIN` DNS-only and do not put it behind Cloudflare proxying: its traffic must reach Caddy through the Tailscale address. DNS-01 certificate issuance still works through the Cloudflare API token even though the admin hostname resolves only to Tailscale. The API host may retain its existing public proxy arrangement, but `/internal/admin/**` on `TILLORA_API_DOMAIN` remains 403. Do not solve routing with forwarded headers. `scripts/deploy.sh` runs `caddy validate` against the production-mounted Caddyfile after the images are pulled and before the deployment is started.

For a manual rollout, copy the Compose, Caddyfile, custom Caddy Dockerfile, and scripts/deploy.sh files to the server before running the script.

After deployment, verify the containers and healthchecks:

```bash
cd /opt/tillora
docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml ps api admin-web caddy
docker inspect "$(docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml ps -q admin-web)" --format '{{json .State.Health}}'
docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml exec -T admin-web wget -q -O - http://127.0.0.1:8080/healthz
```

From a Tailscale client, `https://<admin-domain>/admin/login` and `https://<admin-domain>/internal/admin/auth/csrf` should reach the frontend and API respectively. From any source, `https://<api-domain>/internal/admin/auth/csrf` must return 403. The normal public API route, for example `https://<api-domain>/api/v1/auth/csrf`, must retain its existing behavior. These external checks are valid only when the admin hostname resolves through the Tailscale route; public-interface and direct-IP access must be covered by the manually managed firewall/DNS setup.

Backups are compressed PostgreSQL custom-format dumps under `TILLORA_BACKUP_DIR`. Configure a root-owned rclone remote and set `TILLORA_BACKUP_RCLONE_REMOTE` to copy dumps to R2. Schedule `/opt/tillora/scripts/backup-postgres.sh` with systemd or cron. Local retention is controlled by `TILLORA_BACKUP_RETENTION_DAYS`.

Restore only during a maintenance window after taking another backup:

```bash
cd /opt/tillora
docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml exec -T postgres pg_restore --clean --if-exists --no-owner --username tillora --dbname tillora < /opt/tillora/backups/<backup-file>.dump
```

## Scope

Electronic tax-document integrations are intentionally out of scope for the open-source core.
